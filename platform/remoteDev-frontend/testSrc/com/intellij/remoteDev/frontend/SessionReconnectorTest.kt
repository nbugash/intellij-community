// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.frontend

import com.intellij.remoteDev.protocol.PendingEdit
import com.intellij.remoteDev.protocol.SessionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FR-015. The client reconnects without user action, and the edits made meanwhile are not lost.
 *
 * fleet/rpc's connection loop already retries with exponential backoff. What belongs here is the
 * mapping from its connection states onto the session lifecycle, and the rule about which edits are
 * buffered.
 */
class SessionReconnectorTest {
  private fun connected(): SessionReconnector {
    val r = SessionReconnector()
    r.onTransport(TransportState.CONNECTING)
    r.onTransport(TransportState.CONNECTED)
    return r
  }

  @Test
  fun `a new reconnector is connecting`() {
    assertEquals(SessionStatus.CONNECTING, SessionReconnector().status)
  }

  @Test
  fun `the transport connecting then connected reaches connected through negotiating`() {
    // The handshake sits between them. A client that jumped straight to connected would skip the
    // version and credential checks.
    val r = SessionReconnector()
    r.onTransport(TransportState.CONNECTING)
    r.onTransport(TransportState.CONNECTED)

    assertEquals(SessionStatus.CONNECTED, r.status)
  }

  @Test
  fun `a dropped transport moves the session to temporarily disconnected`() {
    val r = connected()

    r.onTransport(TransportState.DISCONNECTED)

    assertEquals(SessionStatus.TEMPORARILY_DISCONNECTED, r.status)
  }

  @Test
  fun `a transport that comes back resumes the session`() {
    val r = connected()
    r.onTransport(TransportState.DISCONNECTED)

    r.onTransport(TransportState.CONNECTED)

    assertEquals(SessionStatus.CONNECTED, r.status)
  }

  @Test
  fun `an edit made while connected is not buffered`() {
    // It goes over the wire. Buffering it would replay it a second time on the next reconnection.
    val r = connected()

    assertEquals(false, r.record(PendingEdit("A.kt", "live")))
    assertEquals(0, r.pending.size)
  }

  @Test
  fun `an edit made while disconnected is buffered`() {
    val r = connected()
    r.onTransport(TransportState.DISCONNECTED)

    assertEquals(true, r.record(PendingEdit("A.kt", "offline")))
    assertEquals(1, r.pending.size)
  }

  @Test
  fun `reconnecting hands back the buffered edits once`() {
    val r = connected()
    r.onTransport(TransportState.DISCONNECTED)
    r.record(PendingEdit("A.kt", "one"))
    r.record(PendingEdit("A.kt", "two"))

    val replay = r.onTransport(TransportState.CONNECTED)

    assertEquals(listOf("one", "two"), replay.map { it.text })
    assertEquals(0, r.pending.size, "the buffer must be empty, or the edits replay twice")
  }

  @Test
  fun `an expiry is terminal and buffers nothing further`() {
    val r = connected()
    r.onTransport(TransportState.DISCONNECTED)
    r.onTransport(TransportState.EXPIRED)

    assertEquals(SessionStatus.EXPIRED, r.status)
    assertTrue(r.isTerminal)
    assertEquals(false, r.record(PendingEdit("A.kt", "too late")))
  }
}
