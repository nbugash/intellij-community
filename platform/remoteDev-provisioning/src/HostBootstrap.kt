// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.remoteDev.protocol.HostKind
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/** The steps that place a backend on a host and make it reachable. They run in this order. */
@ApiStatus.Internal
enum class BootstrapStep { UPLOAD, START, FORWARD }

/** How a host kind is reached. See research decision D10. */
@ApiStatus.Internal
enum class BootstrapRoute {
  /** The platform execution environment layer. It supports this host kind with no agent of ours. */
  EXECUTION_ENVIRONMENT_LAYER,

  /**
   * A shell session over SSH. The execution environment layer routes SSH through the IJent agent,
   * whose protocol is not in this repository, so this host kind uses a shell directly.
   */
  SHELL_OVER_SSH,
  ;

  companion object {
    fun forKind(kind: HostKind): BootstrapRoute = when (kind) {
      HostKind.SSH -> SHELL_OVER_SSH
      HostKind.WSL -> EXECUTION_ENVIRONMENT_LAYER
      HostKind.CONTAINER -> EXECUTION_ENVIRONMENT_LAYER
    }
  }
}

/** What a host must be able to do for a backend to be placed on it. */
@ApiStatus.Internal
interface HostBootstrap {
  suspend fun upload(local: Path, remotePath: String)

  /** Runs [command] on the host and returns its exit code. */
  suspend fun execute(command: List<String>): Int

  /** Forwards [remotePort] from the host and returns the local port that reaches it. */
  suspend fun forwardPort(remotePort: Int): Int
}

/** What to place on the host, and where. */
@ApiStatus.Internal
data class BootstrapPlan(
  val distribution: Path,
  val remoteDirectory: String,
  val projectPath: String,
  val backendPort: Int,
)

/** Where a bootstrapped backend can be reached. */
@ApiStatus.Internal
data class BootstrapResult(val localPort: Int)

/** A bootstrap step failed. The message names the step, as FR-010 requires. */
@ApiStatus.Internal
class BootstrapException(val step: BootstrapStep, cause: Throwable) :
  RuntimeException("The $step step failed while provisioning the host: ${cause.message}", cause)

/**
 * Places a backend on a host and returns the local port that reaches it.
 *
 * The steps run in a fixed order and stop at the first failure, so a retry never starts from a
 * half-provisioned host. FR-009 requires that, and it requires progress and cancellation, which the
 * [onProgress] callback and the cancellation check provide.
 */
@ApiStatus.Internal
object HostProvisioner {
  suspend fun bootstrap(host: HostBootstrap, plan: BootstrapPlan, onProgress: (BootstrapStep) -> Unit): BootstrapResult {
    step(BootstrapStep.UPLOAD, onProgress) { host.upload(plan.distribution, plan.remoteDirectory) }
    step(BootstrapStep.START, onProgress) { host.execute(startCommand(plan)) }
    var localPort = 0
    step(BootstrapStep.FORWARD, onProgress) { localPort = host.forwardPort(plan.backendPort) }
    return BootstrapResult(localPort)
  }

  /** Starts the split-mode backend on the host. The command name is registered in WellKnownCommand. */
  private fun startCommand(plan: BootstrapPlan): List<String> = listOf(
    "${plan.remoteDirectory}/bin/remote-backend",
    "splitBackend",
    "--project=${plan.projectPath}",
  )

  private suspend fun step(step: BootstrapStep, onProgress: (BootstrapStep) -> Unit, body: suspend () -> Unit) {
    currentCoroutineContext().ensureActive()
    onProgress(step)
    try {
      body()
    }
    catch (failure: Exception) {
      throw BootstrapException(step, failure)
    }
  }
}
