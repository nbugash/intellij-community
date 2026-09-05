// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/** How a keystroke reaches the user's screen. */
enum class EchoMode {
  /** The host confirms the edit before the client draws it. Costs one link round trip. */
  ROUND_TRIP,

  /** The client draws the edit at once and reconciles with the host afterwards. Costs no link time. */
  LOCAL_ECHO,
}

/** What a run measured. Percentiles, because SC-002 is stated at the 95th. */
data class LatencyReport(
  val label: String,
  val samples: Int,
  val p50: Duration,
  val p95: Duration,
  val worst: Duration,
) {
  override fun toString(): String = "$label: n=$samples p50=$p50 p95=$p95 max=$worst"
}

/**
 * Measures what the session protocol costs to move one edit, task T022.
 *
 * ### What this measures, and what it cannot
 *
 * A sample is the real work: serialize the message, write the frame, read it back, deserialize. That
 * cost is measured on a clock. The link is not real; its round trip is a number added to the
 * sample, because a test that sleeps for 100 ms a thousand times measures the scheduler.
 *
 * So the figure is protocol cost plus modelled link cost. It is not end-to-end latency. It contains
 * no editor, no host, and no rendering, and it therefore cannot be compared directly against SC-002,
 * which is stated about a user watching a screen. It is a floor: no real session beats it.
 *
 * ### Why [EchoMode] is a parameter rather than a constant
 *
 * SC-002 asks for 95% of keystrokes visible in under 50 ms on a link whose round trip is 100 ms. No
 * arrangement of a round trip satisfies that, at any protocol speed, because half the link already
 * costs more than the whole budget. The target is only reachable if the client draws the keystroke
 * before the host has seen it.
 *
 * That makes SC-002 a statement about the architecture and not about performance. Both modes are
 * here so the measurement can show the difference rather than assert it.
 *
 * The clock is injectable so a test is deterministic. A test that asserts against wall time on
 * shared hardware fails for reasons that have nothing to do with the code.
 */
class LatencyHarness(
  private val linkRoundTrip: Duration,
  private val mode: EchoMode,
  private val nanoClock: () -> Long = System::nanoTime,
) {
  private val json = Json

  fun measureKeystrokes(samples: Int): LatencyReport =
    measure("keystroke ${mode.name.lowercase()} link=$linkRoundTrip", samples, keystroke())

  fun measureCompletions(samples: Int): LatencyReport =
    measure("completion ${mode.name.lowercase()} link=$linkRoundTrip", samples, completion())

  private fun measure(label: String, samples: Int, edit: PendingEdit): LatencyReport {
    require(samples > 0) { "A run needs at least one sample" }
    val timings = (1..samples).map { oneSample(edit) }.sorted()
    return LatencyReport(label, samples, timings.percentile(50), timings.percentile(95), timings.last())
  }

  /** One edit through the real protocol path, plus the link cost this mode pays. */
  private fun oneSample(edit: PendingEdit): Duration {
    val start = nanoClock()
    val payload = json.encodeToString(PendingEdit.serializer(), edit).encodeToByteArray()
    val buffer = ByteArrayOutputStream()
    SessionFraming.writeFrame(buffer, payload)
    val frame = SessionFraming.readFrame(ByteArrayInputStream(buffer.toByteArray()))
    checkNotNull(frame) { "The frame did not survive the round trip" }
    json.decodeFromString(PendingEdit.serializer(), frame.decodeToString())
    return (nanoClock() - start).nanoseconds + linkCost()
  }

  /**
   * A round trip pays the whole link. Local echo pays none of it: the user sees the character
   * before it leaves the machine, and the host confirmation arrives later without being waited on.
   */
  private fun linkCost(): Duration = when (mode) {
    EchoMode.ROUND_TRIP -> linkRoundTrip
    EchoMode.LOCAL_ECHO -> Duration.ZERO
  }

  private fun keystroke(): PendingEdit = PendingEdit("/src/Main.kt", "a")

  /** A completion reply is a list, not a character. This is a realistic size for one. */
  private fun completion(): PendingEdit = PendingEdit("/src/Main.kt", "completionItem".repeat(600))

  private companion object {
    /** The nearest-rank percentile. With n=100, p95 is the 95th slowest, which is what SC-002 means. */
    fun List<Duration>.percentile(rank: Int): Duration {
      val index = Math.ceil(size * rank / 100.0).toInt().coerceIn(1, size)
      return this[index - 1]
    }
  }
}
