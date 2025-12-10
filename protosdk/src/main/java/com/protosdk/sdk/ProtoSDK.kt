package com.protosdk.sdk

import android.content.Context
import com.protosdk.sdk.fingerprint.FingerprintData
import com.protosdk.sdk.fingerprint.FingerprintManager
import com.protosdk.sdk.fingerprint.FingerprintResult
import com.protosdk.sdk.fingerprint.interfaces.FingerprintCollector
import kotlinx.coroutines.*

/**
 * Main ProtoSDK class for device fingerprinting
 *
 * This class provides a device fingerprinting solution that collects various device characteristics
 * without blocking the application flow.
 */
class ProtoSDK
private constructor(
  private val context: Context,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
  private val fingerprintManager: FingerprintManager =
    FingerprintManager.createInstance(context, scope)

  companion object {
    @Volatile private var INSTANCE: ProtoSDK? = null

    /** Gets the singleton instance of ProtoSDK */
    fun getInstance(context: Context): ProtoSDK = INSTANCE
      ?: synchronized(this) {
        INSTANCE ?: ProtoSDK(context.applicationContext).also { INSTANCE = it }
      }

    /** Creates a new instance with custom scope */
    fun createInstance(
      context: Context,
      scope: CoroutineScope,
    ): ProtoSDK = ProtoSDK(context.applicationContext, scope)

    /** Initializes the SDK with configuration */
    fun initialize(
      context: Context,
      config: ProtoSDKConfig = ProtoSDKConfig(),
    ): ProtoSDK {
      val instance = getInstance(context)
      instance.applyConfig(config)
      return instance
    }
  }

  private var config: ProtoSDKConfig = ProtoSDKConfig()

  /**
   * Collects device fingerprint asynchronously
   *
   * @param timeoutMs Maximum time to wait for collection (default: 10 seconds)
   * @return FingerprintResult containing the fingerprint data and hash
   */
  suspend fun collectFingerprint(timeoutMs: Long = 10000): FingerprintResult = try {
    if (config.enableTimeout) {
      fingerprintManager.collectFingerprintWithTimeout(timeoutMs)
    } else {
      fingerprintManager.collectFingerprintAsync()
    }
  } catch (e: Exception) {
    FingerprintResult(
      data = FingerprintData(),
      fingerprint = "",
      success = false,
      error = "Fingerprint collection failed: ${e.message}",
      collectionTimeMs = 0,
    )
  }

  /** Checks if all required permissions are granted */
  fun hasAllPermissions(): Boolean = fingerprintManager.hasAllPermissions()

  /** Gets available collectors */
  fun getAvailableCollectors(): List<String> = fingerprintManager.getAvailableCollectors()

  /** Gets collector information */
  fun getCollectorInfo(collectorName: String): FingerprintManager.CollectorInfo? = fingerprintManager.getCollectorInfo(collectorName)

  /** Adds custom collector */
  fun addCollector(
    name: String,
    collector: FingerprintCollector,
  ) {
    fingerprintManager.addCollector(name, collector)
  }

  /** Removes collector */
  fun removeCollector(name: String) {
    fingerprintManager.removeCollector(name)
  }

  /** Applies configuration */
  private fun applyConfig(newConfig: ProtoSDKConfig) {
    this.config = newConfig
  }

  /** Cleans up resources */
  fun cleanup() {
    scope.cancel()
  }
}

/** Configuration class for ProtoSDK */
data class ProtoSDKConfig(
  val enableTimeout: Boolean = true,
  val enableDebugLogging: Boolean = false,
  val defaultTimeoutMs: Long = 10000,
)
