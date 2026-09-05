// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** How long each bootstrap step may take, and how long the whole first run may take. */
@ApiStatus.Internal
data class ProvisioningBudget(val total: Duration, val perStep: Map<BootstrapStep, Duration>)

/**
 * The outcome of one measured run.
 *
 * [summary] carries the measured number, not a verdict alone. Constitution Principle 5 states that a
 * success criterion with no test reporting its number is a wish, so the number travels with the
 * result.
 */
@ApiStatus.Internal
data class BudgetReport(
  val total: Duration,
  val withinBudget: Boolean,
  val overrunSteps: List<BootstrapStep>,
  val summary: String,
)

/**
 * SC-001. A developer reaches a working editor on a remote 50,000 file project in under ten minutes
 * on first use.
 *
 * This object holds the budget and judges a measured run against it. Producing the measurement needs
 * a real host and a real transport, which the quickstart guide covers. What lives here is the part
 * that can be enforced on every build: the budget adds up, a breach is detected, and an unmeasured
 * step never passes silently.
 */
@ApiStatus.Internal
object FirstRunBudget {
  /** The limit SC-001 states. */
  val SC001_TOTAL: Duration = 10.minutes

  private val UPLOAD_BUDGET: Duration = 4.minutes
  private val START_BUDGET: Duration = 5.minutes + 30.seconds
  private val FORWARD_BUDGET: Duration = 30.seconds

  /**
   * The share of the limit each step may use. The shares add to [SC001_TOTAL], and a test asserts
   * that, so raising one share without lowering another fails the build rather than SC-001.
   */
  fun forFirstRun(): ProvisioningBudget = ProvisioningBudget(
    total = SC001_TOTAL,
    perStep = mapOf(
      BootstrapStep.UPLOAD to UPLOAD_BUDGET,
      BootstrapStep.START to START_BUDGET,
      BootstrapStep.FORWARD to FORWARD_BUDGET,
    ),
  )

  /**
   * Judges [measured] against the budget.
   *
   * A step with no measurement fails the run. It has not been shown to be fast, and counting it as
   * zero would let an unmeasured run claim success.
   */
  fun evaluate(measured: Map<BootstrapStep, Duration>): BudgetReport {
    val budget = forFirstRun()
    val missing = budget.perStep.keys - measured.keys
    val total = measured.values.fold(Duration.ZERO, Duration::plus)
    val overruns = budget.perStep.filter { (step, allowed) -> (measured[step] ?: Duration.ZERO) > allowed }.keys.toList()
    val within = missing.isEmpty() && total <= budget.total
    return BudgetReport(total, within, overruns, summarise(total, budget.total, missing, overruns))
  }

  private fun summarise(
    total: Duration,
    limit: Duration,
    missing: Set<BootstrapStep>,
    overruns: List<BootstrapStep>,
  ): String {
    val head = "First run took $total against a limit of $limit."
    val missingText = if (missing.isEmpty()) "" else " Not measured: ${missing.joinToString(", ")}."
    val overrunText = if (overruns.isEmpty()) "" else " Over its own share: ${overruns.joinToString(", ")}."
    return head + missingText + overrunText
  }
}
