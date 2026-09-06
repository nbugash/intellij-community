// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract section 4. The session states and the moves between them.
 *
 * The diagram in the contract is the specification. These tests are that diagram, enforced.
 */
class SessionLifecycleTest {
  @Test
  fun `the happy path runs connecting to negotiating to connected`() {
    assertTrue(SessionLifecycle.canMove(SessionStatus.CONNECTING, SessionStatus.NEGOTIATING))
    assertTrue(SessionLifecycle.canMove(SessionStatus.NEGOTIATING, SessionStatus.CONNECTED))
  }

  @Test
  fun `negotiating may end in a refusal`() {
    assertTrue(SessionLifecycle.canMove(SessionStatus.NEGOTIATING, SessionStatus.REFUSED))
  }

  @Test
  fun `a connected session may drop and come back`() {
    assertTrue(SessionLifecycle.canMove(SessionStatus.CONNECTED, SessionStatus.TEMPORARILY_DISCONNECTED))
    assertTrue(SessionLifecycle.canMove(SessionStatus.TEMPORARILY_DISCONNECTED, SessionStatus.CONNECTED))
  }

  @Test
  fun `a drop that outlasts the window expires`() {
    assertTrue(SessionLifecycle.canMove(SessionStatus.TEMPORARILY_DISCONNECTED, SessionStatus.EXPIRED))
  }

  @Test
  fun `a refusal and an expiry are terminal`() {
    assertTrue(SessionLifecycle.isTerminal(SessionStatus.REFUSED))
    assertTrue(SessionLifecycle.isTerminal(SessionStatus.EXPIRED))
    SessionStatus.entries.filterNot { it == SessionStatus.REFUSED || it == SessionStatus.EXPIRED }.forEach {
      assertFalse(SessionLifecycle.isTerminal(it), "$it should not be terminal")
    }
  }

  @Test
  fun `nothing moves out of a terminal state`() {
    SessionStatus.entries.filter(SessionLifecycle::isTerminal).forEach { terminal ->
      SessionStatus.entries.forEach { target ->
        assertFalse(SessionLifecycle.canMove(terminal, target), "$terminal moved to $target")
      }
    }
  }

  @Test
  fun `a session never reconnects straight from connecting`() {
    // A reconnection resumes an established session. Jumping there from connecting would skip the
    // handshake, and with it the version and credential checks.
    assertFalse(SessionLifecycle.canMove(SessionStatus.CONNECTING, SessionStatus.TEMPORARILY_DISCONNECTED))
    assertFalse(SessionLifecycle.canMove(SessionStatus.CONNECTING, SessionStatus.CONNECTED))
  }

  @Test
  fun `a session never refuses after it is connected`() {
    // A refusal belongs to the handshake. After that a failure ends the session another way.
    assertFalse(SessionLifecycle.canMove(SessionStatus.CONNECTED, SessionStatus.REFUSED))
  }
}
