// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The handshake messages from contract section 3.
 *
 * The handshake runs once for each session, before any service call and before the host touches any
 * project state.
 */

/** Contract section 3.1. The client offer. It carries a session token, never a password or a key. */
@ApiStatus.Internal
@Serializable
data class ClientOffer(
  /** Ordered, most recent first. The client decides the preference. */
  val supportedProtocolVersions: List<ProtocolVersion>,
  val clientProductVersion: String,
  val clientPlatform: String,
  val sessionToken: SessionToken,
  val requestedProjectPath: String,
)

/** Contract section 3.2. The host sends exactly one reply. */
@ApiStatus.Internal
@Serializable
sealed interface HandshakeReply

@ApiStatus.Internal
@Serializable
data class HandshakeAccepted(
  val negotiatedVersion: ProtocolVersion,
  val backendProductVersion: String,
  val sessionId: SessionId,
  /** Optional features this host offers. The vocabulary is open, so a later slice can add one. */
  val capabilities: Set<String>,
) : HandshakeReply

/**
 * A refusal states both version sets, because a refusal that hides either side is not actionable.
 * FR-010 requires the next action, which [SessionFailure.messageKey] supplies.
 */
@ApiStatus.Internal
@Serializable
data class HandshakeRefused(
  val reason: SessionFailure,
  val offeredVersions: List<ProtocolVersion>,
  val backendSupportedVersions: List<ProtocolVersion>,
) : HandshakeReply
