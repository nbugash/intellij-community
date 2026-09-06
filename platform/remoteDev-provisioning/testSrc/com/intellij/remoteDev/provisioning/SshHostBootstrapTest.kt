// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * [SshHostBootstrap] against a real SSH host.
 *
 * Skipped unless an identity file is named, so a checkout without a host still builds:
 * ```
 * ./tests.cmd --module intellij.platform.remoteDev.provisioning.tests \
 *   --test com.intellij.remoteDev.provisioning.SshHostBootstrapTest \
 *   -Dpass.remotedev.e2e.ssh.identity=$HOME/.ssh/remotedev_e2e
 * ```
 * The `pass.` prefix is not decoration: `TestingTasksImpl` forwards a property to the forked test
 * JVM only when it carries that prefix.
 *
 * The host may be loopback, and here it is. Nothing under test depends on the host being elsewhere,
 * and requiring a second machine would make the suite unrunnable for everyone who lacks one.
 */
class SshHostBootstrapTest {
  @TempDir
  lateinit var localDir: Path

  private lateinit var bootstrap: SshHostBootstrap
  private lateinit var remoteDir: String

  @BeforeEach
  fun connect() {
    val identity = System.getProperty(IDENTITY_PROPERTY)
    assumeTrue(identity != null, "Set -Dpass.$IDENTITY_PROPERTY to run against a host")
    assumeTrue(Path.of(identity).exists(), "No identity file at $identity")

    bootstrap = SshHostBootstrap(
      SshTarget(host = System.getProperty(HOST_PROPERTY, "localhost"), identityFile = Path.of(identity)),
    )
    remoteDir = "/tmp/remotedev-e2e-${System.nanoTime()}"
  }

  @AfterEach
  fun cleanUp() {
    if (!::bootstrap.isInitialized) return
    runBlocking { bootstrap.execute(listOf("rm", "-rf", remoteDir)) }
    bootstrap.close()
  }

  @Test
  fun `a command runs on the host and reports success`() = runBlocking {
    assertEquals(0, bootstrap.execute(listOf("true")))
  }

  @Test
  fun `a failing command reports its own exit code`() = runBlocking {
    // A step that swallowed this would report success for a host that refused the work.
    assertEquals(42, bootstrap.execute(listOf("sh", "-c", "exit 42")))
  }

  @Test
  fun `a file reaches the host`() = runBlocking {
    val local = localDir.resolve("agent.txt")
    local.writeText(PAYLOAD)
    bootstrap.execute(listOf("mkdir", "-p", remoteDir))

    bootstrap.upload(local, remoteDir)

    assertEquals(0, bootstrap.execute(listOf("grep", "-q", PAYLOAD, "$remoteDir/agent.txt")))
  }

  @Test
  fun `an argument containing a semicolon does not run on the host`() = runBlocking {
    // SshCommandQuotingTest asserts the string. This asserts the consequence, against a real shell.
    val marker = "$remoteDir-injected"

    bootstrap.execute(listOf("echo", "harmless; touch $marker"))

    assertEquals(1, bootstrap.execute(listOf("test", "-e", marker)), "The injected command ran")
  }

  @Test
  fun `a forwarded port reaches a service on the host`() = runBlocking {
    ServerSocket(0).use { service ->
      val local = bootstrap.forwardPort(service.localPort)

      assertNotEquals(service.localPort, local, "The tunnel must not reuse the remote port number")
      Socket().use { it.connect(InetSocketAddress("127.0.0.1", local), CONNECT_TIMEOUT) }
      assertTrue(service.accept() != null, "The listener saw no connection through the tunnel")
    }
  }

  @Test
  fun `closing releases the tunnel`() = runBlocking {
    // A leaked `ssh -N` outlives the session and keeps the port.
    val local = ServerSocket(0).use { bootstrap.forwardPort(it.localPort) }

    bootstrap.close()

    assertTrue(waitUntilRefused(local), "Port $local still accepted a connection after close")
  }

  @Test
  fun `the provisioner drives a real host end to end`() = runBlocking {
    // HostProvisioner is what production calls. Until now its only host was a test double.
    //
    // The backend binary is a stub that exits 0. There is no real one to place: no product in
    // dev-build.json builds the backend. So this proves the provisioner's orchestration against a
    // real machine, and does not prove that a backend starts.
    val distribution = localDir.resolve("distribution.tar")
    distribution.writeText(PAYLOAD)
    installStubBackend()
    val seen = mutableListOf<BootstrapStep>()

    val result = ServerSocket(0).use { service ->
      val plan = BootstrapPlan(distribution, remoteDir, "/tmp/project", service.localPort)
      HostProvisioner.bootstrap(bootstrap, plan, seen::add)
    }

    assertEquals(listOf(BootstrapStep.UPLOAD, BootstrapStep.START, BootstrapStep.FORWARD), seen)
    assertNotEquals(0, result.localPort, "The provisioner returned no local port")
  }

  /** A backend that exits 0, so the START step has something real to run. */
  private suspend fun installStubBackend() {
    bootstrap.execute(listOf("mkdir", "-p", "$remoteDir/bin"))
    bootstrap.execute(listOf("sh", "-c", "printf '#!/bin/sh\\nexit 0\\n' > $remoteDir/bin/remote-backend"))
    bootstrap.execute(listOf("chmod", "+x", "$remoteDir/bin/remote-backend"))
  }

  private fun waitUntilRefused(port: Int): Boolean {
    repeat(RETRIES) {
      val open = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT) }
      }.isSuccess
      if (!open) return true
      Thread.sleep(POLL_MILLIS)
    }
    return false
  }

  private companion object {
    const val IDENTITY_PROPERTY = "remotedev.e2e.ssh.identity"
    const val HOST_PROPERTY = "remotedev.e2e.ssh.host"
    const val PAYLOAD = "agent-payload"
    const val CONNECT_TIMEOUT = 2000
    const val POLL_MILLIS = 100L
    const val RETRIES = 20
  }
}
