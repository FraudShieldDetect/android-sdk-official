package com.protosdk.sdk.fingerprint.collectors

import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import android.view.WindowMetrics
import com.protosdk.sdk.fingerprint.BaseCollector
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

class DisplayInfoCollector : BaseCollector() {

  override suspend fun collect(context: Context): JSONObject {
    return safeCollect {
      val configuration = context.resources.configuration
      val resourceMetrics = context.resources.displayMetrics
      val displayManager = context.getSystemService(DisplayManager::class.java)
      val windowManager = context.getSystemService(WindowManager::class.java)
      val display = resolveDisplay(context, displayManager)

      val currentWindowMetrics = getCurrentWindowMetrics(windowManager)
      val maximumWindowMetrics = getMaximumWindowMetrics(windowManager)
      val fallbackDensity = resourceMetrics.density.toDouble()

      JSONObject().apply {
        putResourceMetrics(resourceMetrics)
        putRealMetrics(maximumWindowMetrics, resourceMetrics)
        putWindowBounds("window", currentWindowMetrics, fallbackDensity)
        putWindowBounds("maximumWindow", maximumWindowMetrics, fallbackDensity)
        putSizeMetrics(currentWindowMetrics, maximumWindowMetrics, resourceMetrics)
        putDisplayDetails(display)
        putConfiguration(configuration)
      }
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
  private fun getCurrentWindowMetrics(windowManager: WindowManager?): WindowMetrics? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        windowManager?.currentWindowMetrics
      } catch (ignored: Exception) {
        null
      }
    } else {
      null
    }
  }

  @Suppress("SwallowedException")
  private fun getMaximumWindowMetrics(windowManager: WindowManager?): WindowMetrics? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      try {
        windowManager?.maximumWindowMetrics
      } catch (ignored: Exception) {
        null
      }
    } else {
      null
    }
  }

  private fun JSONObject.putResourceMetrics(metrics: DisplayMetrics) {
    put("widthPixels", metrics.widthPixels)
    put("heightPixels", metrics.heightPixels)
    put("density", metrics.density)
    put("densityDpi", metrics.densityDpi)
    put("xdpi", metrics.xdpi)
    put("ydpi", metrics.ydpi)
  }

  private fun JSONObject.putRealMetrics(
          windowMetrics: WindowMetrics?,
          fallbackMetrics: DisplayMetrics,
  ) {
    val bounds = windowMetrics?.bounds
    val density = getWindowMetricsDensity(windowMetrics) ?: fallbackMetrics.density.toDouble()
    put("realWidthPixels", bounds?.width() ?: fallbackMetrics.widthPixels)
    put("realHeightPixels", bounds?.height() ?: fallbackMetrics.heightPixels)
    put("realDensity", density)
    put("realDensityDpi", (density * DisplayMetrics.DENSITY_DEFAULT).roundToInt())
    put("realXdpi", fallbackMetrics.xdpi)
    put("realYdpi", fallbackMetrics.ydpi)
  }

  private fun JSONObject.putSizeMetrics(
          currentMetrics: WindowMetrics?,
          maximumMetrics: WindowMetrics?,
          fallbackMetrics: DisplayMetrics,
  ) {
    val currentBounds = currentMetrics?.bounds
    val maxBounds = maximumMetrics?.bounds

    put("sizeWidth", currentBounds?.width() ?: fallbackMetrics.widthPixels)
    put("sizeHeight", currentBounds?.height() ?: fallbackMetrics.heightPixels)
    put("realSizeWidth", maxBounds?.width() ?: fallbackMetrics.widthPixels)
    put("realSizeHeight", maxBounds?.height() ?: fallbackMetrics.heightPixels)
  }

  private fun JSONObject.putWindowBounds(
          prefix: String,
          windowMetrics: WindowMetrics?,
          fallbackDensity: Double,
  ) {
    if (windowMetrics == null) return
    val bounds = windowMetrics.bounds
    val density = getWindowMetricsDensity(windowMetrics) ?: fallbackDensity
    val safeDensity = if (density > 0) density else fallbackDensity
    put("${prefix}BoundsWidth", bounds.width())
    put("${prefix}BoundsHeight", bounds.height())
    put("${prefix}BoundsAreaPx", bounds.width().toLong() * bounds.height().toLong())
    put("${prefix}WidthDp", bounds.width() / safeDensity)
    put("${prefix}HeightDp", bounds.height() / safeDensity)
  }

  private fun JSONObject.putDisplayDetails(display: Display?) {
    display ?: return
    put("refreshRate", display.refreshRate)
    put("flags", display.flags)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      display.hdrCapabilities?.let { capabilities ->
        put("hdrMaxLuminance", capabilities.desiredMaxLuminance)
        put("hdrMinLuminance", capabilities.desiredMinLuminance)
        put("hdrMaxAverageLuminance", capabilities.desiredMaxAverageLuminance)
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      put("isHdr", display.isHdr)
      put("isWideColorGamut", display.isWideColorGamut)
      display.preferredWideGamutColorSpace?.let { colorSpace ->
        put("preferredWideGamutColorSpace", colorSpace.name)
      }
    }

    val supportedRefreshRates = JSONArray()
    display.supportedModes.map { it.refreshRate }.distinct().sorted().forEach { rate ->
      supportedRefreshRates.put(rate)
    }
    put("supportedRefreshRates", supportedRefreshRates)
  }

  private fun JSONObject.putConfiguration(configuration: Configuration) {
    put("screenLayout", configuration.screenLayout)
    put("screenLayoutSize", configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK)
    put("screenLayoutLong", configuration.screenLayout and Configuration.SCREENLAYOUT_LONG_MASK)
    put("screenLayoutDir", configuration.screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK)
    put("uiModeNight", configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
    put("screenWidthDp", configuration.screenWidthDp)
    put("screenHeightDp", configuration.screenHeightDp)
    put("smallestScreenWidthDp", configuration.smallestScreenWidthDp)
    put("densityDpiConfig", configuration.densityDpi)
    put("fontScale", configuration.fontScale)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      put("colorModeConfig", configuration.colorMode)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      val localesArray = JSONArray()
      for (i in 0 until configuration.locales.size()) {
        localesArray.put(configuration.locales.get(i).toString())
      }
      put("locales", localesArray)
    } else {
      @Suppress("DEPRECATION") val localeValue = configuration.locale.toString()
      put("locale", localeValue)
    }
  }

  private fun getWindowMetricsDensity(windowMetrics: WindowMetrics?): Double? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    windowMetrics != null
    ) {
      windowMetrics.density.toDouble()
    } else {
      null
    }
  }
}
