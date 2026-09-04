// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.frontend

import com.intellij.remoteDev.protocol.SessionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FrontendSessionControllerTest {
  @Test
  fun `a new controller starts connecting`() {
    assertEquals(SessionStatus.CONNECTING, FrontendSessionController().status)
  }

  @Test
  fun `the happy path is allowed`() {
    val controller = FrontendSessionController()

    controller.moveTo(SessionStatus.NEGOTIATING)
    controller.moveTo(SessionStatus.CONNECTED)

    assertEquals(SessionStatus.CONNECTED, controller.status)
  }

  @Test
  fun `a reconnection returns to connected`() {
    val controller = connected()

    controller.moveTo(SessionStatus.TEMPORARILY_DISCONNECTED)
    controller.moveTo(SessionStatus.CONNECTED)

    assertEquals(SessionStatus.CONNECTED, controller.status)
  }

  @Test
  fun `an illegal move is refused and leaves the state alone`() {
    val controller = FrontendSessionController()

    assertThrows(IllegalStateException::class.java) { controller.moveTo(SessionStatus.CONNECTED) }
    assertEquals(SessionStatus.CONNECTING, controller.status)
  }

  @Test
  fun `an expired session is terminal and moves no further`() {
    val controller = connected()
    controller.moveTo(SessionStatus.TEMPORARILY_DISCONNECTED)
    controller.moveTo(SessionStatus.EXPIRED)

    assertTrue(controller.isTerminal)
    assertThrows(IllegalStateException::class.java) { controller.moveTo(SessionStatus.CONNECTING) }
  }

  private fun connected(): FrontendSessionController = FrontendSessionController().apply {
    moveTo(SessionStatus.NEGOTIATING)
    moveTo(SessionStatus.CONNECTED)
  }
}
