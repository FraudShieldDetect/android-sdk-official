package com.protosdk.sdk.fingerprint.collectors

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import com.protosdk.sdk.fingerprint.internal.GpuSignalBus
import com.protosdk.sdk.fingerprint.internal.GpuStringDecoder
import com.protosdk.sdk.fingerprint.nativebridge.GpuDetectionBridge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.random.Random

class GpuInfoCollector : BaseCollector() {
  private val gpuDispatcher: CoroutineDispatcher =
    Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "ProtoGpuCollector").apply { isDaemon = true }
    }
      .asCoroutineDispatcher()

  private val cachingEnabled = false

  @Volatile private var cachedResult: JSONObject? = null
  private val cacheLock = Any()
  private val random = Random(System.nanoTime())

  override fun getCollectorName(): String = "GpuInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun isSupported(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_OPENGLES_EXTENSION_PACK) ||
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

  override suspend fun collect(context: Context): JSONObject = withContext(gpuDispatcher) {
    if (cachingEnabled) {
      cachedResult?.let {
        return@withContext it
      }
    }

    val result =
      try {
        withTimeout(200L) { safeCollect { buildPayload(context) } }
      } catch (_: TimeoutCancellationException) {
        buildErrorJson("GPU fingerprinting timed out after 200ms")
      }

    if (cachingEnabled && !result.has("error")) {
      synchronized(cacheLock) { cachedResult = result }
    }

    result
  }

  private fun buildPayload(context: Context): JSONObject {
    val snapshot = collectSnapshot()
    val extensions = snapshot.extensions.distinct()
    val virtualizationIndicators = mutableListOf<String>()

    val rendererMatch =
      GpuStringDecoder.matchPattern(snapshot.renderer, GpuStringDecoder.rendererPatterns())
    if (rendererMatch != null) {
      virtualizationIndicators += "renderer_pattern:${rendererMatch.value}"
    }

    val vendorMatch =
      GpuStringDecoder.matchPattern(snapshot.vendor, GpuStringDecoder.vendorPatterns())
    if (vendorMatch != null) {
      virtualizationIndicators += "vendor_pattern:${vendorMatch.value}"
    }

    val eglMatch =
      GpuStringDecoder.matchPattern(snapshot.eglVendor, GpuStringDecoder.eglVendorPatterns())
    if (eglMatch != null) {
      virtualizationIndicators += "egl_vendor_pattern:${eglMatch.value}"
    }

    val extensionMatches =
      extensions.mapNotNull { ext ->
        val match = GpuStringDecoder.matchPattern(ext, GpuStringDecoder.extensionPatterns())
        match?.let { "extension_pattern:${it.value}" }
      }
    virtualizationIndicators += extensionMatches

    val rendererLower = snapshot.renderer.lowercase()
    val softwareTokens =
      listOf("swiftshader", "llvmpipe", "mesa", "software", "angle", "virtualbox")
    if (softwareTokens.any { rendererLower.contains(it) }) {
      virtualizationIndicators += "software_renderer_signature"
    }

    val extensionOutlier =
      extensions.firstOrNull { entry ->
        entry.contains("GL_ARB", ignoreCase = true) ||
          entry.contains("GL_EXT_texture_filter_anisotropic_desktop", ignoreCase = true)
      }
    if (extensionOutlier != null) {
      virtualizationIndicators += "desktop_extension:$extensionOutlier"
    }

    val systemRamMb = getTotalRamMb(context)

    val apiLevel = Build.VERSION.SDK_INT
    if (!snapshot.vulkanSupported && apiLevel >= Build.VERSION_CODES.Q) {
      virtualizationIndicators += "missing_vulkan_support"
    }

    if (snapshot.computeInvocations == 0 && apiLevel >= Build.VERSION_CODES.R) {
      virtualizationIndicators += "missing_compute_support"
    }

    if (snapshot.microBenchmarkMs > 8.0) {
      virtualizationIndicators += "slow_gl_micro_benchmark"
    }

    if (snapshot.maxTextureSize >= 16384 && systemRamMb in 1..4096) {
      virtualizationIndicators += "max_texture_outlier:${snapshot.maxTextureSize}"
    }

    if (snapshot.renderer.isBlank()) {
      virtualizationIndicators += "renderer_missing"
    }

    if (snapshot.vendor.contains("google", ignoreCase = true) &&
      !Build.MANUFACTURER.contains("google", ignoreCase = true)
    ) {
      virtualizationIndicators += "vendor_mismatch_google"
    }

    val indicatorSet = virtualizationIndicators.filter { it.isNotBlank() }.toSet()
    val highConfidence =
      indicatorSet.count { indicator ->
        indicator.startsWith("renderer_pattern") ||
          indicator.startsWith("vendor_pattern") ||
          indicator.startsWith("egl_vendor_pattern") ||
          indicator.startsWith("software_renderer")
      }

    var confidenceScore = highConfidence * 0.3 + indicatorSet.size * 0.15
    if (!snapshot.vulkanSupported && apiLevel >= Build.VERSION_CODES.Q) {
      confidenceScore += 0.1
    }
    if (snapshot.computeInvocations == 0 && apiLevel >= Build.VERSION_CODES.R) {
      confidenceScore += 0.1
    }
    if (extensionOutlier != null) {
      confidenceScore += 0.1
    }
    if (snapshot.microBenchmarkMs > 8.0) {
      confidenceScore += 0.1
    }
    confidenceScore = min(1.0, confidenceScore)
    val suspectedVirtualization = confidenceScore >= 0.65

    var hardwareChecksPassed = 0
    if (snapshot.renderer.isNotBlank()) hardwareChecksPassed += 1
    if (snapshot.vendor.isNotBlank()) hardwareChecksPassed += 1
    if (extensions.isNotEmpty()) hardwareChecksPassed += 1
    if (snapshot.maxTextureSize > 0) hardwareChecksPassed += 1
    if (snapshot.computeInvocations > 0) hardwareChecksPassed += 1
    if (snapshot.vulkanSupported) hardwareChecksPassed += 1

    val extensionJson = JSONArray().apply { extensions.take(15).forEach { put(it) } }

    val payload =
      JSONObject().apply {
        put("renderer", snapshot.renderer)
        put("vendor", snapshot.vendor)
        put("version", snapshot.version)
        put("eglVendor", snapshot.eglVendor)
        put("extensions", extensionJson)
        put("maxTextureSize", snapshot.maxTextureSize)
        put("computeSupported", snapshot.computeInvocations > 0)
        put("computeWorkGroupInvocations", snapshot.computeInvocations)
        put("systemRamMb", systemRamMb)
        put("vulkanSupported", snapshot.vulkanSupported)
        put("hardwareChecksPassed", hardwareChecksPassed)
        put("suspiciousIndicators", JSONArray().apply { indicatorSet.forEach { put(it) } })
        put("confidenceScore", confidenceScore)
        put("suspectedVirtualization", suspectedVirtualization)
      }
    GpuSignalBus.updateFromJson(payload)
    return payload
  }

  private fun collectSnapshot(): Snapshot {
    val snapshot = Snapshot()
    val operations =
      listOf<(Snapshot) -> Unit>(
        { it.renderer = GpuDetectionBridge.nativeGetGpuRenderer() },
        { it.vendor = GpuDetectionBridge.nativeGetGpuVendor() },
        { it.version = GpuDetectionBridge.nativeGetGpuVersion() },
        {
          it.extensions =
            GpuDetectionBridge.nativeGetGpuExtensions().filter { value ->
              value.isNotBlank()
            }
        },
        { it.eglVendor = GpuDetectionBridge.nativeGetEglVendor() },
        { it.eglConfig = GpuDetectionBridge.nativeGetEglConfig() },
        { it.maxTextureSize = GpuDetectionBridge.nativeGetMaxTextureSize() },
        {
          it.computeInvocations =
            GpuDetectionBridge.nativeGetComputeWorkGroupInvocations()
        },
        { it.vulkanSupported = GpuDetectionBridge.nativeCheckVulkan() },
      )
        .shuffled(random)

    operations.forEach { operation ->
      try {
        operation(snapshot)
      } catch (_: Throwable) {
        // Ignore individual check failures to maximize coverage.
      }
    }
    return snapshot
  }

  private fun getTotalRamMb(context: Context): Int {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    val info = ActivityManager.MemoryInfo()
    return if (activityManager != null) {
      activityManager.getMemoryInfo(info)
      (info.totalMem / (1024 * 1024)).toInt()
    } else {
      0
    }
  }

  private fun buildErrorJson(message: String): JSONObject = JSONObject().apply {
    put("error", message)
    put("permissionRequired", false)
  }

  private data class Snapshot(
    var renderer: String = "",
    var vendor: String = "",
    var version: String = "",
    var extensions: List<String> = emptyList(),
    var eglVendor: String = "",
    var eglConfig: IntArray = intArrayOf(),
    var maxTextureSize: Int = 0,
    var computeInvocations: Int = 0,
    var microBenchmarkMs: Double = 0.0,
    var vulkanSupported: Boolean = false,
  )
}
