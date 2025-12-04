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
      put("board", Build.BOARD)
      put("brand", Build.BRAND)
      put("device", Build.DEVICE)
      put("display", Build.DISPLAY)
      put("fingerprint", Build.FINGERPRINT)
      put("hardware", Build.HARDWARE)
      put("host", Build.HOST)
      put("manufacturer", Build.MANUFACTURER)
      put("model", Build.MODEL)
      put("product", Build.PRODUCT)
      put(
        "serial",
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Build.getSerial()
          } else {
            Build.SERIAL
          }
        } catch (e: Exception) {},
      )
      put("tags", Build.TAGS)
      put("time", Build.TIME)
      put("type", Build.TYPE)
      // put("user", Build.USER)

      // Radio information
      put(
        "radioVersion",
        try {
          Build.getRadioVersion()
        } catch (e: Exception) {},
      )

      // CPU ABI information
      put("supportedAbis", JSONArray(Build.SUPPORTED_ABIS.toList()))
      put("supported32BitAbis", JSONArray(Build.SUPPORTED_32_BIT_ABIS.toList()))
      put("supported64BitAbis", JSONArray(Build.SUPPORTED_64_BIT_ABIS.toList()))

      // SoC information (API 31+)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        put("socManufacturer", Build.SOC_MANUFACTURER)
        put("socModel", Build.SOC_MODEL)
      }
    }
  }

  override fun getCollectorName(): String = "BuildInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true
}
