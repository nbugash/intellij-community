// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.remoteDev.protocol.SessionFailure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * FR-007 and SC-015. No project-supplied code runs before the user grants trust.
 */
class BackendTrustGateTest {
  @Test
  fun `an untrusted project refuses every operation that runs project code`() {
    BackendOperation.entries.filter { it.runsProjectCode }.forEach { operation ->
      assertEquals(
        SessionFailure.TRUST_REQUIRED,
        BackendTrustGate.refusalFor(operation, trusted = false),
        "$operation ran without trust",
      )
    }
  }

  @Test
  fun `an untrusted project still allows every operation that runs no project code`() {
    // Reading and navigating must work before trust. Otherwise a user cannot inspect a project to
    // decide whether to trust it.
    BackendOperation.entries.filterNot { it.runsProjectCode }.forEach { operation ->
      assertNull(BackendTrustGate.refusalFor(operation, trusted = false), "$operation was refused")
    }
  }

  @Test
  fun `a trusted project allows every operation`() {
    BackendOperation.entries.forEach { operation ->
      assertNull(BackendTrustGate.refusalFor(operation, trusted = true), "$operation was refused")
    }
  }

  @Test
  fun `opening a build script counts as running project code`() {
    // The build import path is the one that actually executes a script, so it must need trust.
    assertEquals(true, BackendOperation.IMPORT_BUILD_SCRIPT.runsProjectCode)
    assertEquals(true, BackendOperation.RUN_CONFIGURATION.runsProjectCode)
    assertEquals(false, BackendOperation.READ_FILE.runsProjectCode)
  }
}
