package com.protosdk.sdk.fingerprint

import android.content.Context
import com.protosdk.sdk.fingerprint.collectors.BuildInfoCollector
import com.protosdk.sdk.fingerprint.collectors.DebugInfoCollector
import com.protosdk.sdk.fingerprint.collectors.DeviceInfoCollector
import com.protosdk.sdk.fingerprint.collectors.DisplayInfoCollector
import com.protosdk.sdk.fingerprint.collectors.EmulatorDetectionCollector
import com.protosdk.sdk.fingerprint.collectors.GpuInfoCollector
import com.protosdk.sdk.fingerprint.collectors.CpuInfoCollector
import com.protosdk.sdk.fingerprint.collectors.StorageInfoCollector
import com.protosdk.sdk.fingerprint.collectors.SensorInfoCollector
import com.protosdk.sdk.fingerprint.collectors.NetworkInfoCollector
import com.protosdk.sdk.fingerprint.collectors.RootDetectionCollector
import com.protosdk.sdk.fingerprint.collectors.GsfIdCollector
import com.protosdk.sdk.fingerprint.collectors.MediaDrmCollector
import com.protosdk.sdk.fingerprint.interfaces.FingerprintCollector
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class FingerprintManager
private constructor(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
  private val collectors = ConcurrentHashMap<String, FingerprintCollector>()
  private var isInitialized = false

  companion object {
    @Volatile private var INSTANCE: FingerprintManager? = null

    fun getInstance(context: Context): FingerprintManager = INSTANCE
      ?: synchronized(this) {
        INSTANCE ?: FingerprintManager(context.applicationContext).also { INSTANCE = it }
      }

    fun createInstance(
      context: Context,
      scope: CoroutineScope,
    ): FingerprintManager = FingerprintManager(context.applicationContext, scope)
  }

  init {
    initializeCollectors()
  }

  private fun initializeCollectors() {
    if (isInitialized) return

    // Initialize collectors
    collectors["buildInfo"] = BuildInfoCollector()
    collectors["deviceInfo"] = DeviceInfoCollector()
    collectors["displayInfo"] = DisplayInfoCollector()
    collectors["debugInfo"] = DebugInfoCollector()
    collectors["rootInfo"] = RootDetectionCollector()
    collectors["emulatorInfo"] = EmulatorDetectionCollector()
    collectors["gpuInfo"] = GpuInfoCollector()
    collectors["cpuInfo"] = CpuInfoCollector()
    collectors["storageInfo"] = StorageInfoCollector()
    collectors["sensorInfo"] = SensorInfoCollector()
    collectors["networkInfo"] = NetworkInfoCollector()
    collectors["gsfInfo"] = GsfIdCollector()
    collectors["mediaDrmInfo"] = MediaDrmCollector()

    isInitialized = true
  }

  /** Collects fingerprint data asynchronously without blocking the main thread */
  suspend fun collectFingerprintAsync(): FingerprintResult {
    val startTime = System.currentTimeMillis()

    return try {
      val fingerprintData = withContext(Dispatchers.IO) { collectAllData() }

      val fingerprintString = generateFingerprintString(fingerprintData)
      val fingerprintHash = generateFingerprintHash(fingerprintString)

      val collectionTime = System.currentTimeMillis() - startTime

      FingerprintResult(
        data = fingerprintData,
        fingerprint = fingerprintHash,
        success = true,
        error = null,
        collectionTimeMs = collectionTime,
      )
    } catch (e: Exception) {
      val collectionTime = System.currentTimeMillis() - startTime

      FingerprintResult(
        data = FingerprintData(),
        fingerprint = "",
        success = false,
        error = e.message ?: "Unknown error occurred",
        collectionTimeMs = collectionTime,
      )
    }
  }

  /** Collects fingerprint data with timeout to prevent hanging */
  suspend fun collectFingerprintWithTimeout(timeoutMs: Long = 10000): FingerprintResult = try {
    withTimeout(timeoutMs) { collectFingerprintAsync() }
  } catch (e: TimeoutCancellationException) {
    FingerprintResult(
      data = FingerprintData(),
      fingerprint = "",
      success = false,
      error = "Fingerprint collection timed out after ${timeoutMs}ms",
      collectionTimeMs = timeoutMs,
    )
  } catch (e: Exception) {
    FingerprintResult(
      data = FingerprintData(),
      fingerprint = "",
      success = false,
      error = e.message ?: "Unknown error occurred",
      collectionTimeMs = 0,
    )
  }

  /** Collects fingerprint data in parallel for better performance */
  private suspend fun collectAllData(): FingerprintData {
    val deferredResults = mutableListOf<Deferred<Pair<String, JSONObject>>>()

    // Launch all collectors in parallel with individual timeouts
    collectors.forEach { (name, collector) ->
      if (collector.isSupported(context)) {
        val deferred =
          scope.async {
            try {
              // Check permissions before attempting collection
              if (!collector.hasRequiredPermissions(context)) {
                val errorData = JSONObject().apply {
                  put("error", "Missing required permissions")
                  put("permissionRequired", true)
                  put("missingPermissions", JSONArray(collector.getRequiredPermissions()))
                }
                Pair(name, errorData)
              } else {
                withTimeout(3000) {
                  // 3 second timeout per collector
                  val data = collector.collect(context)
                  Pair(name, data)
                }
              }
            } catch (e: TimeoutCancellationException) {
              val errorData =
                JSONObject().apply {
                  put("error", "Collection timed out after 3000ms")
                  put("permissionRequired", false)
                }
              Pair(name, errorData)
            } catch (e: Exception) {
              val errorData =
                JSONObject().apply {
                  put("error", "Collection failed: ${e.message}")
                  put("permissionRequired", e is SecurityException)
                }
              Pair(name, errorData)
            }
          }
        deferredResults.add(deferred)
      }
    }

    // Wait for all collectors to complete
    val results = deferredResults.awaitAll()

    // Build fingerprint data
    val fingerprintData = FingerprintData().apply {
      results.forEach { (name, data) ->
        when (name) {
          "buildInfo" -> buildInfo = data
          "deviceInfo" -> deviceInfo = data
          "displayInfo" -> displayInfo = data
          "debugInfo" -> debugInfo = data
          "rootInfo" -> rootInfo = data
          "emulatorInfo" -> emulatorInfo = data
          "gpuInfo" -> gpuInfo = data
          "cpuInfo" -> cpuInfo = data
          "storageInfo" -> storageInfo = data
          "sensorInfo" -> sensorInfo = data
          "networkInfo" -> networkInfo = data
          "gsfInfo" -> gsfInfo = data
          "mediaDrmInfo" -> mediaDrmInfo = data
        }
      }
    }

    // Validate JSON size before returning
    try {
      fingerprintData.getJsonSize()
    } catch (e: IllegalStateException) {
      throw IllegalStateException("Fingerprint data too large: ${e.message}")
    }

    return fingerprintData
  }

  /** Collects specific collector data */
  suspend fun collectCollectorData(collectorName: String): JSONObject? {
    val collector = collectors[collectorName] ?: return null

    if (!collector.isSupported(context)) {
      return JSONObject().apply {
        put("error", "Collector not supported on this device")
        put("supported", false)
      }
    }

    return try {
      withContext(Dispatchers.IO) { collector.collect(context) }
    } catch (e: Exception) {
      JSONObject().apply {
        put("error", "Collection failed: ${e.message}")
        put("permissionRequired", e is SecurityException)
      }
    }
  }

  /** Gets list of available collectors */
  fun getAvailableCollectors(): List<String> = collectors.filter { (_, collector) -> collector.isSupported(context) }.keys.toList()

  /** Gets collector information */
  fun getCollectorInfo(collectorName: String): CollectorInfo? {
    val collector = collectors[collectorName] ?: return null

    return CollectorInfo(
      name = collector.getCollectorName(),
      requiredPermissions = collector.getRequiredPermissions(),
      isSupported = collector.isSupported(context),
    )
  }

  /** Gets all collectors information */
  fun getAllCollectorsInfo(): List<CollectorInfo> = collectors.map { (_, collector) ->
    CollectorInfo(
      name = collector.getCollectorName(),
      requiredPermissions = collector.getRequiredPermissions(),
      isSupported = collector.isSupported(context),
    )
  }

  /** Checks if all required permissions are granted */
  fun hasAllPermissions(): Boolean {
    return collectors.values.all { collector ->
      if (!collector.isSupported(context)) return@all true
      collector.hasRequiredPermissions(context)
    }
  }

  /** Adds a custom collector */
  fun addCollector(
    name: String,
    collector: FingerprintCollector,
  ) {
    collectors[name] = collector
  }

  /** Removes a collector */
  fun removeCollector(name: String) {
    collectors.remove(name)
  }

  /** Clears all collectors */
  fun clearCollectors() {
    collectors.clear()
    isInitialized = false
  }

  /** Reinitializes collectors */
  fun reinitializeCollectors() {
    clearCollectors()
    initializeCollectors()
  }

  /** Cancels all ongoing operations */
  fun cancelAll() {
    scope.cancel()
  }

  /** Generates fingerprint string from data (excluding timestamp for consistency) */
  private fun generateFingerprintString(data: FingerprintData): String = data.toJson().toString()

  /** Generates fingerprint hash */
  private fun generateFingerprintHash(data: String): String = try {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(data.toByteArray())
    hash.joinToString("") { "%02x".format(it) }
  } catch (e: Exception) {
    ""
  }

  /** Collector information data class */
  data class CollectorInfo(
    val name: String,
    val requiredPermissions: List<String>,
    val isSupported: Boolean,
  )
}
