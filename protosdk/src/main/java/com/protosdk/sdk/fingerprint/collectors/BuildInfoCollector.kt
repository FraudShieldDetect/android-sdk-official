package com.protosdk.sdk.fingerprint.collectors

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import org.json.JSONArray
import org.json.JSONObject

class BuildInfoCollector : BaseCollector() {
  @SuppressLint("HardwareIds")
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    JSONObject().apply {
      // Basic build information
      collectDataPoint("board") { Build.BOARD }
      collectDataPoint("brand") { Build.BRAND }
      collectDataPoint("device") { Build.DEVICE }
      collectDataPoint("display") { Build.DISPLAY }
      collectDataPoint("fingerprint") { Build.FINGERPRINT }
      collectDataPoint("hardware") { Build.HARDWARE }
      collectDataPoint("host") { Build.HOST }
      collectDataPoint("manufacturer") { Build.MANUFACTURER }
      collectDataPoint("model") { Build.MODEL }
      collectDataPoint("product") { Build.PRODUCT }
      collectDataPoint("serial") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          runCatching { Build.getSerial() }.getOrDefault(Build.SERIAL)
        } else {
          Build.SERIAL
        }
      }
      collectDataPoint("tags") { Build.TAGS }
      collectDataPoint("time") { Build.TIME }
      collectDataPoint("type") { Build.TYPE }
      // put("user", Build.USER)

      // Radio information
      collectDataPoint("radioVersion") { Build.getRadioVersion() }

      // CPU ABI information
      collectDataPoint("supportedAbis") { JSONArray(Build.SUPPORTED_ABIS.toList()) }
      collectDataPoint("supported32BitAbis") { JSONArray(Build.SUPPORTED_32_BIT_ABIS.toList()) }
      collectDataPoint("supported64BitAbis") { JSONArray(Build.SUPPORTED_64_BIT_ABIS.toList()) }

      // SoC information (API 31+)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        collectDataPoint("socManufacturer") { Build.SOC_MANUFACTURER }
        collectDataPoint("socModel") { Build.SOC_MODEL }
      }
    }
  }

  override fun getCollectorName(): String = "BuildInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true
}
