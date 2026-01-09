# ProtoSDK

A lightweight, open-source Android SDK for generating **stable device fingerprints** using hardware, OS, and runtime signals. Designed for fraud detection, abuse prevention, and device reputation systems.

ProtoSDK runs fully on-device, works offline, and exposes both a compact fingerprint hash and structured device metadata.

---

## What Is Device Fingerprinting?

Device fingerprinting is a technique used to **identify a device based on its technical characteristics**, rather than relying on user-provided identifiers like accounts, cookies, or advertising IDs.

Instead of asking *who* the user is, fingerprinting answers:

* Is this the **same physical device** we have seen before?
* How consistent is this device with known legitimate behavior?
* Does this device show signs of tampering, automation, or virtualization?

A fingerprint is typically derived from a combination of:

* Hardware properties (CPU, GPU, sensors, storage)
* Operating system details (Android version, build data)
* Runtime environment signals (debug flags, emulator artifacts)
* System configuration and capabilities

ProtoSDK aggregates these signals and derives a **stable, non-reversible hash** that can be safely used as a device identifier in backend systems.

Importantly, fingerprinting is **probabilistic**, not absolute. It provides strong signals that must be interpreted server-side alongside behavior, risk rules, and business context.

---

## Common Use Cases

### Fraud and Abuse Detection

* Identify devices repeatedly involved in fraudulent activity
* Detect fraud rings reusing the same physical devices
* Strengthen risk scoring with hard-to-spoof device traits

### Account Protection

* Bind sessions and logins to a known device fingerprint
* Detect account takeovers from unfamiliar or high-risk devices
* Reduce reliance on SMS or email-based verification

### Multi-Account and Ban Evasion Prevention

* Detect users creating multiple accounts on the same device
* Identify ban evasion across app reinstalls or account resets
* Enforce one-account-per-device policies

### Bot and Emulator Defense

* Detect emulators, virtual devices, and automation frameworks
* Flag devices with abnormal sensor or GPU characteristics
* Reduce automated abuse without impacting real users

### Payments and Checkout Risk

* Add device reputation to transaction risk models
* Identify high-risk devices before payment authorization
* Reduce false positives without adding user friction

### Compliance and Audit Trails

* Record consistent device metadata for investigations
* Support forensic analysis after incidents
* Maintain historical device context for regulatory needs

---

## Features

* Stable device fingerprint (SHA-256)
* Parallel signal collection (non-blocking)
* Native integrity checks (root, emulator, GPU)
* Resilient to partial failures
* Works across app reinstalls
* Kotlin-first API (coroutines)
* Extensible collector system
* No PII collection

---

## Requirements

* Android 7.0+ (API 24)
* Kotlin + Coroutines
* Android NDK 25.1.8937393

---

## Installation

### Gradle (JitPack)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositories {
    maven { url = uri("https://jitpack.io") }
    google()
    mavenCentral()
  }
}

// app/build.gradle.kts
dependencies {
  implementation("com.github.fraudshielddetect:protosdk:1.0.0")
}
```

### AAR (Manual)

```kotlin
implementation(files("libs/protosdk-release.aar"))
```

---

## Usage

### Initialize

Initialize once in your `Application` class:

```kotlin
class MyApp : Application() {
  override fun onCreate() {
    super.onCreate()
    ProtoSDK.initialize(this, ProtoSDKConfig())
  }
}
```

### Collect Fingerprint

```kotlin
lifecycleScope.launch {
  val result = ProtoSDK.getInstance().collectFingerprint()

  if (result.success) {
    val fingerprint = result.fingerprint
    val data = result.data.toJsonWithTimestamp()

    sendToBackend(fingerprint, data)
  }
}
```

Returned values:

* `fingerprint`: stable SHA-256 hash
* `data`: structured JSON (all collected signals)
* `hasErrors`: whether any collectors failed

---

## Configuration

```kotlin
ProtoSDK.initialize(
  context,
  ProtoSDKConfig(
    enableTimeout = true,
    enableDebugLogging = false,
    defaultTimeoutMs = 10_000L
  )
)
```

---

## Timeout Configuration

ProtoSDK provides fine-grained control over collection timeouts:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `defaultTimeoutMs` | 10000 | Overall fingerprint collection timeout |
| `collectorTimeoutMs` | 500 | Timeout per individual collector |
| `rootDetectionTimeoutMs` | 200 | Root detection internal timeout |
| `emulatorDetectionTimeoutMs` | 300 | Emulator detection internal timeout |
| `gpuCollectionTimeoutMs` | 200 | GPU info collection timeout |
| `gpuSignalWaitMs` | 80 | Wait time for GPU signals |
| `sensorSignalWaitMs` | 50 | Wait time for sensor signals |
| `sensorCheckWindowMs` | 50 | Native sensor polling window (ms) |
| `signalPollIntervalMs` | 20 | Signal bus polling interval |
| `batterySampleIntervalMs` | 5 | Battery sampling interval |

Example with custom timeouts for faster collection:

```kotlin
ProtoSDK.initialize(
  context,
  ProtoSDKConfig(
    defaultTimeoutMs = 5_000L,       // Faster overall timeout
    collectorTimeoutMs = 300L,       // Faster per-collector timeout
    sensorCheckWindowMs = 30,        // Shorter sensor check
  )
)
```

---

## Data Model

Signals are grouped into independent buckets:

| Bucket            | Description                                 |
| ----------------- | ------------------------------------------- |
| buildInfo         | Device model, manufacturer, Android version |
| deviceInfo        | Android ID, system settings, boot count     |
| displayInfo       | Screen metrics and refresh rate             |
| debugInfo         | ADB and debugger flags                      |
| rootDetection     | Root and tamper checks                      |
| emulatorDetection | Emulator and virtualization hints           |
| gpuInfo           | Renderer, vendor, capabilities              |
| cpuInfo           | Core count and frequencies                  |
| storageInfo       | RAM and disk stats                          |
| sensorInfo        | Available hardware sensors                  |
| networkInfo       | Network type, VPN, DNS                      |
| gsfId             | Google Services Framework ID                |
| widevine          | Widevine DRM device ID                      |

Only a **stable subset** of fields is used to generate the fingerprint hash.

---

## Extending the SDK

### Add Custom Collector

```kotlin
ProtoSDK.getInstance().addCollector("custom") {
  json {
    put("userTier", getUserTier())
    put("accountAgeDays", getAccountAge())
  }
}
```

### Remove Built-in Collectors

```kotlin
val sdk = ProtoSDK.getInstance()

sdk.removeCollector("networkInfo")
sdk.removeCollector("sensorInfo")
```

### Collect Single Bucket

```kotlin
val gpuInfo = sdk.collectCollectorData("gpuInfo")
```

---

## Performance

* Initialization: ~10ms
* Collection: ~200–300ms typical
* Memory: < 5MB peak
* Payload size: < 1MB JSON

Collectors run in parallel with per-collector timeouts.

---

## Backend Integration

ProtoSDK collects metadata and creates a fingerprint on-device, but this is all done on the client side. the real value comes from server-side processing—verifying devices, scoring risk, detecting fraud patterns, and tracking behavior over time.

```kotlin
lifecycleScope.launch {
  val result = ProtoSDK.getInstance().collectFingerprint()

  if (result.success) {
    // send to your backend
    api.submitFingerprint(
      fingerprint = result.fingerprint,
      data = result.data.toJsonWithTimestamp()
    )
  }
}
```

Your backend can then compare fingerprints against known devices, apply risk rules, and make trust decisions based on business context.

---

## Privacy

* No personal user data is collected (such as names, emails, phone numbers, or account identifiers)
* The SDK collects only device-level technical information
* No runtime permissions are requested beyond standard Android access

---

## Demo App

This repository includes a demo application that shows how to initialize the SDK and collect device data and the generated device fingerprint.

See app/src/main/java/com/protosdk/demo/MainActivity.kt for a simple example implementation.

---
