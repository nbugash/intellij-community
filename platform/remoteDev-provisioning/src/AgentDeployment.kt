// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import org.jetbrains.annotations.ApiStatus

/** The version of the agent binary that a host runs. */
@ApiStatus.Internal
@JvmInline
value class AgentVersion(val value: String) {
  init {
    require(value.isNotBlank()) { "An agent version must not be blank" }
  }
}

/** The deployment states from data-model.md. */
@ApiStatus.Internal
enum class DeploymentState { ABSENT, UPLOADING, VERIFYING, READY, SUPERSEDED }

/** What this client believes about the agent on one host. */
@ApiStatus.Internal
data class AgentRecord(val host: HostId, val version: AgentVersion, val state: DeploymentState)

/**
 * The rules that govern deploying the agent to a host.
 *
 * Two of them carry the weight.
 *
 * A deployment never moves from UPLOADING straight to READY. Verification sits between them, so a
 * binary that arrived truncated or altered is never executed on a host.
 *
 * A failure returns to ABSENT rather than resting in a half-deployed state. FR-009 requires an
 * operation to leave no partial state that prevents a retry.
 */
@ApiStatus.Internal
object AgentDeployment {
  private val ALLOWED: Map<DeploymentState, Set<DeploymentState>> = mapOf(
    DeploymentState.ABSENT to setOf(DeploymentState.UPLOADING),
    DeploymentState.UPLOADING to setOf(DeploymentState.VERIFYING, DeploymentState.ABSENT),
    DeploymentState.VERIFYING to setOf(DeploymentState.READY, DeploymentState.ABSENT),
    DeploymentState.READY to setOf(DeploymentState.SUPERSEDED),
    DeploymentState.SUPERSEDED to emptySet(),
  )

  fun canMove(from: DeploymentState, to: DeploymentState): Boolean = ALLOWED.getValue(from).contains(to)

  /**
   * Deployment is idempotent. A host that already runs the wanted version needs no work, which keeps
   * a reconnection cheap.
   */
  fun needsDeployment(current: AgentRecord?, wanted: AgentVersion): Boolean =
    current == null || current.state != DeploymentState.READY || current.version != wanted
}
