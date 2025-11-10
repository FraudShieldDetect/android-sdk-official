package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import android.provider.Settings
import com.protosdk.sdk.fingerprint.BaseCollector
import org.json.JSONObject

class DeviceInfoCollector : BaseCollector() {
  override suspend fun collect(context: Context): JSONObject = safeCollect {
    JSONObject().apply {
      val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
      put("androidId", androidId ?: "unknown")

      put(
        "developmentSettingsEnabled",
        Settings.Global.getInt(
          context.contentResolver,
          Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
          0,
        ),
      )

      put(
        "waitForDebugger",
        Settings.Global.getInt(
          context.contentResolver,
          Settings.Global.WAIT_FOR_DEBUGGER,
          0,
        ),
      )

      put(
        "airplaneModeOn",
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0),
      )

      put(
        "bootCount",
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0),
      )

      put(
        "deviceProvisioned",
        Settings.Global.getInt(
          context.contentResolver,
          Settings.Global.DEVICE_PROVISIONED,
          0,
        ),
      )

      put(
        "dataRoaming",
        Settings.Global.getInt(context.contentResolver, Settings.Global.DATA_ROAMING, 0),
      )

      put(
        "stayOnWhilePluggedIn",
        Settings.Global.getInt(
          context.contentResolver,
          Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
          0,
        ),
      )

      put(
        "animatorDurationScale",
        Settings.Global.getString(
          context.contentResolver,
          Settings.Global.ANIMATOR_DURATION_SCALE,
        )
          ?: "1.0",
      )

      put(
        "transitionAnimationScale",
        Settings.Global.getString(
          context.contentResolver,
          Settings.Global.TRANSITION_ANIMATION_SCALE,
        )
          ?: "1.0",
      )

      put(
        "windowAnimationScale",
        Settings.Global.getString(
          context.contentResolver,
          Settings.Global.WINDOW_ANIMATION_SCALE,
        )
          ?: "1.0",
      )

      put(
        "autoTime",
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME, 0),
      )

      put(
        "autoTimeZone",
        Settings.Global.getInt(context.contentResolver, Settings.Global.AUTO_TIME_ZONE, 0),
      )

      put(
        "bluetoothOn",
        Settings.Global.getInt(context.contentResolver, Settings.Global.BLUETOOTH_ON, 0),
      )

      put(
        "bluetoothDiscoverability",
        Settings.Global.getString(context.contentResolver, "bluetooth_discoverability"),
      )

      put(
        "bluetoothDiscoverabilityTimeout",
        Settings.Global.getString(
          context.contentResolver,
          "bluetooth_discoverability_timeout",
        ),
      )

      put(
        "httpProxy",
        Settings.Global.getString(context.contentResolver, Settings.Global.HTTP_PROXY),
      )

      put(
        "networkPreference",
        Settings.Global.getString(
          context.contentResolver,
          Settings.Global.NETWORK_PREFERENCE,
        ),
      )

      put(
        "usbMassStorageEnabled",
        Settings.Global.getString(
          context.contentResolver,
          Settings.Global.USB_MASS_STORAGE_ENABLED,
        ),
      )

      put(
        "wifiNetworksAvailableNotificationOn",
        Settings.Global.getString(
          context.contentResolver,
          Settings.Global.WIFI_NETWORKS_AVAILABLE_NOTIFICATION_ON,
        ),
      )

      put(
        "alwaysFinishActivities",
        Settings.Global.getInt(
          context.contentResolver,
          Settings.Global.ALWAYS_FINISH_ACTIVITIES,
          0,
        ),
      )

      put(
        "modeRinger",
        Settings.Global.getString(context.contentResolver, Settings.Global.MODE_RINGER),
      )

      put(
        "airplaneModeRadios",
        Settings.Global.getString(
          context.contentResolver,
          Settings.Global.AIRPLANE_MODE_RADIOS,
        ),
      )
    }
  }

  override fun getCollectorName(): String = "DeviceInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true
}
