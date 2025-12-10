package com.protosdk.sdk.fingerprint.collectors

import android.media.MediaDrm
import android.os.Build
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

private const val WIDEVINE_UUID_MSB = -0x121074568629b532L
private const val WIDEVINE_UUID_LSB = -0x5c37d8232ae2de13L

class MediaDrmCollector : BaseCollector() {
  override suspend fun collect(context: android.content.Context): JSONObject = safeCollect {
    JSONObject().apply {
      collectDataPoint("id") {
        val widevineUuid = UUID(WIDEVINE_UUID_MSB, WIDEVINE_UUID_LSB)
        val drm = MediaDrm(widevineUuid)

        val uniqueIdBytes =
          try {
            drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
          } finally {
            releaseDrm(drm)
          }

        val sha256 = MessageDigest.getInstance("SHA-256").digest(uniqueIdBytes)
        sha256.toHexString()
      }
    }
  }

  private fun releaseDrm(mediaDrm: MediaDrm) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      mediaDrm.close()
    } else {
      @Suppress("DEPRECATION")
      mediaDrm.release()
    }
  }

  override fun getCollectorName(): String = "MediaDrmCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun isSupported(context: android.content.Context): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
}

private fun ByteArray.toHexString(): String = joinToString("") { b -> "%02x".format(b) }
