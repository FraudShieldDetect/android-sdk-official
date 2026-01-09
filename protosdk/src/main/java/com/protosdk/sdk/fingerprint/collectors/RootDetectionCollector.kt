package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import android.os.Debug
import com.protosdk.sdk.fingerprint.integrity.DexIntegrityVerifier
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import com.protosdk.sdk.fingerprint.internal.CollectorConfigHolder
import com.protosdk.sdk.fingerprint.internal.RootStringDecoder
import com.protosdk.sdk.fingerprint.nativebridge.RootDetectionBridge
import com.protosdk.sdk.fingerprint.utils.SecurityCollectorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

class RootDetectionCollector(
  private val bridge: RootDetectionBridge = RootDetectionBridge,
  private val random: SecureRandom = SecureRandom(),
) : BaseCollector() {

  override suspend fun collect(context: Context): JSONObject = withContext(Dispatchers.IO) {
    try {
      withTimeout(CollectorConfigHolder.config.rootDetectionTimeoutMs) {
        val integrityVerified =
          runCatching { DexIntegrityVerifier.verify(context) }.getOrDefault(false)
        safeCollect { performDetection(context, integrityVerified) }
      }
    } catch (timeout: TimeoutCancellationException) {
      JSONObject().apply {
        put("rootIndicators", JSONArray())
        put("integrityVerified", false)
        put("isRooted", false)
        put("nativeChecksPerformed", 0)
        put("antiDebugTriggered", false)
        put("timeout", true)
      }
    }
  }

  private fun performDetection(
    context: Context,
    integrityVerified: Boolean,
  ): JSONObject {
    val indicators = linkedSetOf<String>()
    var nativeChecks = 0
    var highConfidenceSignals = 0
    val operationErrors = mutableListOf<String>()

    fun recordIndicator(indicator: String, isHighConfidence: Boolean) {
      val added = indicators.add(indicator)
      if (added && isHighConfidence) {
        highConfidenceSignals += 1
      }
    }

    fun stat(path: String): Boolean {
      nativeChecks += 1
      return bridge.nativeStat(path)
    }

    fun property(key: String): String {
      nativeChecks += 1
      return bridge.nativeGetProperty(key)
    }

    val operations = mutableListOf<() -> Unit>()

    operations += {
      RootStringDecoder.paths()
        .shuffled(random)
        .forEach { path ->
          if (stat(path)) {
            val highConfidence = highConfidencePathKeywords.any { path.contains(it, ignoreCase = true) }
            recordIndicator("path:$path", highConfidence)
          }
        }
    }

    operations += {
      RootStringDecoder.properties()
        .shuffled(random)
        .forEach { key ->
          val rule = propertyAlerts[key] ?: return@forEach
          val value = property(key)
          if (rule.matcher.invoke(value)) {
            recordIndicator("property:$key=$value", rule.highConfidence)
          }
        }
    }

    operations += {
      RootStringDecoder.packages()
        .shuffled(random)
        .forEach { packageName ->
          if (SecurityCollectorUtils.isAppInstalled(context, packageName)) {
            val highConfidence = highConfidencePackageNames.contains(packageName)
            recordIndicator("package:$packageName", highConfidence)
          }
        }
    }

    operations += {
      RootStringDecoder.env()
        .shuffled(random)
        .forEach { envVar ->
          val value = System.getenv(envVar) ?: return@forEach
          if (value.isEmpty()) return@forEach
          val lower = value.lowercase()
          if (envSignals.any { lower.contains(it) }) {
            recordIndicator("env:$envVar", false)
          }
        }
    }

    operations.shuffle(random)
    operations.forEach { operation ->
      runCatching { operation.invoke() }.onFailure { throwable ->
        operationErrors += throwable.message ?: throwable::class.java.simpleName
      }
    }

    val tracerPid = bridge.nativeTracerPid().also { nativeChecks += 1 }
    val antiDebugTriggered = Debug.isDebuggerConnected() || tracerPid > 0
    val isRooted = highConfidenceSignals > 0 || indicators.size >= MIN_INDICATORS_FOR_ROOT

    val orderedIndicators = indicators.toList().sorted()
    val orderedWarnings = operationErrors.sorted()

    return JSONObject().apply {
      collectDataPoint("rootIndicators") { JSONArray().apply { orderedIndicators.forEach { put(it) } } }
      collectDataPoint("integrityVerified") { integrityVerified }
      collectDataPoint("isRooted") { isRooted }
      collectDataPoint("nativeChecksPerformed") { nativeChecks }
      collectDataPoint("antiDebugTriggered") { antiDebugTriggered }
      collectDataPoint("highConfidenceSignals") { highConfidenceSignals }
      if (orderedWarnings.isNotEmpty()) {
        collectDataPoint("collectionWarnings") { JSONArray(orderedWarnings) }
      }
    }
  }

  override fun getCollectorName(): String = "RootDetectionCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  private val envSignals =
    listOf("su", "busybox", "magisk", "/data/local", "/system/xbin", "kernelsu")

  private data class PropertyAlert(
    val matcher: (String) -> Boolean,
    val highConfidence: Boolean,
  )

  private val propertyAlerts =
    mapOf(
      "ro.debuggable" to PropertyAlert(matcher = { it == "1" }, highConfidence = false),
      "ro.secure" to PropertyAlert(matcher = { it == "0" }, highConfidence = false),
      "ro.boot.vbmeta.device_state" to
        PropertyAlert(
          matcher = { it.equals("unlocked", ignoreCase = true) },
          highConfidence = true,
        ),
      "ro.boot.verifiedbootstate" to
        PropertyAlert(
          matcher = { it.equals("orange", ignoreCase = true) },
          highConfidence = true,
        ),
      "ro.boot.flash.locked" to PropertyAlert(matcher = { it == "0" }, highConfidence = true),
      "ro.boot.veritymode" to
        PropertyAlert(matcher = { it.equals("logging", ignoreCase = true) }, highConfidence = false),
      "ro.boot.warranty_bit" to PropertyAlert(matcher = { it == "0" }, highConfidence = true),
    )

  private val highConfidencePathKeywords = listOf("magisk", "kernelsu", "zygisk", "ksu")

  private val highConfidencePackageNames =
    setOf(
      "com.topjohnwu.magisk",
      "eu.chainfire.supersu",
      "com.kingroot.kinguser",
      "com.topjohnwu.magisk.beta",
    )

  private companion object {
    const val MIN_INDICATORS_FOR_ROOT = 3
  }
}
