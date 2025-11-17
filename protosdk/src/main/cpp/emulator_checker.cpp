#include <jni.h>
#include <android/looper.h>
#include <android/sensor.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <map>
#include <string>
#include <string_view>
#include <vector>

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
#include <arm_neon.h>
#endif

namespace {

constexpr size_t kMaxFileBytes = 64 * 1024;  // 64KB per read

bool performStat(const char* path) {
    if (path == nullptr) {
        return false;
    }
    struct stat fileStat {};
    return ::stat(path, &fileStat) == 0;
}

std::string readSmallFile(const char* path) {
    if (path == nullptr) {
        return {};
    }
    int fd = ::open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return {};
    }
    std::string output;
    output.reserve(kMaxFileBytes);
    char buffer[1024];
    while (true) {
        const ssize_t bytes = ::read(fd, buffer, sizeof(buffer));
        if (bytes <= 0) {
            break;
        }
        const size_t remaining = kMaxFileBytes - output.size();
        const size_t toCopy = std::min(static_cast<size_t>(bytes), remaining);
        output.append(buffer, buffer + toCopy);
        if (output.size() >= kMaxFileBytes) {
            break;
        }
    }
    ::close(fd);
    return output;
}

int readTracerPid() {
    constexpr const char* kStatusPath = "/proc/self/status";
    const std::string status = readSmallFile(kStatusPath);
    if (status.empty()) {
        return -1;
    }

    const std::string marker = "TracerPid:";
    const size_t pos = status.find(marker);
    if (pos == std::string::npos) {
        return 0;
    }
    size_t idx = pos + marker.size();
    while (idx < status.size() && (status[idx] == ' ' || status[idx] == '\t')) {
        ++idx;
    }
    return std::atoi(status.c_str() + idx);
}

#if defined(__ARM_NEON) || defined(__ARM_NEON__)
bool runNeonProbe() {
    alignas(16) uint8_t buffer[64];
    for (size_t i = 0; i < sizeof(buffer); ++i) {
        buffer[i] = static_cast<uint8_t>((i * 13) & 0xFF);
    }
    const float* misaligned = reinterpret_cast<const float*>(buffer + 1);
    float32x4_t vec = vld1q_f32(misaligned);
    std::array<float, 4> tmp{};
    vst1q_f32(tmp.data(), vec);
    volatile float sum = tmp[0] + tmp[1] + tmp[2] + tmp[3];
    return !std::isnan(sum) && sum != 0.0f;
}
#else
bool runNeonProbe() {
    return false;
}
#endif

struct SensorStats {
    bool active = false;
    int samples = 0;
    float minVals[3] = {0.f, 0.f, 0.f};
    float maxVals[3] = {0.f, 0.f, 0.f};
};

void updateStats(SensorStats& stats, const ASensorEvent& event) {
    if (!stats.active) {
        stats.active = true;
        stats.samples = 0;
        for (int i = 0; i < 3; ++i) {
            stats.minVals[i] = event.vector.v[i];
            stats.maxVals[i] = event.vector.v[i];
        }
    } else {
        for (int i = 0; i < 3; ++i) {
            if (event.vector.v[i] < stats.minVals[i]) {
                stats.minVals[i] = event.vector.v[i];
            }
            if (event.vector.v[i] > stats.maxVals[i]) {
                stats.maxVals[i] = event.vector.v[i];
            }
        }
    }
    stats.samples += 1;
}

}  // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_EmulatorDetectionBridge_nativeStat(
        JNIEnv* env,
        jobject /* thiz */,
        jstring path) {
    const char* cPath = env->GetStringUTFChars(path, nullptr);
    const bool exists = performStat(cPath);
    env->ReleaseStringUTFChars(path, cPath);
    return exists ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_EmulatorDetectionBridge_nativeGetProperty(
        JNIEnv* env,
        jobject /* thiz */,
        jstring key) {
    const char* cKey = env->GetStringUTFChars(key, nullptr);
    char value[PROP_VALUE_MAX + 1] = {0};
    __system_property_get(cKey, value);
    env->ReleaseStringUTFChars(key, cKey);
    return env->NewStringUTF(value);
}

JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_EmulatorDetectionBridge_nativeReadCpuInfo(
        JNIEnv* env,
        jobject /* thiz */) {
    const std::string cpuInfo = readSmallFile("/proc/cpuinfo");
    return env->NewStringUTF(cpuInfo.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_EmulatorDetectionBridge_nativeReadProc(
        JNIEnv* env,
        jobject /* thiz */,
        jstring path) {
    const char* cPath = env->GetStringUTFChars(path, nullptr);
    std::string_view view(cPath);
    if (!(view.size() >= 5 && std::strncmp(view.data(), "/proc", 5) == 0)) {
        env->ReleaseStringUTFChars(path, cPath);
        return env->NewStringUTF("");
    }
    const std::string content = readSmallFile(cPath);
    env->ReleaseStringUTFChars(path, cPath);
    return env->NewStringUTF(content.c_str());
}

JNIEXPORT jobjectArray JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_EmulatorDetectionBridge_nativeGetNetworkIfaces(
        JNIEnv* env,
        jobject /* thiz */) {
    DIR* dir = ::opendir("/sys/class/net");
    if (dir == nullptr) {
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), nullptr);
    }

    std::vector<std::string> ifaces;
    while (true) {
        dirent* entry = ::readdir(dir);
        if (entry == nullptr) {
            break;
        }
        if (entry->d_name[0] == '.') {
            continue;
        }
        const std::string iface(entry->d_name);
        const std::string base = std::string("/sys/class/net/") + iface;
        const std::string mac = readSmallFile((base + "/address").c_str());
        const std::string type = readSmallFile((base + "/type").c_str());
        if (mac.empty()) {
            continue;
        }
        std::string normalizedMac = mac;
        normalizedMac.erase(std::remove(normalizedMac.begin(), normalizedMac.end(), '\n'), normalizedMac.end());
        std::string normalizedType = type;
        normalizedType.erase(std::remove(normalizedType.begin(), normalizedType.end(), '\n'), normalizedType.end());
        ifaces.emplace_back(iface + '|' + normalizedMac + '|' + normalizedType);
    }
    ::closedir(dir);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(ifaces.size()), stringClass, nullptr);
    for (size_t i = 0; i < ifaces.size(); ++i) {
        env->SetObjectArrayElement(array, static_cast<jsize>(i), env->NewStringUTF(ifaces[i].c_str()));
    }
    return array;
}

JNIEXPORT jintArray JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_EmulatorDetectionBridge_nativeCheckSensors(
        JNIEnv* env,
        jobject /* thiz */,
        jint windowMs) {
    ASensorManager* manager = ASensorManager_getInstance();
    if (manager == nullptr) {
        return env->NewIntArray(0);
    }

    ALooper* looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
    if (looper == nullptr) {
        return env->NewIntArray(0);
    }

    ASensorEventQueue* queue = ASensorManager_createEventQueue(manager, looper, 0, nullptr, nullptr);
    if (queue == nullptr) {
        return env->NewIntArray(0);
    }

    const int sensorTypes[] = {ASENSOR_TYPE_ACCELEROMETER, ASENSOR_TYPE_GYROSCOPE, ASENSOR_TYPE_MAGNETIC_FIELD};
    SensorStats stats[3];
    std::map<int, int> typeToIndex;
    const ASensor* activeSensors[3] = {nullptr, nullptr, nullptr};

    for (int i = 0; i < 3; ++i) {
        const ASensor* sensor = ASensorManager_getDefaultSensor(manager, sensorTypes[i]);
        if (sensor == nullptr) {
            continue;
        }
        typeToIndex[sensorTypes[i]] = i;
        stats[i].active = true;
        activeSensors[i] = sensor;
        ASensorEventQueue_enableSensor(queue, sensor);
        ASensorEventQueue_setEventRate(queue, sensor, 20000);  // 20ms
    }

    const auto start = std::chrono::steady_clock::now();
    const auto deadline = start + std::chrono::milliseconds(windowMs > 0 ? windowMs : 100);

    while (std::chrono::steady_clock::now() < deadline) {
        ALooper_pollAll(5, nullptr, nullptr, nullptr);
        ASensorEvent events[8];
        const int count = ASensorEventQueue_getEvents(queue, events, 8);
        if (count <= 0) {
            continue;
        }
        for (int i = 0; i < count; ++i) {
            const ASensorEvent& event = events[i];
            auto it = typeToIndex.find(event.type);
            if (it == typeToIndex.end()) {
                continue;
            }
            updateStats(stats[it->second], event);
        }
    }
    for (int i = 0; i < 3; ++i) {
        if (activeSensors[i] != nullptr) {
            ASensorEventQueue_disableSensor(queue, activeSensors[i]);
        }
    }
    ASensorManager_destroyEventQueue(manager, queue);

    int sampled = 0;
    int varying = 0;
    constexpr float kVarianceThreshold = 0.05f;
    for (const auto& entry : typeToIndex) {
        const SensorStats& sensorStats = stats[entry.second];
        if (sensorStats.samples >= 3) {
            sampled += 1;
            for (int axis = 0; axis < 3; ++axis) {
                const float diff = sensorStats.maxVals[axis] - sensorStats.minVals[axis];
                if (diff >= kVarianceThreshold) {
                    varying += 1;
                    break;
                }
            }
        }
    }

    jintArray result = env->NewIntArray(2);
    if (result == nullptr) {
        return nullptr;
    }
    jint values[2] = {sampled, varying};
    env->SetIntArrayRegion(result, 0, 2, values);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_EmulatorDetectionBridge_nativeTracerPid(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    return readTracerPid();
}

JNIEXPORT jboolean JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_EmulatorDetectionBridge_nativeNeonProbe(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    return runNeonProbe() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_EmulatorDetectionBridge_nativeDecodeString(
        JNIEnv* env,
        jobject /* thiz */,
        jintArray payload,
        jstring key) {
    if (payload == nullptr || key == nullptr) {
        return env->NewStringUTF("");
    }
    const jsize payloadSize = env->GetArrayLength(payload);
    if (payloadSize <= 0) {
        return env->NewStringUTF("");
    }
    std::vector<jint> buffer(payloadSize);
    env->GetIntArrayRegion(payload, 0, payloadSize, buffer.data());

    const char* keyChars = env->GetStringUTFChars(key, nullptr);
    const size_t keyLength = std::strlen(keyChars);
    if (keyLength == 0) {
        env->ReleaseStringUTFChars(key, keyChars);
        return env->NewStringUTF("");
    }

    std::string decoded(static_cast<size_t>(payloadSize), '\0');
    for (jsize i = 0; i < payloadSize; ++i) {
        const uint8_t keyByte = static_cast<uint8_t>(keyChars[i % keyLength]);
        decoded[static_cast<size_t>(i)] = static_cast<char>((buffer[i] ^ keyByte) & 0xFF);
    }
    env->ReleaseStringUTFChars(key, keyChars);
    return env->NewStringUTF(decoded.c_str());
}

}  // extern "C"
