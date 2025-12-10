package com.protosdk.sdk.fingerprint

import org.json.JSONObject

data class FingerprintData(
  var buildInfo: JSONObject = JSONObject(),
  var deviceInfo: JSONObject = JSONObject(),
  var displayInfo: JSONObject = JSONObject(),
  var debugInfo: JSONObject = JSONObject(),
  var rootInfo: JSONObject = JSONObject(),
  var emulatorInfo: JSONObject = JSONObject(),
  var gpuInfo: JSONObject = JSONObject(),
  var cpuInfo: JSONObject = JSONObject(),
  var storageInfo: JSONObject = JSONObject(),
  var networkInfo: JSONObject = JSONObject(),
  var sensorInfo: JSONObject = JSONObject(),
  var gsfInfo: JSONObject = JSONObject(),
  var mediaDrmInfo: JSONObject = JSONObject(),
  var timestamp: Long = System.currentTimeMillis(),
) {
  /** function to use the most stable data to build the fingerprint hash */
  fun toStableJson(): JSONObject {
    fun JSONObject.putIfPresent(source: JSONObject, key: String) {
      if (source.has(key)) {
        put(key, source.opt(key))
      }
    }

    val stableDevice =
      JSONObject().apply {
        putIfPresent(deviceInfo, "androidId")
        putIfPresent(deviceInfo, "deviceProvisioned")
      }

    val stableDisplay =
      JSONObject().apply {
        putIfPresent(displayInfo, "density")
        putIfPresent(displayInfo, "densityDpi")
        putIfPresent(displayInfo, "xdpi")
        putIfPresent(displayInfo, "ydpi")
        putIfPresent(displayInfo, "supportedRefreshRates")
        putIfPresent(displayInfo, "locales")
        putIfPresent(displayInfo, "locale")
        putIfPresent(displayInfo, "uiModeNight")
        putIfPresent(displayInfo, "screenLayoutSize")
      }

    val stableRoot =
      JSONObject().apply {
        putIfPresent(rootInfo, "isRooted")
        putIfPresent(rootInfo, "rootIndicators")
        putIfPresent(rootInfo, "nativeChecksPerformed")
        putIfPresent(rootInfo, "highConfidenceSignals")
      }

    val stableEmulator =
      JSONObject().apply {
        putIfPresent(emulatorInfo, "isEmulator")
        putIfPresent(emulatorInfo, "emulatorIndicators")
        putIfPresent(emulatorInfo, "hardwareChecksPassed")
        putIfPresent(emulatorInfo, "confidenceScore")
        putIfPresent(emulatorInfo, "highConfidenceSignals")
      }

    val stableCpu = JSONObject().apply { putIfPresent(cpuInfo, "topology") }

    val stableStorage =
      JSONObject().apply {
        putIfPresent(storageInfo, "storageTotalBytes")
        putIfPresent(storageInfo, "ramTotalBytes")
      }

    return JSONObject().apply {
      put("build", buildInfo)
      put("device", stableDevice)
      put("display", stableDisplay)
      put("debug", JSONObject())
      put("root", stableRoot)
      put("emulator", stableEmulator)
      put("gpu", gpuInfo)
      put("cpu", stableCpu)
      put("storage", stableStorage)
      put("network", JSONObject())
      put("sensors", sensorInfo)
      put("gsf", gsfInfo)
      put("mediaDrm", mediaDrmInfo)
    }
  }

  fun toJson(): JSONObject {
    val json =
      JSONObject().apply {
        put("build", buildInfo)
        put("device", deviceInfo)
        put("display", displayInfo)
        put("debug", debugInfo)
        put("root", rootInfo)
        put("emulator", emulatorInfo)
        put("gpu", gpuInfo)
        put("cpu", cpuInfo)
        put("storage", storageInfo)
        put("network", networkInfo)
        put("sensors", sensorInfo)
        put("gsf", gsfInfo)
        put("mediaDrm", mediaDrmInfo)
      }

    // Check JSON size (in bytes)
    val jsonString = json.toString()
    val sizeInBytes = jsonString.toByteArray(Charsets.UTF_8).size
    val maxSizeBytes = 1024 * 1024 // 1MB max

    if (sizeInBytes > maxSizeBytes) {
      throw IllegalStateException(
        "Fingerprint JSON too large: $sizeInBytes bytes (max: $maxSizeBytes bytes)",
      )
    }

    return json
  }

  fun toJsonWithTimestamp(): JSONObject {
    val json =
      JSONObject().apply {
        put("build", buildInfo)
        put("device", deviceInfo)
        put("display", displayInfo)
        put("debug", debugInfo)
        put("root", rootInfo)
        put("emulator", emulatorInfo)
        put("gpu", gpuInfo)
        put("cpu", cpuInfo)
        put("storage", storageInfo)
        put("network", networkInfo)
        put("sensors", sensorInfo)
        put("gsf", gsfInfo)
        put("mediaDrm", mediaDrmInfo)
        put("timestamp", timestamp)
      }

    // Check JSON size (in bytes)
    val jsonString = json.toString()
    val sizeInBytes = jsonString.toByteArray(Charsets.UTF_8).size
    val maxSizeBytes = 1024 * 1024 // 1MB max

    if (sizeInBytes > maxSizeBytes) {
      throw IllegalStateException(
        "Fingerprint JSON too large: $sizeInBytes bytes (max: $maxSizeBytes bytes)",
      )
    }

    return json
  }

  fun getJsonSize(): Int = toJson().toString().toByteArray(Charsets.UTF_8).size
}

data class FingerprintResult(
  val data: FingerprintData,
  val fingerprint: String,
  val success: Boolean,
  val error: String? = null,
  val collectionTimeMs: Long = 0,
) {
  fun toJson(): JSONObject = JSONObject().apply {
    put("data", data.toJson())
    put("fingerprint", fingerprint)
    put("success", success)
    put("error", error)
    put("collectionTimeMs", collectionTimeMs)
  }
}
