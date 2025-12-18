This SDK collects structured information about an Android device and generates a device fingerprint derived from system, hardware, and execution environment signals.

The SDK focuses solely on device data collection and exposes the collected data in a structured format for use by the integrating application.



## Requirements
- Android minSdk 24, compileSdk 35 (NDK 25.1.8937393 for native checks).
- Kotlin + coroutines (`kotlinx-coroutines-android`).

## Add the SDK (no publishing needed)
Use the released artifact from JitPack. Replace the version with the tag you want (e.g., `1.0.0`):

```kotlin
// settings.gradle[.kts]
dependencyResolutionManagement {
  repositories {
    maven { url = uri("https://jitpack.io") }
    google()
    mavenCentral()
  }
}

// app/build.gradle[.kts]
dependencies {
  implementation("com.github.fraudshielddetect:protosdk:1.0.0")
}
```

Alternative Option:
- **Direct AAR**: download the release AAR and drop into `app/libs`, then `implementation(files("libs/protosdk-release.aar"))`.

## Initialize the SDK
Call once at app start (e.g., in `Application.onCreate`):
```kotlin
import com.protosdk.sdk.ProtoSDK
import com.protosdk.sdk.ProtoSDKConfig

class MyApp : Application() {
  override fun onCreate() {
    super.onCreate()
    ProtoSDK.initialize(
      this,
      ProtoSDKConfig(
        enableTimeout = true,
        enableDebugLogging = false,
        defaultTimeoutMs = 10_000L,
      ),
    )
  }
}
```

## Collect a Fingerprint
```kotlin
// In a coroutine (e.g., viewModelScope / lifecycleScope)
import com.protosdk.sdk.ProtoSDK

val sdk = ProtoSDK.getInstance()
val result = sdk.collectFingerprint()
if (result.success) {
  val hash = result.fingerprint          // Stable SHA-256 over stable fields
  val json = result.data.toJsonWithTimestamp() // Full payload if you need it
} else {
  // handle error
}
```

## Customize Collectors
- Add a custom collector: `sdk.addCollector("customName", YourCollector())`.
- Remove a collector: `sdk.removeCollector("networkInfo")` (useful to drop volatile network state).
- Collect a single collector: `sdk.collectCollectorData("gpuInfo")`.

## Notes on Stability
The generated device fingerprint is derived from a subset of collected device signals that are expected to remain relatively consistent across app launches.

Fingerprint values may change over time due to system updates, hardware changes, or environmental differences. The full set of collected device data is always available alongside the fingerprint for applications that require additional context.

## Demo App
This repository includes a demo application that shows how to initialize the SDK and collect device data and the generated device fingerprint.

See `app/src/main/java/com/protosdk/demo/MainActivity.kt` for a simple example implementation.
