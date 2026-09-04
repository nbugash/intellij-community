// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.jetbrains.annotations.ApiStatus

/**
 * The moves a session may make, per contract section 4.
 *
 * Both sides need this rule, so it lives in the contract module rather than in either peer. A move
 * that the table does not list is a defect, not an undefined case.
 */
@ApiStatus.Internal
object SessionLifecycle {
  private val ALLOWED: Map<SessionStatus, Set<SessionStatus>> = mapOf(
    SessionStatus.CONNECTING to setOf(SessionStatus.NEGOTIATING, SessionStatus.REFUSED),
    SessionStatus.NEGOTIATING to setOf(SessionStatus.CONNECTED, SessionStatus.REFUSED),
    SessionStatus.CONNECTED to setOf(SessionStatus.TEMPORARILY_DISCONNECTED),
    SessionStatus.TEMPORARILY_DISCONNECTED to setOf(SessionStatus.CONNECTED, SessionStatus.EXPIRED),
    SessionStatus.REFUSED to emptySet(),
    SessionStatus.EXPIRED to emptySet(),
  )

  fun canMove(from: SessionStatus, to: SessionStatus): Boolean = ALLOWED.getValue(from).contains(to)

  fun isTerminal(status: SessionStatus): Boolean = ALLOWED.getValue(status).isEmpty()
}
