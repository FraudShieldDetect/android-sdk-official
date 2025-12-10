# ProtoSDK Android Integration Guide

This SDK collects device/build/display/debug/root/emulator/GPU/CPU/storage/network/sensor data and produces a deterministic SHA-256 fingerprint plus the raw JSON.

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

Alternative option:
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

## Collect a fingerprint
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

## Customize collectors
- Add a custom collector: `sdk.addCollector("customName", YourCollector())`.
- Remove a collector: `sdk.removeCollector("networkInfo")` (useful to drop volatile network state).
- Collect a single collector: `sdk.collectCollectorData("gpuInfo")`.

## Notes on stability
- The fingerprint hash is computed from a stable subset (build/device identifiers, GPU/sensor inventory, storage totals, root/emulator indicators, etc.) and excludes volatile runtime state. The full JSON still contains everything for debugging and other uses.

## Demo app
See `app/src/main/java/com/protosdk/demo/MainActivity.kt` for a Compose example that initializes the SDK and displays the collected JSON and hash.
