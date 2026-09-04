// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.frontend

import com.intellij.remoteDev.protocol.SessionLifecycle
import com.intellij.remoteDev.protocol.SessionStatus
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks where one session has got to on the client side.
 *
 * The legal moves come from [SessionLifecycle], which both peers share, so the client cannot invent
 * a state change that the host would reject. An illegal move raises rather than being ignored,
 * because a silently dropped transition would show the user a session state that is not real.
 *
 * The state is atomic because a reconnection is driven from a background thread while the user
 * interface reads the state.
 */
@ApiStatus.Internal
class FrontendSessionController {
  private val state = AtomicReference(SessionStatus.CONNECTING)

  val status: SessionStatus
    get() = state.get()

  val isTerminal: Boolean
    get() = SessionLifecycle.isTerminal(status)

  fun moveTo(next: SessionStatus) {
    val current = state.get()
    check(SessionLifecycle.canMove(current, next)) { "A session cannot move from $current to $next" }
    state.set(next)
  }
}
