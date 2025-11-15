package com.protosdk.sdk.fingerprint.interfaces

import android.content.Context
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

  protected fun hasPermission(
    context: Context,
    permission: String,
  ): Boolean = context.checkSelfPermission(permission) ==
    android.content.pm.PackageManager.PERMISSION_GRANTED

  override fun hasRequiredPermissions(context: Context): Boolean = getRequiredPermissions().all { permission ->
    hasPermission(context, permission)
  }

  override fun isSupported(context: Context): Boolean = true
}
