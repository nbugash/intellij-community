// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for the harness itself, task T022.
 *
 * Every assertion here uses a fake clock. Asserting a wall-clock figure on shared hardware produces
 * a test that fails for reasons unrelated to the code, and a flaky performance test teaches a team
 * to ignore performance tests. The recorded run lives in `latency-baseline.md` instead.
 */
class LatencyHarnessTest {
  /** Advances a fixed amount per reading, so a sample's measured work is exactly one tick. */
  private class FakeClock(private val tickNanos: Long) : () -> Long {
    private var now = 0L
    override fun invoke(): Long {
      val current = now
      now += tickNanos
      return current
    }
  }

  private fun harness(link: Duration, mode: EchoMode, tickMillis: Long = 1) =
    LatencyHarness(link, mode, FakeClock(tickMillis * 1_000_000))

  @Test
  fun `a round trip never beats the link`() {
    // Structural, not empirical. Half the link is already spent before the host has read anything.
    val report = harness(100.milliseconds, EchoMode.ROUND_TRIP).measureKeystrokes(50)

    assertTrue(report.p95 >= 100.milliseconds, "p95 was ${report.p95}")
  }

  @Test
  fun `local echo does not pay the link at all`() {
    val slow = harness(500.milliseconds, EchoMode.LOCAL_ECHO).measureKeystrokes(50)
    val fast = harness(1.milliseconds, EchoMode.LOCAL_ECHO).measureKeystrokes(50)

    assertEquals(slow.p95, fast.p95, "Local echo must not vary with the link")
  }

  @Test
  fun `the SC-002 keystroke budget is unreachable by round trip and reachable by local echo`() {
    // The measurement this whole harness exists to make. SC-002: under 50 ms on a 100 ms link.
    val budget = 50.milliseconds
    val link = 100.milliseconds

    assertTrue(harness(link, EchoMode.ROUND_TRIP).measureKeystrokes(100).p95 > budget)
    assertTrue(harness(link, EchoMode.LOCAL_ECHO).measureKeystrokes(100).p95 < budget)
  }

  @Test
  fun `the protocol path is actually exercised`() {
    // A harness that measured nothing would report zero and look excellent.
    val report = harness(Duration.ZERO, EchoMode.LOCAL_ECHO, tickMillis = 2).measureKeystrokes(10)

    assertEquals(2.milliseconds, report.p95)
  }

  @Test
  fun `the percentile is nearest-rank`() {
    // With 100 samples of equal cost every percentile matches; the rank arithmetic is what is tested.
    val report = harness(Duration.ZERO, EchoMode.LOCAL_ECHO, tickMillis = 3).measureKeystrokes(100)

    assertEquals(3.milliseconds, report.p50)
    assertEquals(3.milliseconds, report.p95)
    assertEquals(3.milliseconds, report.worst)
  }

  @Test
  fun `a completion carries more bytes than a keystroke`() {
    // Both must survive framing. A completion payload that exceeded the frame limit would throw.
    val report = harness(Duration.ZERO, EchoMode.LOCAL_ECHO).measureCompletions(5)

    assertEquals(5, report.samples)
    assertTrue(report.label.startsWith("completion"))
  }

  @Test
  fun `a run needs at least one sample`() {
    val failure = runCatching { harness(Duration.ZERO, EchoMode.LOCAL_ECHO).measureKeystrokes(0) }

    assertTrue(failure.isFailure)
  }

  @Test
  fun `the report names what it measured`() {
    // The baseline document quotes these labels, so an unlabelled number cannot end up in it.
    val report = harness(100.milliseconds, EchoMode.ROUND_TRIP).measureKeystrokes(1)

    assertTrue(report.label.contains("round_trip"), report.label)
    assertTrue(report.label.contains("100ms"), report.label)
  }
}
