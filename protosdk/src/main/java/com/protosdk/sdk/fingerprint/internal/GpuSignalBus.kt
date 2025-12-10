package com.protosdk.sdk.fingerprint.internal

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

internal data class GpuSignals(
  val confidenceScore: Double,
  val suspectedVirtualization: Boolean,
  val suspiciousIndicators: List<String>,
  val hardwareChecksPassed: Int,
  val timestampMs: Long,
)

/**
 * Lightweight in-process bus that shares GPU virtualization hints with other collectors.
 */
internal object GpuSignalBus {
  private val cache = AtomicReference<GpuSignals?>(null)
  private const val DEFAULT_MAX_AGE_MS = 10_000L

  fun updateFromJson(payload: JSONObject) {
    val indicators = payload.optJSONArray("suspiciousIndicators").toStringList()
    val confidence = payload.optDouble("confidenceScore", 0.0)
    val suspected = payload.optBoolean("suspectedVirtualization", false)
    val hardwarePassed = payload.optInt("hardwareChecksPassed", 0)
    val snapshot = GpuSignals(
      confidenceScore = confidence,
      suspectedVirtualization = suspected,
      suspiciousIndicators = indicators,
      hardwareChecksPassed = hardwarePassed,
      timestampMs = System.currentTimeMillis(),
    )
    cache.set(snapshot)
  }

  fun latest(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): GpuSignals? {
    val snapshot = cache.get() ?: return null
    val age = System.currentTimeMillis() - snapshot.timestampMs
    return if (age <= maxAgeMs) snapshot else null
  }
}

private fun JSONArray?.toStringList(): List<String> {
  if (this == null || length() == 0) return emptyList()
  val results = ArrayList<String>(length())
  for (index in 0 until min(length(), 64)) {
    val value = optString(index)
    if (!value.isNullOrBlank()) {
      results += value
    }
  }
  return results
}
