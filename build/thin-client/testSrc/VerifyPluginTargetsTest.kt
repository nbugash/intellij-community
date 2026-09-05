// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

import com.intellij.openapi.util.BuildNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** FR-056. A plugin slice must load on stock Community Edition and on this fork. */
class VerifyPluginTargetsTest {
  private val stock = BuildNumber.fromString("263.1234.56")!!
  private val fork = BuildNumber.fromString("263.1234.99")!!

  private fun target(
    since: String? = "263.1000",
    until: String? = "263.*",
    deps: List<String> = emptyList(),
  ) = PluginTarget("com.example.slice", since, until, deps)

  @Test
  fun `a plugin covering both builds passes`() {
    assertEquals(emptyList<TargetViolation>(), VerifyPluginTargets.verify(target(), stock, fork))
  }

  @Test
  fun `an open range passes`() {
    // Omitting a bound means open, which is what the platform means by leaving it out.
    assertEquals(emptyList<TargetViolation>(), VerifyPluginTargets.verify(target(since = null, until = null), stock, fork))
  }

  @Test
  fun `a range starting after both builds fails for both`() {
    val violations = VerifyPluginTargets.verify(target(since = "264.1"), stock, fork)

    assertEquals(2, violations.size)
    assertEquals(setOf("stock", "fork"), violations.filterIsInstance<TargetViolation.OutsideDeclaredRange>().map { it.target }.toSet())
  }

  @Test
  fun `a range that admits stock but stops before the fork fails for the fork only`() {
    // The case FR-056 is actually about: the plugin works where it was tested and nowhere else.
    val violations = VerifyPluginTargets.verify(target(until = "263.1234.60"), stock, fork)

    assertEquals(1, violations.size)
    assertEquals("fork", (violations.single() as TargetViolation.OutsideDeclaredRange).target)
  }

  @Test
  fun `the wildcard upper bound admits every build in the baseline`() {
    val violations = VerifyPluginTargets.verify(target(until = "263.*"), stock, fork)

    assertTrue(violations.isEmpty(), "263.* should admit 263.1234.99, got $violations")
  }

  @Test
  fun `a dependency on a fork-only module fails even when the range is fine`() {
    // The quiet failure. The range admits both builds, and the plugin still cannot load on stock.
    val violations = VerifyPluginTargets.verify(
      target(deps = listOf("intellij.platform.remoteDev.protocol")), stock, fork,
    )

    assertEquals(
      listOf(TargetViolation.ForkOnlyDependency("com.example.slice", "intellij.platform.remoteDev.protocol")),
      violations,
    )
  }

  @Test
  fun `a dependency on an ordinary platform module is fine`() {
    val violations = VerifyPluginTargets.verify(target(deps = listOf("intellij.platform.core")), stock, fork)

    assertTrue(violations.isEmpty())
  }

  @Test
  fun `both kinds of violation are reported together`() {
    // Reporting one and stopping would send someone round the loop twice.
    val violations = VerifyPluginTargets.verify(
      target(since = "264.1", deps = listOf("intellij.platform.remoteDev.backend")), stock, fork,
    )

    assertEquals(2, violations.count { it is TargetViolation.OutsideDeclaredRange })
    assertEquals(1, violations.count { it is TargetViolation.ForkOnlyDependency })
  }

  @Test
  fun `every fork module this repository adds is listed`() {
    // A module added to the fork but missing here makes the check silently weaker.
    assertEquals(5, VerifyPluginTargets.FORK_ONLY_MODULES.size)
    assertTrue(VerifyPluginTargets.FORK_ONLY_MODULES.all { it.startsWith("intellij.platform.") })
  }
}
