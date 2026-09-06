// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * SC-001: a developer reaches a working editor on a remote 50,000 file project in under ten minutes
 * on first use.
 *
 * These tests cover the budget and the report. The end-to-end number needs a real host and a real
 * transport, and the quickstart guide holds that check. What is enforced here is that the budget
 * adds up to the criterion, that a breach is detected, and that the report states the measured
 * number rather than a verdict alone.
 */
class FirstRunTimingTest {
  @Test
  fun `the step budgets add up to the criterion`() {
    // If a step budget is raised without lowering another, SC-001 quietly stops being met. This
    // test is what stops that.
    val budget = FirstRunBudget.forFirstRun()

    assertEquals(FirstRunBudget.SC001_TOTAL, budget.perStep.values.reduce { a, b -> a + b })
    assertEquals(10.minutes, FirstRunBudget.SC001_TOTAL)
  }

  @Test
  fun `every bootstrap step has a budget`() {
    assertEquals(BootstrapStep.entries.toSet(), FirstRunBudget.forFirstRun().perStep.keys)
  }

  @Test
  fun `a run inside the budget passes and states its number`() {
    val measured = mapOf(
      BootstrapStep.UPLOAD to 2.minutes,
      BootstrapStep.START to 3.minutes,
      BootstrapStep.FORWARD to 5.seconds,
    )

    val report = FirstRunBudget.evaluate(measured)

    assertTrue(report.withinBudget)
    assertEquals(5.minutes + 5.seconds, report.total)
    assertTrue(report.summary.contains("5m"), "the report hides its number: ${report.summary}")
  }

  @Test
  fun `a run over the total fails`() {
    val measured = mapOf(
      BootstrapStep.UPLOAD to 6.minutes,
      BootstrapStep.START to 5.minutes,
      BootstrapStep.FORWARD to 1.seconds,
    )

    val report = FirstRunBudget.evaluate(measured)

    assertFalse(report.withinBudget)
    assertTrue(report.summary.contains("10m"), "the report omits the limit: ${report.summary}")
  }

  @Test
  fun `a step over its own budget is named even when the total passes`() {
    // An early warning. The total can pass while one step is heading the wrong way.
    val measured = mapOf(
      BootstrapStep.UPLOAD to 9.minutes,
      BootstrapStep.START to 10.seconds,
      BootstrapStep.FORWARD to 1.seconds,
    )

    val report = FirstRunBudget.evaluate(measured)

    assertTrue(report.withinBudget, "the total should still pass")
    assertTrue(report.overrunSteps.contains(BootstrapStep.UPLOAD), "overruns: ${report.overrunSteps}")
  }

  @Test
  fun `a missing measurement is treated as a failure, not as zero`() {
    // A step that reported nothing has not been shown to be fast. Counting it as zero would let an
    // unmeasured run claim success.
    val report = FirstRunBudget.evaluate(mapOf(BootstrapStep.UPLOAD to 1.minutes))

    assertFalse(report.withinBudget)
    assertTrue(report.summary.contains("not measured", ignoreCase = true), "summary: ${report.summary}")
  }
}
