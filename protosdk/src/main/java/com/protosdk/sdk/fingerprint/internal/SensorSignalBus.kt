package com.protosdk.sdk.fingerprint.internal

import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

internal data class SensorSignals(
  val confidenceScore: Double,
  val suspectedEmulation: Boolean,
  val indicators: List<String>,
  val totalSensors: Int,
  val missingCoreCount: Int,
  val uniqueVendors: Int,
  val uniqueTypes: Int,
  val timestampMs: Long,
)

/**
 * In-process bus sharing sensor-derived emulator heuristics with the emulator detector.
 */
internal object SensorSignalBus {
  private val cache = AtomicReference<SensorSignals?>(null)
  private const val DEFAULT_MAX_AGE_MS = 10_000L

  fun publish(signals: SensorSignals) {
    cache.set(signals.copy(timestampMs = System.currentTimeMillis()))
  }

  fun latest(maxAgeMs: Long = DEFAULT_MAX_AGE_MS): SensorSignals? {
    val snapshot = cache.get() ?: return null
    val age = System.currentTimeMillis() - snapshot.timestampMs
    return if (age <= maxAgeMs) snapshot else null
  }
}

internal fun List<String>.trimTo(max: Int): List<String> = take(min(size, max)).filter { it.isNotBlank() }
