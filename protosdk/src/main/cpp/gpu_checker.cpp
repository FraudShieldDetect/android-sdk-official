#include <jni.h>
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <GLES3/gl3.h>
#include <GLES3/gl31.h>
#include <android/log.h>
#include <dlfcn.h>
#include <vulkan/vulkan.h>

#include <array>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <sstream>
#include <string>
#include <vector>

namespace {

#ifndef GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX
#define GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX 0x9048
#endif
#ifndef GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX
#define GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX 0x9049
#endif
#ifndef GL_TEXTURE_FREE_MEMORY_ATI
#define GL_TEXTURE_FREE_MEMORY_ATI 0x87FC
#endif
#ifndef GL_NUM_EXTENSIONS
#define GL_NUM_EXTENSIONS 0x821D
#endif
#ifndef GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS
#define GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS 0x90EB
#endif

constexpr const char* kLogTag = "ProtoGpuChecker";

struct GlRuntime {
    EGLDisplay display = EGL_NO_DISPLAY;
    EGLSurface surface = EGL_NO_SURFACE;
    EGLContext context = EGL_NO_CONTEXT;
    EGLConfig config = nullptr;
};

GlRuntime gRuntime;
std::mutex gMutex;

bool InitializeContextLocked(int glVersion) {
    EGLint major = 0;
    EGLint minor = 0;
    if (gRuntime.display == EGL_NO_DISPLAY) {
        gRuntime.display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        if (gRuntime.display == EGL_NO_DISPLAY) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "eglGetDisplay failed");
            return false;
        }
        if (!eglInitialize(gRuntime.display, &major, &minor)) {
            __android_log_print(ANDROID_LOG_ERROR, kLogTag, "eglInitialize failed");
            return false;
        }
    }

    eglBindAPI(EGL_OPENGL_ES_API);

    const EGLint attribs[] = {
        EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, (glVersion >= 3) ? EGL_OPENGL_ES3_BIT_KHR : EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 16,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE
    };
    EGLint numConfigs = 0;
    EGLConfig config = nullptr;
    if (!eglChooseConfig(gRuntime.display, attribs, &config, 1, &numConfigs) || numConfigs <= 0) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "eglChooseConfig failed for GLES%d", glVersion);
        return false;
    }

    const EGLint surfaceAttribs[] = {EGL_WIDTH, 1, EGL_HEIGHT, 1, EGL_NONE};
    EGLSurface surface = eglCreatePbufferSurface(gRuntime.display, config, surfaceAttribs);
    if (surface == EGL_NO_SURFACE) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "eglCreatePbufferSurface failed");
        return false;
    }

    const EGLint contextAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, glVersion, EGL_NONE};
    EGLContext context = eglCreateContext(gRuntime.display, config, EGL_NO_CONTEXT, contextAttribs);
    if (context == EGL_NO_CONTEXT) {
        eglDestroySurface(gRuntime.display, surface);
        __android_log_print(ANDROID_LOG_WARN, kLogTag, "eglCreateContext failed for GLES%d", glVersion);
        return false;
    }

    gRuntime.config = config;
    gRuntime.surface = surface;
    gRuntime.context = context;
    return true;
}

bool EnsureGlContext() {
    std::lock_guard<std::mutex> lock(gMutex);
    if (gRuntime.context == EGL_NO_CONTEXT || gRuntime.surface == EGL_NO_SURFACE) {
        if (!InitializeContextLocked(3)) {
            if (!InitializeContextLocked(2)) {
                return false;
            }
        }
    }
    if (!eglMakeCurrent(gRuntime.display, gRuntime.surface, gRuntime.surface, gRuntime.context)) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag, "eglMakeCurrent failed");
        return false;
    }
    return true;
}

std::string QueryGlString(GLenum token) {
    if (!EnsureGlContext()) {
        return {};
    }
    const GLubyte* value = glGetString(token);
    if (value == nullptr) {
        return {};
    }
    return reinterpret_cast<const char*>(value);
}

std::vector<std::string> QueryExtensions() {
    std::vector<std::string> extensions;
    if (!EnsureGlContext()) {
        return extensions;
    }

    const GLubyte* extStr = glGetString(GL_EXTENSIONS);
    if (extStr != nullptr && std::strlen(reinterpret_cast<const char*>(extStr)) > 0) {
        std::string raw(reinterpret_cast<const char*>(extStr));
        std::istringstream stream(raw);
        std::string segment;
        while (stream >> segment) {
            extensions.push_back(segment);
        }
        return extensions;
    }

    GLint count = 0;
    glGetIntegerv(GL_NUM_EXTENSIONS, &count);
    using GlGetStringiFn = const GLubyte* (*)(GLenum, GLuint);
    auto glGetStringiPtr = reinterpret_cast<GlGetStringiFn>(eglGetProcAddress("glGetStringi"));
    if (glGetStringiPtr == nullptr || count <= 0) {
        return extensions;
    }
    for (GLint index = 0; index < count; ++index) {
        const GLubyte* name = glGetStringiPtr(GL_EXTENSIONS, static_cast<GLuint>(index));
        if (name != nullptr) {
            extensions.emplace_back(reinterpret_cast<const char*>(name));
        }
    }
    return extensions;
}

std::array<jint, 6> QueryEglConfig() {
    std::array<jint, 6> values{0, 0, 0, 0, 0, 0};
    if (!EnsureGlContext() || gRuntime.config == nullptr) {
        return values;
    }
    EGLint red = 0, green = 0, blue = 0, alpha = 0, depth = 0, stencil = 0;
    eglGetConfigAttrib(gRuntime.display, gRuntime.config, EGL_RED_SIZE, &red);
    eglGetConfigAttrib(gRuntime.display, gRuntime.config, EGL_GREEN_SIZE, &green);
    eglGetConfigAttrib(gRuntime.display, gRuntime.config, EGL_BLUE_SIZE, &blue);
    eglGetConfigAttrib(gRuntime.display, gRuntime.config, EGL_ALPHA_SIZE, &alpha);
    eglGetConfigAttrib(gRuntime.display, gRuntime.config, EGL_DEPTH_SIZE, &depth);
    eglGetConfigAttrib(gRuntime.display, gRuntime.config, EGL_STENCIL_SIZE, &stencil);
    values[0] = red;
    values[1] = green;
    values[2] = blue;
    values[3] = alpha;
    values[4] = depth;
    values[5] = stencil;
    return values;
}

std::array<jint, 3> QueryGpuMemory() {
    std::array<jint, 3> values{0, 0, 0};
    if (!EnsureGlContext()) {
        return values;
    }
    const auto extensions = QueryExtensions();
    std::string joined;
    joined.reserve(extensions.size() * 16);
    for (const auto& entry : extensions) {
        joined.append(entry);
        joined.push_back(' ');
    }

    if (joined.find("GL_NVX_gpu_memory_info") != std::string::npos) {
        GLint total = 0;
        GLint current = 0;
        glGetIntegerv(GL_GPU_MEMORY_INFO_TOTAL_AVAILABLE_MEMORY_NVX, &total);
        glGetIntegerv(GL_GPU_MEMORY_INFO_CURRENT_AVAILABLE_VIDMEM_NVX, &current);
        if (total > 0) {
            values[0] = total;
            values[2] = current > 0 ? current : 0;
            values[1] = total - values[2];
            return values;
        }
    }

    if (joined.find("GL_ATI_meminfo") != std::string::npos) {
        GLint freeMem[4] = {0, 0, 0, 0};
        glGetIntegerv(GL_TEXTURE_FREE_MEMORY_ATI, freeMem);
        if (freeMem[0] > 0) {
            values[0] = freeMem[0];
            values[2] = freeMem[0];
        }
    }
    return values;
}

jstring ToJavaString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

jobjectArray ToJavaStringArray(JNIEnv* env, const std::vector<std::string>& values) {
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        return nullptr;
    }
    jobjectArray array = env->NewObjectArray(static_cast<jsize>(values.size()), stringClass, nullptr);
    if (array == nullptr) {
        return nullptr;
    }
    for (jsize i = 0; i < static_cast<jsize>(values.size()); ++i) {
        jstring element = env->NewStringUTF(values[static_cast<size_t>(i)].c_str());
        env->SetObjectArrayElement(array, i, element);
        env->DeleteLocalRef(element);
    }
    return array;
}

jintArray ToJavaIntArray(JNIEnv* env, const std::array<jint, 6>& values) {
    jintArray array = env->NewIntArray(6);
    if (array == nullptr) {
        return nullptr;
    }
    env->SetIntArrayRegion(array, 0, 6, values.data());
    return array;
}

jintArray ToJavaIntArray3(JNIEnv* env, const std::array<jint, 3>& values) {
    jintArray array = env->NewIntArray(3);
    if (array == nullptr) {
        return nullptr;
    }
    env->SetIntArrayRegion(array, 0, 3, values.data());
    return array;
}

jstring DecodePayload(JNIEnv* env, jintArray payload, jstring key) {
    if (payload == nullptr || key == nullptr) {
        return env->NewStringUTF("");
    }
    const jsize payloadSize = env->GetArrayLength(payload);
    if (payloadSize <= 0) {
        return env->NewStringUTF("");
    }
    std::vector<jint> buffer(static_cast<size_t>(payloadSize));
    env->GetIntArrayRegion(payload, 0, payloadSize, buffer.data());

    const char* keyChars = env->GetStringUTFChars(key, nullptr);
    if (keyChars == nullptr) {
        return env->NewStringUTF("");
    }
    const size_t keyLength = std::strlen(keyChars);
    if (keyLength == 0) {
        env->ReleaseStringUTFChars(key, keyChars);
        return env->NewStringUTF("");
    }

    std::string decoded(static_cast<size_t>(payloadSize), '\0');
    for (jsize i = 0; i < payloadSize; ++i) {
        const uint8_t xorByte = static_cast<uint8_t>(keyChars[i % keyLength]);
        decoded[static_cast<size_t>(i)] = static_cast<char>((buffer[static_cast<size_t>(i)] ^ xorByte) & 0xFF);
    }
    env->ReleaseStringUTFChars(key, keyChars);
    return env->NewStringUTF(decoded.c_str());
}

jboolean CheckVulkanSupport() {
    void* handle = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) {
        handle = dlopen("libvulkan.so.1", RTLD_NOW | RTLD_LOCAL);
    }
    if (handle == nullptr) {
        return JNI_FALSE;
    }
    using EnumerateFn = VkResult (*)(uint32_t*);
    auto enumerate = reinterpret_cast<EnumerateFn>(dlsym(handle, "vkEnumerateInstanceVersion"));
    bool supported = true;
    if (enumerate != nullptr) {
        uint32_t version = 0;
        const VkResult result = enumerate(&version);
        supported = (result == VK_SUCCESS) && (version >= VK_MAKE_VERSION(1, 0, 0));
    }
    dlclose(handle);
    return supported ? JNI_TRUE : JNI_FALSE;
}

double RunMicroBenchmark() {
    if (!EnsureGlContext()) {
        return 0.0;
    }
    const int iterations = 12;
    const auto start = std::chrono::steady_clock::now();
    for (int i = 0; i < iterations; ++i) {
        glClearColor(0.1f * i, 0.2f, 0.3f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        glFinish();
    }
    const auto end = std::chrono::steady_clock::now();
    const std::chrono::duration<double, std::milli> elapsed = end - start;
    return elapsed.count() / iterations;
}

}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeGetGpuRenderer(
        JNIEnv* env,
        jobject /* thiz */) {
    return ToJavaString(env, QueryGlString(GL_RENDERER));
}

JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeGetGpuVendor(
        JNIEnv* env,
        jobject /* thiz */) {
    return ToJavaString(env, QueryGlString(GL_VENDOR));
}

JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeGetGpuVersion(
        JNIEnv* env,
        jobject /* thiz */) {
    return ToJavaString(env, QueryGlString(GL_VERSION));
}

JNIEXPORT jobjectArray JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeGetGpuExtensions(
        JNIEnv* env,
        jobject /* thiz */) {
    const auto extensions = QueryExtensions();
    return ToJavaStringArray(env, extensions);
}

JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeGetEglVendor(
        JNIEnv* env,
        jobject /* thiz */) {
    if (!EnsureGlContext() || gRuntime.display == EGL_NO_DISPLAY) {
        return ToJavaString(env, "");
    }
    const char* vendor = eglQueryString(gRuntime.display, EGL_VENDOR);
    if (vendor == nullptr) {
        return ToJavaString(env, "");
    }
    return ToJavaString(env, vendor);
}

JNIEXPORT jintArray JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeGetEglConfig(
        JNIEnv* env,
        jobject /* thiz */) {
    return ToJavaIntArray(env, QueryEglConfig());
}

JNIEXPORT jintArray JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeGetGpuMemoryInfo(
        JNIEnv* env,
        jobject /* thiz */) {
    return ToJavaIntArray3(env, QueryGpuMemory());
}

JNIEXPORT jint JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeGetMaxTextureSize(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    if (!EnsureGlContext()) {
        return 0;
    }
    GLint value = 0;
    glGetIntegerv(GL_MAX_TEXTURE_SIZE, &value);
    return value;
}

JNIEXPORT jint JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeGetComputeWorkGroupInvocations(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    if (!EnsureGlContext()) {
        return 0;
    }
    GLint value = 0;
    glGetIntegerv(GL_MAX_COMPUTE_WORK_GROUP_INVOCATIONS, &value);
    if (glGetError() != GL_NO_ERROR) {
        return 0;
    }
    return value;
}

JNIEXPORT jdouble JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeRunMicroBenchmark(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    return RunMicroBenchmark();
}

JNIEXPORT jboolean JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeCheckVulkan(
        JNIEnv* /* env */,
        jobject /* thiz */) {
    return CheckVulkanSupport();
}

JNIEXPORT jstring JNICALL
Java_com_protosdk_sdk_fingerprint_nativebridge_GpuDetectionBridge_nativeDecodeString(
        JNIEnv* env,
        jobject /* thiz */,
        jintArray payload,
        jstring key) {
    return DecodePayload(env, payload, key);
}

}  // extern "C"
