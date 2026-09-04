// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The identity of a session. It survives a reconnection, so the host can resume the session that a
 * dropped connection left behind.
 */
@ApiStatus.Internal
@Serializable
@JvmInline
value class SessionId(val value: String)

/**
 * The credential that authorises one session.
 *
 * [toString] is redacted on purpose. FR-008 forbids a credential in a log or a diagnostic report,
 * and SC-013 verifies it by scanning the produced artifacts. A data class that holds this type
 * inherits the redaction, because its generated `toString` calls this one.
 */
@ApiStatus.Internal
@Serializable
@JvmInline
value class SessionToken(val value: String) {
  override fun toString(): String = REDACTED

  companion object {
    const val REDACTED: String = "SessionToken(redacted)"
  }
}

/**
 * The kinds of host a backend runs on. FR-013 requires all three.
 */
@ApiStatus.Internal
@Serializable
enum class HostKind { SSH, WSL, CONTAINER }

/**
 * The session states from contract section 4.
 *
 * `REFUSED` and `EXPIRED` are terminal. `TEMPORARILY_DISCONNECTED` is the state a session holds while
 * it waits to reconnect, and FR-015 requires it to keep unsaved work for at least the retention
 * window.
 */
@ApiStatus.Internal
@Serializable
enum class SessionStatus {
  CONNECTING,
  NEGOTIATING,
  CONNECTED,
  TEMPORARILY_DISCONNECTED,
  REFUSED,
  EXPIRED,
}
