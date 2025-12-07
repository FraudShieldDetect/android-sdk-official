package com.protosdk.sdk.fingerprint.interfaces

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

interface FingerprintCollector {
  suspend fun collect(context: Context): JSONObject

  fun getCollectorName(): String

  fun getRequiredPermissions(): List<String>

  fun hasRequiredPermissions(context: Context): Boolean

  fun isSupported(context: Context): Boolean
}

abstract class BaseCollector : FingerprintCollector {
  protected fun safeCollect(block: () -> JSONObject): JSONObject = try {
    block()
  } catch (e: SecurityException) {
    JSONObject().apply {
      put("error", "Permission denied: ${e.message}")
      put("permissionRequired", true)
    }
  } catch (e: Exception) {
    JSONObject().apply {
      put("error", "Collection failed: ${e.message}")
      put("permissionRequired", false)
    }
  }

  protected fun JSONObject.collectDataPoint(
    key: String,
    fallback: Any? = DEFAULT_DATA_POINT_FALLBACK,
    resolver: () -> Any?,
  ) {
    try {
      val value = resolver() ?: fallback
      put(key, value ?: JSONObject.NULL)
    } catch (e: SecurityException) {
      put(key, fallback ?: DEFAULT_DATA_POINT_FALLBACK)
      if (LOG_DATA_POINT_ERRORS) {
        recordDataPointError(key, e.message, true)
      }
    } catch (e: Exception) {
      put(key, fallback ?: DEFAULT_DATA_POINT_FALLBACK)
      if (LOG_DATA_POINT_ERRORS) {
        recordDataPointError(key, e.message, false)
      }
    }
  }

  private fun JSONObject.recordDataPointError(
    key: String,
    message: String?,
    permissionRequired: Boolean,
  ) {
    val errors = optJSONArray(DATA_POINT_ERRORS) ?: JSONArray().also { put(DATA_POINT_ERRORS, it) }
    errors.put(
      JSONObject().apply {
        put("key", key)
        put("error", message ?: "unknown_error")
        put("permissionRequired", permissionRequired)
      },
    )
  }

  protected fun hasPermission(
    context: Context,
    permission: String,
  ): Boolean = context.checkSelfPermission(permission) ==
    android.content.pm.PackageManager.PERMISSION_GRANTED

  override fun hasRequiredPermissions(context: Context): Boolean = getRequiredPermissions().all { permission ->
    hasPermission(context, permission)
  }

  override fun isSupported(context: Context): Boolean = true

  private companion object {
    const val DATA_POINT_ERRORS = "dataPointErrors"
    const val DEFAULT_DATA_POINT_FALLBACK = "error"
    const val LOG_DATA_POINT_ERRORS = false
  }
}
