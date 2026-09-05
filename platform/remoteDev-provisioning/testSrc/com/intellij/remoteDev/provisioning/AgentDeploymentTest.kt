// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The deployment states from data-model.md, and the rules FR-009 places on them.
 *
 * A failure at any step returns to ABSENT and leaves no partial file, so a retry starts clean.
 */
class AgentDeploymentTest {
  @Test
  fun `a deployment runs absent to uploading to verifying to ready`() {
    assertTrue(AgentDeployment.canMove(DeploymentState.ABSENT, DeploymentState.UPLOADING))
    assertTrue(AgentDeployment.canMove(DeploymentState.UPLOADING, DeploymentState.VERIFYING))
    assertTrue(AgentDeployment.canMove(DeploymentState.VERIFYING, DeploymentState.READY))
  }

  @Test
  fun `a newer agent supersedes a ready one`() {
    assertTrue(AgentDeployment.canMove(DeploymentState.READY, DeploymentState.SUPERSEDED))
  }

  @Test
  fun `a failure at any step returns to absent`() {
    // FR-009. A retry must start from a clean state, so no step fails into a half-deployed one.
    listOf(DeploymentState.UPLOADING, DeploymentState.VERIFYING).forEach { state ->
      assertTrue(AgentDeployment.canMove(state, DeploymentState.ABSENT), "$state cannot fail cleanly")
    }
  }

  @Test
  fun `an agent never skips verification`() {
    // Running an unverified binary on a host is the one move that must never be possible.
    assertFalse(AgentDeployment.canMove(DeploymentState.UPLOADING, DeploymentState.READY))
    assertFalse(AgentDeployment.canMove(DeploymentState.ABSENT, DeploymentState.READY))
  }

  @Test
  fun `a superseded agent is terminal`() {
    DeploymentState.entries.forEach { target ->
      assertFalse(AgentDeployment.canMove(DeploymentState.SUPERSEDED, target), "moved to $target")
    }
  }

  @Test
  fun `deploying the same version twice does no work`() {
    // The task calls for idempotence. A deployment that is already ready at this version is a no-op.
    val ready = AgentRecord(HostId("h1"), AgentVersion("1.2.3"), DeploymentState.READY)

    assertFalse(AgentDeployment.needsDeployment(ready, AgentVersion("1.2.3")))
  }

  @Test
  fun `a different version needs deployment`() {
    val ready = AgentRecord(HostId("h1"), AgentVersion("1.2.3"), DeploymentState.READY)

    assertTrue(AgentDeployment.needsDeployment(ready, AgentVersion("1.3.0")))
  }

  @Test
  fun `an agent that is not ready needs deployment whatever its version`() {
    listOf(DeploymentState.ABSENT, DeploymentState.UPLOADING, DeploymentState.VERIFYING, DeploymentState.SUPERSEDED)
      .forEach { state ->
        val record = AgentRecord(HostId("h1"), AgentVersion("1.2.3"), state)
        assertTrue(AgentDeployment.needsDeployment(record, AgentVersion("1.2.3")), "$state was treated as ready")
      }
  }
}
