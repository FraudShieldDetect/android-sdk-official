#include <jni.h>
#include <sys/stat.h>
#include <sys/system_properties.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <cstdlib>

namespace {

bool performStat(const char* path) {
    if (path == nullptr) {
        return false;
    }
    struct stat fileStat {};
    return ::stat(path, &fileStat) == 0;
}

int readTracerPid() {
    constexpr const char* kStatusPath = "/proc/self/status";
    int fd = ::open(kStatusPath, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return -1;
    }

    char buffer[4096];
    const ssize_t count = ::read(fd, buffer, sizeof(buffer) - 1);
    ::close(fd);
    if (count <= 0) {
        return -1;
    }
    buffer[count] = '\0';

    const char* marker = std::strstr(buffer, "TracerPid:");
    if (marker == nullptr) {
        return 0;
    }

    marker += std::strlen("TracerPid:");
    while (*marker == ' ' || *marker == '\t') {
        ++marker;
    }
    return std::atoi(marker);
}

}  // namespace

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_RootDetectionBridge_nativeStat(
        JNIEnv* env,
        jobject /* thiz */,
        jstring path) {
    const char* cPath = env->GetStringUTFChars(path, nullptr);
    const bool exists = performStat(cPath);
    env->ReleaseStringUTFChars(path, cPath);
    return exists ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_RootDetectionBridge_nativeGetProperty(
        JNIEnv* env,
        jobject /* thiz */,
        jstring key) {
    const char* cKey = env->GetStringUTFChars(key, nullptr);
    char value[PROP_VALUE_MAX + 1] = {0};
    __system_property_get(cKey, value);
    env->ReleaseStringUTFChars(key, cKey);
    return env->NewStringUTF(value);
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_RootDetectionBridge_nativeTracerPid(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    return readTracerPid();
}
