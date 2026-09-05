// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Produces the figures in `specs/001-ultimate-feature-parity/latency-baseline.md`, task T038.
 *
 * This asserts nothing about time. It runs the harness on the real clock and prints, so anyone can
 * reproduce the recorded numbers with one command and see the machine they came from. A timing
 * assertion belongs nowhere near a shared build: it fails on a loaded agent and teaches everyone to
 * rerun until green.
 *
 * Run it with:
 * ```
 * ./tests.cmd --module intellij.platform.remoteDev.protocol.tests \
 *   --test com.intellij.remoteDev.protocol.LatencyBaselineTest
 * ```
 */
class LatencyBaselineTest {
  @Test
  fun `record the baseline`() {
    val runs = buildList {
      for (link in listOf(Duration.ZERO, 100.milliseconds)) {
        for (mode in EchoMode.entries) {
          val harness = LatencyHarness(link, mode)
          add(harness.measureKeystrokes(SAMPLES))
          add(harness.measureCompletions(SAMPLES))
        }
      }
    }

    println("=== T038 latency baseline, $SAMPLES samples per row ===")
    println("java=${System.getProperty("java.version")} os=${System.getProperty("os.name")}")
    runs.forEach { println(it) }

    assertEquals(8, runs.size, "Two link settings times two modes times two message kinds")
  }

  private companion object {
    /** Enough that a 95th percentile means something, small enough to stay quick. */
    const val SAMPLES = 1000
  }
}
