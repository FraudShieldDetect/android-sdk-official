package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import com.protosdk.sdk.fingerprint.internal.SensorSignalBus
import com.protosdk.sdk.fingerprint.internal.SensorSignals
import org.json.JSONArray
import org.json.JSONObject

class SensorInfoCollector : BaseCollector() {
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    val sensorManager =
      context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ?: return@safeCollect JSONObject().apply {
          put("error", "sensorManagerUnavailable")
        }

    val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
    val dynamicSensors =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        sensorManager.getDynamicSensorList(Sensor.TYPE_ALL)
      } else {
        emptyList()
      }

    val sensorArray = JSONArray()
    sensors.forEach { sensor ->
      val obj =
        JSONObject().apply {
          put("name", sensor.name)
          put("vendor", sensor.vendor)
          put("type", sensor.type)
          put(
            "stringType",
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
              sensor.stringType
            } else {
              null
            },
          )
          put("version", sensor.version)
          put("powerMa", sensor.power)
          put("maxRange", sensor.maximumRange)
          put("resolution", sensor.resolution)
          put("minDelayMicros", sensor.minDelay)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            put("maxDelayMicros", sensor.maxDelay)
            put("fifoMaxEventCount", sensor.fifoMaxEventCount)
            put("fifoReservedEventCount", sensor.fifoReservedEventCount)
            put("isWakeUp", sensor.isWakeUpSensor)
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            put("isDynamic", sensor.isDynamicSensor)
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            put("reportingMode", reportingModeToString(sensor.reportingMode))
          }
        }
      sensorArray.put(obj)
    }

    val vendorCounts = sensors.groupingBy { it.vendor }.eachCount()
    val aospGoldfishCount =
      sensors.count {
        val v = it.vendor.lowercase()
        v.contains("goldfish") || v.contains("the android open source project") || v == "aosp"
      }

    val missingCommon =
      listOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD,
      )
        .filter { sensorManager.getDefaultSensor(it) == null }

    publishEmulatorSignals(sensors, missingCommon, vendorCounts)

    JSONObject().apply {
      put("totalSensors", sensors.size)
      put("dynamicSensorCount", dynamicSensors.size)
      put("aospGoldfishSensorCount", aospGoldfishCount)
      put("vendorCounts", JSONObject(vendorCounts))
      put("missingCommonSensors", JSONArray(missingCommon))
      put("sensors", sensorArray)
    }
  }

  private fun reportingModeToString(mode: Int): String = when (mode) {
    Sensor.REPORTING_MODE_CONTINUOUS -> "continuous"
    Sensor.REPORTING_MODE_ON_CHANGE -> "on_change"
    Sensor.REPORTING_MODE_ONE_SHOT -> "one_shot"
    Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "special_trigger"
    else -> "unknown"
  }

  override fun getCollectorName(): String = "SensorInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true

  private fun publishEmulatorSignals(
    sensors: List<Sensor>,
    missingCommon: List<Int>,
    vendorCounts: Map<String, Int>,
  ) {
    val locale = java.util.Locale.ROOT
    val indicators = mutableListOf<String>()
    var score = 0.0
    var highConfidence = 0

    fun hit(label: String, weight: Double, high: Boolean = false) {
      indicators += label
      score += weight
      if (high) highConfidence += 1
    }

    val uniqueVendors = vendorCounts.size
    val uniqueTypes = sensors.map { it.type }.distinct().size
    val missingCore = missingCommon.size

    val goldfishVendorHit =
      vendorCounts.keys.any {
        it.contains("goldfish") ||
          it.contains("aosp") ||
          it.contains("android open source project")
      }
    val emulatorNameHit =
      sensors.any {
        val n = it.name.lowercase(locale)
        n.contains("goldfish") || n.contains("emulator") || n.contains("android sdk")
      }
    val vendorAllSame = uniqueVendors == 1 && sensors.isNotEmpty()
    val onlyAospVendors =
      sensors.isNotEmpty() &&
        vendorCounts.keys.all {
          it.contains("android open source project") ||
            it.contains("aosp") ||
            it.contains("goldfish")
        }
    val veryFewSensors = sensors.size <= 5
    val veryLowUniqueTypes = uniqueTypes <= 4
    val buildFingerprint = Build.FINGERPRINT.lowercase(locale)
    val buildHardware = Build.HARDWARE.lowercase(locale)
    val buildModel = Build.MODEL.lowercase(locale)

    if (sensors.isEmpty()) hit("sensors:none", WEIGHT_HIGH, true)
    if (veryFewSensors) hit("sensors:very_few", WEIGHT_MEDIUM)
    if (missingCore >= 2) hit("sensors:missing_core", WEIGHT_MEDIUM)
    if (goldfishVendorHit) hit("sensors:goldfish_vendor", WEIGHT_HIGH, true)
    if (emulatorNameHit) hit("sensors:emulator_named", WEIGHT_HIGH, true)
    if (vendorAllSame) hit("sensors:single_vendor", WEIGHT_LOW)
    if (onlyAospVendors) hit("sensors:aosp_only_vendors", WEIGHT_HIGH, true)
    if (veryLowUniqueTypes) hit("sensors:few_unique_types", WEIGHT_LOW)
    if (buildFingerprint.contains("generic") || buildFingerprint.contains("sdk_gphone")) {
      hit("sensors:fingerprint_generic", WEIGHT_MEDIUM)
    }
    if (buildHardware.contains("goldfish") || buildHardware.contains("ranchu")) {
      hit("sensors:hardware_goldfish", WEIGHT_HIGH, true)
    }
    if (buildModel.contains("sdk") || buildModel.contains("emulator")) {
      hit("sensors:model_emulator", WEIGHT_MEDIUM)
    }

    val confidence = kotlin.math.min(1.0, score)
    val suspected = highConfidence > 0 || confidence >= 0.6

    SensorSignalBus.publish(
      SensorSignals(
        confidenceScore = confidence,
        suspectedEmulation = suspected,
        indicators = indicators,
        totalSensors = sensors.size,
        missingCoreCount = missingCore,
        uniqueVendors = uniqueVendors,
        uniqueTypes = uniqueTypes,
        timestampMs = System.currentTimeMillis(),
      ),
    )
  }

  private companion object {
    const val WEIGHT_HIGH = 0.35
    const val WEIGHT_MEDIUM = 0.2
    const val WEIGHT_LOW = 0.1
  }
}
