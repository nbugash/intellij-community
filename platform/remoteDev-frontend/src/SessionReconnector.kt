// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.frontend

import com.intellij.remoteDev.protocol.PendingEdit
import com.intellij.remoteDev.protocol.PendingEditLog
import com.intellij.remoteDev.protocol.SessionStatus
import org.jetbrains.annotations.ApiStatus

/**
 * What the transport is doing, as this client cares about it.
 *
 * fleet/rpc reports a richer `ConnectionStatus`, which also carries the retry instant, the delay job
 * and the failure. This enum is the part the session lifecycle reacts to, so the lifecycle does not
 * depend on the transport's own type.
 */
@ApiStatus.Internal
enum class TransportState { CONNECTING, CONNECTED, DISCONNECTED, EXPIRED }

/**
 * Drives the session lifecycle from the transport, and holds the edits made while it is down.
 *
 * fleet/rpc's connection loop already retries with exponential backoff, so reconnecting is not this
 * class's job. Its job is FR-015's other half: the session keeps its identity across the outage, and
 * the edits made meanwhile are replayed rather than lost.
 */
@ApiStatus.Internal
class SessionReconnector {
  private val controller = FrontendSessionController()

  /** The edits made while the transport is down. */
  val pending: PendingEditLog = PendingEditLog()

  val status: SessionStatus get() = controller.status

  val isTerminal: Boolean get() = controller.isTerminal

  /**
   * Applies a transport change and returns the edits to replay, which is empty unless this call
   * restored a dropped connection.
   */
  fun onTransport(state: TransportState): List<PendingEdit> {
    when (state) {
      TransportState.CONNECTING -> moveIfAllowed(SessionStatus.NEGOTIATING)
      TransportState.CONNECTED -> return connect()
      TransportState.DISCONNECTED -> moveIfAllowed(SessionStatus.TEMPORARILY_DISCONNECTED)
      TransportState.EXPIRED -> moveIfAllowed(SessionStatus.EXPIRED)
    }
    return emptyList()
  }

  /**
   * Records an edit, and reports whether it was buffered.
   *
   * An edit made while connected travels over the wire, so buffering it would replay it a second
   * time on the next reconnection.
   */
  fun record(edit: PendingEdit): Boolean {
    if (status != SessionStatus.TEMPORARILY_DISCONNECTED) return false
    pending.record(edit)
    return true
  }

  private fun connect(): List<PendingEdit> {
    val resuming = status == SessionStatus.TEMPORARILY_DISCONNECTED
    moveIfAllowed(SessionStatus.CONNECTED)
    return if (resuming) pending.drain() else emptyList()
  }

  /** A transport change that the lifecycle forbids is ignored, because the transport leads. */
  private fun moveIfAllowed(next: SessionStatus) {
    runCatching { controller.moveTo(next) }
  }
}
