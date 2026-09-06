// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.remoteDev.protocol.HostKind
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The bootstrap contract from research decision D10.
 *
 * The steps run in a fixed order, and each one is cancellable and reports progress, because FR-009
 * requires both and because provisioning a 50,000 file project is slow enough that a user will want
 * to stop it.
 */
class HostBootstrapTest {
  private class RecordingHost(
    private val failAt: BootstrapStep? = null,
  ) : HostBootstrap {
    val steps = mutableListOf<BootstrapStep>()
    var uploadedTo: String? = null
    var startedCommand: List<String>? = null
    var forwarded: Int? = null

    override suspend fun upload(local: Path, remotePath: String) {
      steps += BootstrapStep.UPLOAD
      if (failAt == BootstrapStep.UPLOAD) error("upload failed")
      uploadedTo = remotePath
    }

    override suspend fun execute(command: List<String>): Int {
      steps += BootstrapStep.START
      if (failAt == BootstrapStep.START) error("start failed")
      startedCommand = command
      return 0
    }

    override suspend fun forwardPort(remotePort: Int): Int {
      steps += BootstrapStep.FORWARD
      if (failAt == BootstrapStep.FORWARD) error("forward failed")
      forwarded = remotePort
      return 15990
    }
  }

  private val plan = BootstrapPlan(
    distribution = Path.of("/local/backend.tar.gz"),
    remoteDirectory = "/srv/backends/alpha",
    projectPath = "/srv/project",
    backendPort = 5990,
  )

  @Test
  fun `the steps run upload then start then forward`() {
    val host = RecordingHost()

    runBlocking { HostProvisioner.bootstrap(host, plan) {} }

    assertEquals(listOf(BootstrapStep.UPLOAD, BootstrapStep.START, BootstrapStep.FORWARD), host.steps)
  }

  @Test
  fun `the backend is started with the project it must serve`() {
    val host = RecordingHost()

    runBlocking { HostProvisioner.bootstrap(host, plan) {} }

    assertTrue(host.startedCommand!!.any { it.contains("/srv/project") }, "command: ${host.startedCommand}")
    assertTrue(host.startedCommand!!.contains("splitBackend"), "command: ${host.startedCommand}")
  }

  @Test
  fun `the local port is reported back`() {
    val host = RecordingHost()

    val result = runBlocking { HostProvisioner.bootstrap(host, plan) {} }

    assertEquals(15990, result.localPort)
  }

  @Test
  fun `progress is reported for every step`() {
    // FR-009. A long operation reports progress.
    val seen = mutableListOf<BootstrapStep>()
    runBlocking { HostProvisioner.bootstrap(RecordingHost(), plan) { seen += it } }

    assertEquals(BootstrapStep.entries.toList(), seen)
  }

  @Test
  fun `a failure names the step that failed`() {
    // FR-010. A failure states which operation failed, so the user knows what to retry.
    val failure = assertThrows(BootstrapException::class.java) {
      runBlocking { HostProvisioner.bootstrap(RecordingHost(failAt = BootstrapStep.START), plan) {} }
    }

    assertTrue(failure.message!!.contains("START"), "message: ${failure.message}")
  }

  @Test
  fun `a failure stops the remaining steps`() {
    // FR-009. No partial state that a retry would trip over: nothing runs after a failure.
    val host = RecordingHost(failAt = BootstrapStep.UPLOAD)

    assertThrows(BootstrapException::class.java) { runBlocking { HostProvisioner.bootstrap(host, plan) {} } }

    assertEquals(listOf(BootstrapStep.UPLOAD), host.steps)
    assertFalse(host.steps.contains(BootstrapStep.START))
  }

  @Test
  fun `every host kind FR-013 names has a bootstrap route`() {
    val routed = HostKind.entries.associateWith(BootstrapRoute::forKind)

    assertEquals(HostKind.entries.size, routed.size)
    assertTrue(routed.values.all { it in BootstrapRoute.entries }, "routes: $routed")
  }

  @Test
  fun `the SSH route uses a shell, not the execution environment layer`() {
    // D10. The layer's SSH support routes through IJent, which this fork cannot use.
    assertEquals(BootstrapRoute.SHELL_OVER_SSH, BootstrapRoute.forKind(HostKind.SSH))
    assertEquals(BootstrapRoute.EXECUTION_ENVIRONMENT_LAYER, BootstrapRoute.forKind(HostKind.WSL))
    assertEquals(BootstrapRoute.EXECUTION_ENVIRONMENT_LAYER, BootstrapRoute.forKind(HostKind.CONTAINER))
  }
}
