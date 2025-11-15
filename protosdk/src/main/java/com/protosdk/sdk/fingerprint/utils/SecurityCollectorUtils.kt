package com.protosdk.sdk.fingerprint.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.Signature
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.MessageDigest

object SecurityCollectorUtils {

  fun readSystemProperty(property: String): String = try {
    val process = Runtime.getRuntime().exec("getprop $property")
    process.inputStream.bufferedReader().use { it.readText().trim() }.also { process.waitFor() }
  } catch (e: Exception) {
    ""
  }

  fun isAppInstalled(context: Context, packageName: String): Boolean = try {
    context.packageManager.getPackageInfoCompat(packageName, 0)
    true
  } catch (e: Exception) {
    false
  }

  fun canWriteToDirectory(dir: String): Boolean = try {
    val testFile = File("$dir/.root_test")
    val canWrite = testFile.createNewFile()
    if (canWrite) testFile.delete()
    canWrite
  } catch (e: Exception) {
    false
  }

  fun readProcCpuinfo(): String = try {
    val process = Runtime.getRuntime().exec("cat /proc/cpuinfo")
    process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
  } catch (e: Exception) {
    ""
  }

  fun fileExists(path: String): Boolean = File(path).exists()

  fun readNetworkInterfaces(): List<String> = try {
    val process = Runtime.getRuntime().exec("ls /sys/class/net")
    process.inputStream.bufferedReader().use { it.readLines() }.also { process.waitFor() }
  } catch (e: Exception) {
    emptyList()
  }

  fun readFile(path: String): String = try {
    File(path).takeIf { it.exists() }?.bufferedReader()?.use { it.readText() } ?: ""
  } catch (e: Exception) {
    ""
  }

  fun readProcSelfMaps(): String = readFile("/proc/self/maps")

  fun readProcSelfMounts(): String = readFile("/proc/self/mounts")

  fun readProcMounts(): String = readFile("/proc/mounts")

  fun readGlobalSetting(context: Context, key: String): Int = try {
    Settings.Global.getInt(context.contentResolver, key, 0)
  } catch (e: SecurityException) {
    -1
  }

  fun getEncryptionStatusName(status: Int): String = when (status) {
    android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_UNSUPPORTED -> "UNSUPPORTED"
    android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_INACTIVE -> "INACTIVE"
    android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVATING -> "ACTIVATING"
    android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE -> "ACTIVE"
    android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY -> "ACTIVE_DEFAULT_KEY"
    android.app.admin.DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER -> "ACTIVE_PER_USER"
    else -> "UNKNOWN($status)"
  }
}

fun PackageManager.getPackageInfoCompat(packageName: String, flags: Int): PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
  getPackageInfo(packageName, PackageInfoFlags.of(flags.toLong()))
} else {
  @Suppress("DEPRECATION")
  getPackageInfo(packageName, flags)
}

fun Signature.sha256(): String = try {
  val digest = MessageDigest.getInstance("SHA-256")
  digest.digest(toByteArray()).joinToString("") { "%02x".format(it) }
} catch (e: Exception) {
  "error:${e.message}"
}
