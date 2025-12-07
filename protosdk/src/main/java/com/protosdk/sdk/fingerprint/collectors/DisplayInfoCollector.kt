package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.WindowMetrics
import com.protosdk.sdk.fingerprint.interfaces.BaseCollector
import org.json.JSONArray
import org.json.JSONObject

class DisplayInfoCollector : BaseCollector() {

  override suspend fun collect(context: Context): JSONObject = safeCollect {
    val configuration = context.resources.configuration
    val resourceMetrics = context.resources.displayMetrics
    val displayManager = context.getSystemService(DisplayManager::class.java)
    val windowManager = context.getSystemService(WindowManager::class.java)
    val display = resolveDisplay(context, displayManager)

    val currentWindowMetrics = getCurrentWindowMetrics(windowManager)
    val maximumWindowMetrics = getMaximumWindowMetrics(windowManager)

    JSONObject().apply {
      putResourceMetrics(resourceMetrics)
      putRealMetrics(maximumWindowMetrics, resourceMetrics)
      putDisplayDetails(display)
      putConfiguration(configuration)
    }
  }

  override fun getCollectorName(): String = "DisplayInfoCollector"

  override fun getRequiredPermissions(): List<String> = emptyList()

  override fun hasRequiredPermissions(context: Context): Boolean = true

  private fun resolveDisplay(
    context: Context,
    displayManager: DisplayManager?,
  ): Display? {
    val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      runCatching { context.display }.getOrNull() ?: defaultDisplay
    } else {
      defaultDisplay
    }
  }

  @Suppress("SwallowedException")
  private fun getCurrentWindowMetrics(windowManager: WindowManager?): WindowMetrics? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    try {
      windowManager?.currentWindowMetrics
    } catch (ignored: Exception) {
      null
    }
  } else {
    null
  }

  @Suppress("SwallowedException")
  private fun getMaximumWindowMetrics(windowManager: WindowManager?): WindowMetrics? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    try {
      windowManager?.maximumWindowMetrics
    } catch (ignored: Exception) {
      null
    }
  } else {
    null
  }

  private fun JSONObject.putResourceMetrics(metrics: DisplayMetrics) {
    collectDataPoint("widthPixels") { metrics.widthPixels }
    collectDataPoint("heightPixels") { metrics.heightPixels }
    collectDataPoint("density") { metrics.density }
    collectDataPoint("densityDpi") { metrics.densityDpi }
    collectDataPoint("xdpi") { metrics.xdpi }
    collectDataPoint("ydpi") { metrics.ydpi }
  }

  private fun JSONObject.putDisplayDetails(display: Display?) {
    display ?: return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      display.hdrCapabilities?.let { capabilities ->
        collectDataPoint("hdrMaxLuminance") { capabilities.desiredMaxLuminance }
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      collectDataPoint("isHdr") { display.isHdr }
      collectDataPoint("isWideColorGamut") { display.isWideColorGamut }
    }

    val supportedRefreshRates = JSONArray()
    display.supportedModes.map { it.refreshRate }.distinct().sorted().forEach { rate ->
      supportedRefreshRates.put(rate)
    }
    collectDataPoint("supportedRefreshRates") { supportedRefreshRates }
  }

  private fun JSONObject.putConfiguration(configuration: Configuration) {
    collectDataPoint("screenLayoutSize") { configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK }
    collectDataPoint("uiModeNight") { configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK }
    collectDataPoint("screenWidthDp") { configuration.screenWidthDp }
    collectDataPoint("screenHeightDp") { configuration.screenHeightDp }
    collectDataPoint("smallestScreenWidthDp") { configuration.smallestScreenWidthDp }
    collectDataPoint("fontScale") { configuration.fontScale }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val localesArray = JSONArray()
      for (i in 0 until configuration.locales.size()) {
        localesArray.put(configuration.locales.get(i).toString())
      }
      collectDataPoint("locales") { localesArray }
    } else {
      @Suppress("DEPRECATION")
      val localeValue = configuration.locale.toString()
      collectDataPoint("locale") { localeValue }
    }
  }

  private fun JSONObject.putRealMetrics(
    maximumWindowMetrics: WindowMetrics?,
    resourceMetrics: DisplayMetrics,
  ) {
    if (maximumWindowMetrics == null) return
    val bounds = maximumWindowMetrics.bounds
    collectDataPoint("realWidthPixels") { bounds.width() }
    collectDataPoint("realHeightPixels") { bounds.height() }
    collectDataPoint("realDensity") { resourceMetrics.density }
    collectDataPoint("realDensityDpi") { resourceMetrics.densityDpi }
  }
}
