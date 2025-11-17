package com.protosdk.sdk.fingerprint.collectors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.telephony.TelephonyManager
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import com.protosdk.sdk.fingerprint.internal.EmulatorStringDecoder
import com.protosdk.sdk.fingerprint.nativebridge.EmulatorDetectionBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Locale
import kotlin.math.min

class EmulatorDetectionCollector(
  private val bridge: EmulatorDetectionBridge = EmulatorDetectionBridge,
  private val random: SecureRandom = SecureRandom(),
) : BaseCollector() {

  override suspend fun collect(context: Context): JSONObject = withContext(Dispatchers.IO) {
    try {
      withTimeout(150L) { safeCollect { performDetection(context) } }
    } catch (timeout: TimeoutCancellationException) {
      JSONObject().apply {
        put("emulatorIndicators", JSONArray())
        put("isEmulator", false)
        put("hardwareChecksPassed", 0)
        put("confidenceScore", 0.0)
        put("antiDebugTriggered", false)
        put("timeout", true)
      }
    }
  }

  private fun performDetection(context: Context): JSONObject {
    val indicators = linkedSetOf<String>()
    var suspicionScore = 0.0
    var highConfidenceSignals = 0
    var hardwareChecksPassed = 0

    fun recordIndicator(label: String, weight: Double, highConfidence: Boolean = false) {
      if (indicators.add(label)) {
        suspicionScore += weight
        if (highConfidence) {
          highConfidenceSignals += 1
        }
      }
    }

    val locale = Locale.ROOT
    val cpuInfo = bridge.nativeReadCpuInfo().lowercase(locale)
    val arpTable = bridge.nativeReadProc("/proc/net/arp").lowercase(locale)
    val networkIfaces = bridge.nativeGetNetworkIfaces().toList()
    val propertyMap = EmulatorStringDecoder.properties().associateWith { key ->
      bridge.nativeGetProperty(key)
    }
    val deviceTokens = EmulatorStringDecoder.devices().map { it.lowercase(locale) }

    val operations = mutableListOf<() -> Unit>()

    operations += {
      EmulatorStringDecoder.paths().shuffled(random).forEach { path ->
        if (bridge.nativeStat(path)) {
          recordIndicator("artifact:$path", WEIGHT_HIGH, highConfidence = true)
        }
      }
    }

    operations += {
      val descriptor =
        listOf(
          Build.FINGERPRINT,
          Build.HARDWARE,
          Build.MODEL,
          Build.MANUFACTURER,
          Build.BRAND,
          Build.DEVICE,
          Build.PRODUCT,
          Build.BOARD,
        )
          .joinToString(separator = " ")
          .lowercase(locale)
      deviceTokens.forEach { token ->
        if (token.isNotBlank() && descriptor.contains(token)) {
          recordIndicator("device_tag:$token", WEIGHT_MEDIUM, highConfidence = token in HIGH_CONF_DEVICE_TOKENS)
        }
      }
    }

    operations += {
      propertyMap.forEach { (key, value) ->
        val lower = value.lowercase(locale)
        when (key) {
          "ro.kernel.qemu" ->
            if (lower == "1" || lower == "true") {
              recordIndicator("property:$key=$lower", WEIGHT_HIGH, highConfidence = true)
            }
          "ro.product.manufacturer" ->
            if (lower in suspiciousManufacturers) {
              recordIndicator("property:$key=$lower", WEIGHT_MEDIUM)
            }
          else -> {
            val hit = deviceTokens.any { token ->
              token.isNotBlank() && lower.contains(token)
            }
            if (hit) {
              recordIndicator("property:$key=$lower", WEIGHT_MEDIUM)
            }
          }
        }
      }
    }

    operations += {
      val procTokens = EmulatorStringDecoder.procTokens()
      procTokens.forEach { token ->
        val hit = token.isNotBlank() && cpuInfo.contains(token.lowercase(locale))
        if (hit) {
          recordIndicator("proc:$token", WEIGHT_MEDIUM)
        }
      }
      if (cpuInfo.contains("intel") && Build.SUPPORTED_ABIS.any { it.lowercase(locale).contains("arm") }) {
        recordIndicator("cpu:mixed_vendor", WEIGHT_MEDIUM, highConfidence = true)
      }
    }

    operations += {
      val macPrefixes = EmulatorStringDecoder.macPrefixes().map { it.lowercase(locale) }
      val hasWifi = networkIfaces.any { it.startsWith("wlan", ignoreCase = true) }
      if (!hasWifi) {
        recordIndicator("network:no_wlan", WEIGHT_LOW)
      }
      networkIfaces.forEach { raw ->
        val parts = raw.split('|')
        if (parts.size < 2) return@forEach
        val iface = parts[0]
        val mac = parts[1].lowercase(locale)
        macPrefixes.firstOrNull { mac.startsWith(it) }?.let { prefix ->
          recordIndicator("network:mac:$iface:$prefix", WEIGHT_MEDIUM, highConfidence = true)
        }
      }
      EmulatorStringDecoder.ipRanges().forEach { range ->
        if (range.isNotBlank() && arpTable.contains(range.lowercase(locale))) {
          recordIndicator("network:arp:$range", WEIGHT_MEDIUM)
        }
      }
    }

    operations += {
      val sensorStats = bridge.nativeCheckSensors(90)
      val sampled = sensorStats.takeIf { it.size >= 1 }?.get(0) ?: 0
      val varying = sensorStats.takeIf { it.size >= 2 }?.get(1) ?: 0
      val sensorsHealthy = sampled >= 2 && varying >= 1
      if (!sensorsHealthy) {
        recordIndicator("sensors:static", WEIGHT_MEDIUM)
      } else {
        hardwareChecksPassed += 1
      }
    }

    operations += {
      val (batteryHealthy, reason) = evaluateBattery(context)
      if (batteryHealthy) {
        hardwareChecksPassed += 1
      } else {
        reason?.let { recordIndicator("battery:$it", WEIGHT_LOW) }
      }
    }

    operations += {
      evaluateImei(context)?.let { status ->
        if (!status.isValid) {
          recordIndicator("telephony:${status.reason}", WEIGHT_LOW)
        }
      }
    }

    operations += {
      val abiSuspicious = isAbiSuspicious(propertyMap)
      if (abiSuspicious) {
        recordIndicator("abi:x86_virtual", WEIGHT_HIGH, highConfidence = true)
      } else {
        hardwareChecksPassed += 1
      }
    }

    operations += {
      val neonPass = evaluateNeonProbe()
      if (neonPass) {
        hardwareChecksPassed += 1
      } else {
        recordIndicator("neon:probe_failed", WEIGHT_MEDIUM)
      }
    }

    operations.shuffle(random)
    operations.forEach { it.invoke() }

    val tracerPid = bridge.nativeTracerPid()
    val antiDebugTriggered = Debug.isDebuggerConnected() || tracerPid > 0
    if (antiDebugTriggered) {
      recordIndicator("anti_debug_triggered", WEIGHT_LOW)
    }

    val confidenceScore = min(1.0, suspicionScore)
    val isEmulator = highConfidenceSignals > 0 || confidenceScore >= 0.7

    return JSONObject().apply {
      put("emulatorIndicators", JSONArray().apply { indicators.forEach { put(it) } })
      put("isEmulator", isEmulator)
      put("hardwareChecksPassed", hardwareChecksPassed)
      put("confidenceScore", String.format(locale, "%.2f", confidenceScore).toDouble())
      put("antiDebugTriggered", antiDebugTriggered)
      put("highConfidenceSignals", highConfidenceSignals)
    }
  }

  private fun evaluateBattery(context: Context): Pair<Boolean, String?> {
    val batteryManager = context.getSystemService(BatteryManager::class.java) ?: return true to null
    val currentSamples = mutableListOf<Int>()
    repeat(3) { index ->
      val reading = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
      if (reading != Int.MIN_VALUE) {
        currentSamples += reading
      }
      if (index < 2) {
        SystemClock.sleep(10)
      }
    }
    val hasVariance = currentSamples.distinct().size > 1
    val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    val counterValid =
      if (chargeCounter == Int.MIN_VALUE) {
        true
      } else {
        chargeCounter != 0
      }
    if ((currentSamples.isEmpty() || hasVariance) && counterValid) {
      return true to null
    }
    return false to "static_battery"
  }

  private data class ImeiStatus(val isValid: Boolean, val reason: String)

  private fun evaluateImei(context: Context): ImeiStatus? {
    val telephony = context.getSystemService(TelephonyManager::class.java) ?: return null
    val hasPermission =
      context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED
    if (!hasPermission) return null
    val raw = try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        telephony.imei
      } else {
        @Suppress("DEPRECATION")
        telephony.deviceId
      }
    } catch (_: SecurityException) {
      null
    }
    val imei = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (imei.any { !it.isDigit() }) {
      return ImeiStatus(false, "imei_non_digit")
    }
    if (imei.length !in 14..16) {
      return ImeiStatus(false, "imei_length_${imei.length}")
    }
    return if (passesLuhn(imei)) ImeiStatus(true, "ok") else ImeiStatus(false, "imei_luhn")
  }

  private fun passesLuhn(value: String): Boolean {
    var sum = 0
    var shouldDouble = false
    for (i in value.length - 1 downTo 0) {
      var digit = value[i] - '0'
      if (shouldDouble) {
        digit *= 2
        if (digit > 9) digit -= 9
      }
      sum += digit
      shouldDouble = !shouldDouble
    }
    return sum % 10 == 0
  }

  private fun evaluateNeonProbe(): Boolean {
    val supportedAbis = Build.SUPPORTED_ABIS?.map { it.lowercase(Locale.ROOT) } ?: emptyList()
    val expectsNeon = supportedAbis.any { it.contains("arm") }
    val probeResult = bridge.nativeNeonProbe()
    return !expectsNeon || probeResult
  }

  private fun isAbiSuspicious(propertyMap: Map<String, String>): Boolean {
    val supportedAbis = Build.SUPPORTED_ABIS?.map { it.lowercase(Locale.ROOT) } ?: emptyList()
    val primary = supportedAbis.firstOrNull().orEmpty()
    val isX86 = primary.contains("x86")
    if (!isX86) return false
    val manufacturer = Build.MANUFACTURER?.lowercase(Locale.ROOT).orEmpty()
    if (manufacturer in physicalX86Vendors) return false
    val bootloaderProp = propertyMap["ro.bootloader"].orEmpty().lowercase(Locale.ROOT)
    val bootloader = (Build.BOOTLOADER?.lowercase(Locale.ROOT).orEmpty() + bootloaderProp)
    return bootloader.isBlank() || bootloader.contains("unknown")
  }

  override fun getCollectorName(): String = "EmulatorDetectionCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  private val suspiciousManufacturers = setOf("unknown", "genymotion", "netease", "bluestacks")

  private val physicalX86Vendors = setOf("asus", "lenovo", "acer")

  private val HIGH_CONF_DEVICE_TOKENS = setOf("bluestacks", "ldplayer", "genymotion")

  private companion object {
    const val WEIGHT_HIGH = 0.35
    const val WEIGHT_MEDIUM = 0.2
    const val WEIGHT_LOW = 0.1
  }
}
