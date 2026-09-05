// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.remoteDev.protocol.SessionId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FR-016. A port is forwarded on request, and a port a launched process opens is offered.
 *
 * The tunnel itself belongs to the platform. What is tested here is who may open one, and that none
 * outlives its session.
 */
class PortForwardingTest {
  private class FakeTunnels : Tunnels {
    val open = mutableSetOf<Int>()
    private var next = 20000

    override suspend fun open(hostPort: Int): Int {
      next++
      open += next
      return next
    }

    override suspend fun close(localPort: Int) {
      open -= localPort
    }
  }

  private val session = SessionId("s-1")
  private val other = SessionId("s-2")

  private fun forwarder(consent: Boolean = true, tunnels: Tunnels = FakeTunnels()) =
    PortForwarder(tunnels) { consent }

  @Test
  fun `a port the user asks for is forwarded`() {
    val forwarder = forwarder()

    val port = runBlocking { forwarder.forward(session, hostPort = 8080, origin = PortOrigin.USER) }

    assertNotNull(port)
    assertEquals(8080, port!!.hostPort)
  }

  @Test
  fun `a port the user asks for needs no consent`() {
    // Asking is the consent. Prompting again would be noise.
    val forwarder = forwarder(consent = false)

    assertNotNull(runBlocking { forwarder.forward(session, 8080, PortOrigin.USER) })
  }

  @Test
  fun `a detected port is not forwarded without consent`() {
    // FR-016. A port a process opened is offered, not taken.
    val tunnels = FakeTunnels()
    val forwarder = forwarder(consent = false, tunnels = tunnels)

    assertNull(runBlocking { forwarder.forward(session, 3000, PortOrigin.DETECTED) })
    assertTrue(tunnels.open.isEmpty(), "a tunnel was opened without consent")
  }

  @Test
  fun `a detected port is forwarded once consent is given`() {
    val forwarder = forwarder(consent = true)

    assertNotNull(runBlocking { forwarder.forward(session, 3000, PortOrigin.DETECTED) })
  }

  @Test
  fun `forwarding the same host port twice reuses the tunnel`() {
    val tunnels = FakeTunnels()
    val forwarder = forwarder(tunnels = tunnels)

    val first = runBlocking { forwarder.forward(session, 8080, PortOrigin.USER) }
    val second = runBlocking { forwarder.forward(session, 8080, PortOrigin.USER) }

    assertEquals(first, second)
    assertEquals(1, tunnels.open.size, "a second tunnel was opened for the same port")
  }

  @Test
  fun `closing a session closes every tunnel it opened`() {
    // T088. A tunnel that outlives its session is a leak, and it leaves a way into the host open.
    val tunnels = FakeTunnels()
    val forwarder = forwarder(tunnels = tunnels)
    runBlocking {
      forwarder.forward(session, 8080, PortOrigin.USER)
      forwarder.forward(session, 9090, PortOrigin.USER)
      forwarder.closeSession(session)
    }

    assertTrue(tunnels.open.isEmpty(), "tunnels left open: ${tunnels.open}")
    assertTrue(forwarder.active(session).isEmpty())
  }

  @Test
  fun `closing one session leaves another session's tunnels alone`() {
    val tunnels = FakeTunnels()
    val forwarder = forwarder(tunnels = tunnels)
    runBlocking {
      forwarder.forward(session, 8080, PortOrigin.USER)
      forwarder.forward(other, 8080, PortOrigin.USER)
      forwarder.closeSession(session)
    }

    assertEquals(1, tunnels.open.size)
    assertEquals(1, forwarder.active(other).size)
  }

  @Test
  fun `two sessions may forward the same host port independently`() {
    // FR-019 allows several backends on one host. They must not collide on a port.
    val forwarder = forwarder()
    val a = runBlocking { forwarder.forward(session, 8080, PortOrigin.USER) }
    val b = runBlocking { forwarder.forward(other, 8080, PortOrigin.USER) }

    assertFalse(a!!.localPort == b!!.localPort, "two sessions shared a local port")
  }
}
