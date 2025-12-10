package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import android.provider.Settings
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import org.json.JSONObject

class DeviceInfoCollector : BaseCollector() {
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    JSONObject().apply {
      val resolver = context.contentResolver

      val androidId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID)
      collectDataPoint("androidId", fallback = "unknown") { androidId }

      collectDataPoint("developmentSettingsEnabled") {
        safeGetInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
      }

      collectDataPoint("bootCount") { safeGetInt(resolver, Settings.Global.BOOT_COUNT, 0) }

      collectDataPoint("deviceProvisioned") { safeGetInt(resolver, Settings.Global.DEVICE_PROVISIONED, 0) }

      collectDataPoint("stayOnWhilePluggedIn") {
        safeGetInt(resolver, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 0)
      }

      collectDataPoint("transitionAnimationScale") {
        safeGetString(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, "1.0")
      }
      collectDataPoint("windowAnimationScale") {
        safeGetString(resolver, Settings.Global.WINDOW_ANIMATION_SCALE, "1.0")
      }
      collectDataPoint("autoTime") { safeGetInt(resolver, Settings.Global.AUTO_TIME, 0) }
      collectDataPoint("autoTimeZone") { safeGetInt(resolver, Settings.Global.AUTO_TIME_ZONE, 0) }
      collectDataPoint("bluetoothDiscoverability") { safeGetString(resolver, "bluetooth_discoverability") }
      collectDataPoint("bluetoothDiscoverabilityTimeout") {
        safeGetString(resolver, "bluetooth_discoverability_timeout")
      }
      collectDataPoint("httpProxy") { safeGetString(resolver, Settings.Global.HTTP_PROXY) }
      collectDataPoint("networkPreference") { safeGetString(resolver, Settings.Global.NETWORK_PREFERENCE) }
      collectDataPoint("wifiNetworksAvailableNotificationOn") {
        safeGetString(resolver, Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON)
      }
      collectDataPoint("airplaneModeRadios") { safeGetString(resolver, Settings.Global.AIRPLANE_MODE_RADIOS) }
    }
  }

  override fun getCollectorName(): String = "DeviceInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true

  private fun safeGetInt(
    resolver: android.content.ContentResolver,
    key: String,
    default: Int?,
  ): Any? = runCatching { Settings.Global.getInt(resolver, key) }.getOrElse {
    if (it is SecurityException) "permission_denied" else default
  }

  private fun safeGetString(
    resolver: android.content.ContentResolver,
    key: String,
    default: String? = null,
  ): Any? = runCatching { Settings.Global.getString(resolver, key) }.getOrElse {
    if (it is SecurityException) "permission_denied" else default
  }
}
