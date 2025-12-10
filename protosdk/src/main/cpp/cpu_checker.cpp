#include <jni.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <sys/auxv.h>

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <set>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

namespace {

constexpr size_t kMaxFileBytes = 32 * 1024;

std::string Strip(const std::string& in) {
  size_t start = 0;
  while (start < in.size() && std::isspace(static_cast<unsigned char>(in[start]))) start++;
  size_t end = in.size();
  while (end > start && std::isspace(static_cast<unsigned char>(in[end - 1]))) end--;
  return in.substr(start, end - start);
}

std::string ReadFile(const std::string& path, size_t maxBytes = kMaxFileBytes) {
  FILE* file = std::fopen(path.c_str(), "re");
  if (!file) return {};
  std::string out;
  out.reserve(256);
  char buffer[512];
  size_t total = 0;
  while (true) {
    size_t read = std::fread(buffer, 1, sizeof(buffer), file);
    if (read == 0) break;
    size_t toCopy = std::min(read, maxBytes - total);
    out.append(buffer, buffer + toCopy);
    total += toCopy;
    if (total >= maxBytes) break;
  }
  std::fclose(file);
  return Strip(out);
}

bool IsDir(const std::string& path) {
  struct stat st {};
  return ::stat(path.c_str(), &st) == 0 && S_ISDIR(st.st_mode);
}

std::vector<std::string> ListDirs(const std::string& base, const std::string& prefix) {
  std::vector<std::string> result;
  DIR* dir = ::opendir(base.c_str());
  if (!dir) return result;
  struct dirent* entry;
  while ((entry = ::readdir(dir)) != nullptr) {
    if (entry->d_name[0] == '.') continue;
    std::string name(entry->d_name);
    if (!prefix.empty()) {
      if (name.rfind(prefix, 0) != 0) continue;
    }
    std::string full = base + "/" + name;
    if (IsDir(full)) result.push_back(full);
  }
  closedir(dir);
  return result;
}

std::vector<std::string> ListFiles(const std::string& base, const std::string& prefix) {
  std::vector<std::string> result;
  DIR* dir = ::opendir(base.c_str());
  if (!dir) return result;
  struct dirent* entry;
  while ((entry = ::readdir(dir)) != nullptr) {
    if (entry->d_name[0] == '.') continue;
    std::string name(entry->d_name);
    if (!prefix.empty() && name.rfind(prefix, 0) != 0) continue;
    std::string full = base + "/" + name;
    struct stat st {};
    if (::stat(full.c_str(), &st) == 0 && S_ISREG(st.st_mode)) result.push_back(full);
  }
  closedir(dir);
  return result;
}

std::string JsonQuote(const std::string& value) {
  std::string out;
  out.reserve(value.size() + 2);
  out.push_back('"');
  for (char c : value) {
    switch (c) {
      case '\"': out += "\\\""; break;
      case '\\': out += "\\\\"; break;
      case '\n': out += "\\n"; break;
      case '\r': out += "\\r"; break;
      case '\t': out += "\\t"; break;
      default: out.push_back(c); break;
    }
  }
  out.push_back('"');
  return out;
}

int ParseInt(const std::string& str) {
  if (str.empty()) return 0;
  return std::atoi(str.c_str());
}

int CountBitsFromMap(const std::string& mapStr) {
  // shared_cpu_map is hex mask separated by commas, e.g., "ff,00" or "0000000f".
  int count = 0;
  for (char c : mapStr) {
    if (c == ',' || c == ' ') continue;
    if (c >= '0' && c <= '9') count += __builtin_popcount(c - '0');
    else if (c >= 'a' && c <= 'f') count += __builtin_popcount(10 + (c - 'a'));
    else if (c >= 'A' && c <= 'F') count += __builtin_popcount(10 + (c - 'A'));
  }
  return count;
}

std::string BuildCpuTopologyJson() {
  const std::string cpuRoot = "/sys/devices/system/cpu";
  const std::string possible = ReadFile(cpuRoot + "/possible");
  int totalCores = 0;
  if (!possible.empty()) {
    // Expect format like "0-7"
    auto dash = possible.find('-');
    if (dash != std::string::npos) {
      int start = std::atoi(possible.substr(0, dash).c_str());
      int end = std::atoi(possible.substr(dash + 1).c_str());
      if (end >= start) totalCores = end - start + 1;
    }
  }
  if (totalCores == 0) {
    // Fallback: count cpu* dirs
    auto cpus = ListDirs(cpuRoot, "cpu");
    totalCores = static_cast<int>(cpus.size());
  }

  std::map<int, std::string> levelToSize;  // prefer first seen per level
  std::set<std::string> l2Maps;
  std::set<int> levels;

  auto cpuDirs = ListDirs(cpuRoot, "cpu");
  for (const auto& cpuPath : cpuDirs) {
    auto cacheDirs = ListDirs(cpuPath + "/cache", "index");
    for (const auto& cachePath : cacheDirs) {
      std::string levelStr = ReadFile(cachePath + "/level");
      int level = ParseInt(levelStr);
      if (level <= 0) continue;
      levels.insert(level);

      std::string size = ReadFile(cachePath + "/size");
      if (!size.empty() && !levelToSize.count(level)) {
        levelToSize[level] = size;
      }
      if (level == 2 || level == 3) {
        std::string shared = ReadFile(cachePath + "/shared_cpu_map");
        if (!shared.empty()) l2Maps.insert(shared);
      }
    }
  }

  int clusters = 0;
  if (!l2Maps.empty()) {
    clusters = static_cast<int>(l2Maps.size());
  } else if (totalCores > 0) {
    clusters = 1;
  }

  std::string json = "{";
  json += "\"totalCores\":" + std::to_string(totalCores) + ",";
  json += "\"clusters\":" + std::to_string(clusters) + ",";
  json += "\"cacheLevels\":" + std::to_string(levels.empty() ? 0 : static_cast<int>(levels.size()));
  if (levelToSize.count(1)) json += ",\"l1Cache\":" + JsonQuote(levelToSize[1]);
  if (levelToSize.count(2)) json += ",\"l2Cache\":" + JsonQuote(levelToSize[2]);
  if (levelToSize.count(3)) json += ",\"l3Cache\":" + JsonQuote(levelToSize[3]);

  json += "}";
  return json;
}

std::string BuildCpuFreqJson() {
  const std::string base = "/sys/devices/system/cpu/cpu0/cpufreq";
  std::string maxFreq = ReadFile(base + "/cpuinfo_max_freq");

  std::string json = "{";
  json += "\"maxFreq\":" + (maxFreq.empty() ? "0" : maxFreq);

  // Optional available frequencies for richer heuristics.
  std::string availableFreq = ReadFile(base + "/scaling_available_frequencies");
  if (!availableFreq.empty()) {
    json += ",\"availableFrequencies\":" + JsonQuote(availableFreq);
  }

  json += "}";
  return json;
}

// std::string BuildCpuIdleJson() {
//   const std::string base = "/sys/devices/system/cpu/cpu0/cpuidle";
//   auto stateDirs = ListDirs(base, "state");
//
//   std::vector<std::string> names;
//   std::vector<int> latencies;
//   for (const auto& dir : stateDirs) {
//     std::string name = ReadFile(dir + "/name");
//     std::string exitLatency = ReadFile(dir + "/exit_latency");
//     if (!name.empty()) names.push_back(name);
//     latencies.push_back(ParseInt(exitLatency));
//   }
//
//   std::string json = "{";
//   json += "\"states\":[";
//   for (size_t i = 0; i < names.size(); ++i) {
//     if (i > 0) json += ",";
//     json += JsonQuote(names[i]);
//   }
//   json += "],\"exitLatencies\":[";
//   for (size_t i = 0; i < latencies.size(); ++i) {
//     if (i > 0) json += ",";
//     json += std::to_string(latencies[i]);
//   }
//   json += "]}";
//   return json;
// }

// std::string BuildPlatformJson() {
//   const char* platform = reinterpret_cast<const char*>(getauxval(AT_PLATFORM));
//   const char* base = reinterpret_cast<const char*>(getauxval(AT_BASE_PLATFORM));
//
//   std::string json = "{";
//   json += "\"platform\":" + JsonQuote(platform ? platform : "");
//   json += ",\"base\":" + JsonQuote(base ? base : "");
//   json += "}";
//   return json;
// }

std::string BuildCpuInfoJson() {
  const std::string path = "/proc/cpuinfo";
  const std::string content = ReadFile(path, 64 * 1024);

  // Parse per-processor blocks.
  std::vector<std::map<std::string, std::string>> processors;
  std::map<std::string, std::string>* currentProc = nullptr;

  size_t start = 0;
  while (start < content.size()) {
    size_t end = content.find('\n', start);
    if (end == std::string::npos) end = content.size();
    std::string line = content.substr(start, end - start);
    start = end + 1;
    if (line.empty()) continue;

    auto delim = line.find(':');
    if (delim == std::string::npos) continue;
    std::string key = Strip(line.substr(0, delim));
    std::string value = Strip(line.substr(delim + 1));

    if (key == "processor") {
      processors.emplace_back();
      currentProc = &processors.back();
      (*currentProc)[key] = value;
    } else if (currentProc != nullptr) {
      (*currentProc)[key] = value;
    }
  }

  auto mapToJson = [](const std::map<std::string, std::string>& m) {
    std::string j = "{";
    bool first = true;
    for (const auto& kv : m) {
      if (!first) j += ",";
      j += JsonQuote(kv.first) + ":" + JsonQuote(kv.second);
      first = false;
    }
    j += "}";
    return j;
  };

  std::string json = "{";
  json += "\"processors\":[";
  for (size_t i = 0; i < processors.size(); ++i) {
    if (i > 0) json += ",";
    json += mapToJson(processors[i]);
  }
  json += "]";
  json += "}";
  return json;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_CpuDetectionBridge_nativeGetCpuTopology(
    JNIEnv* env,
    jobject /*thiz*/) {
  const std::string payload = BuildCpuTopologyJson();
  return env->NewStringUTF(payload.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_CpuDetectionBridge_nativeGetCpuFreqInfo(
    JNIEnv* env,
    jobject /*thiz*/) {
  const std::string payload = BuildCpuFreqJson();
  return env->NewStringUTF(payload.c_str());
}

// extern "C" JNIEXPORT jstring JNICALL
// Java_com_protosdk_sdk_fingerprint_nativebridge_CpuDetectionBridge_nativeGetCpuIdleStates(
//     JNIEnv* env,
//     jobject /*thiz*/) {
//   const std::string payload = BuildCpuIdleJson();
//   return env->NewStringUTF(payload.c_str());
// }

// extern "C" JNIEXPORT jstring JNICALL
// Java_com_protosdk_sdk_fingerprint_nativebridge_CpuDetectionBridge_nativeGetPlatformId(
//     JNIEnv* env,
//     jobject /*thiz*/) {
//   const std::string payload = BuildPlatformJson();
//   return env->NewStringUTF(payload.c_str());
// }

extern "C" JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_CpuDetectionBridge_nativeGetProcInfo(
    JNIEnv* env,
    jobject /*thiz*/) {
  const std::string payload = BuildCpuInfoJson();
  return env->NewStringUTF(payload.c_str());
}
