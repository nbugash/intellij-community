// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.remoteDev.protocol.ClientOffer
import com.intellij.remoteDev.protocol.HandshakeAccepted
import com.intellij.remoteDev.protocol.HandshakeReply
import com.intellij.remoteDev.protocol.HandshakeRefused
import com.intellij.remoteDev.protocol.ProtocolVersion
import com.intellij.remoteDev.protocol.SessionFailure
import com.intellij.remoteDev.protocol.SessionId
import com.intellij.remoteDev.protocol.SessionToken
import com.intellij.remoteDev.protocol.VersionNegotiator
import org.jetbrains.annotations.ApiStatus

/** Whether the host can serve the project a client asked for. */
@ApiStatus.Internal
enum class ProjectAvailability { AVAILABLE, NOT_FOUND, LOCKED }

/**
 * What the responder needs to know about this host.
 *
 * This interface exists as a test seam, which is the justification constitution Principle 1 requires
 * for a new type over an existing API. The handshake order is the part that carries risk, and a test
 * must be able to observe whether the project was looked up at all. A production implementation
 * reaches the platform, which no unit test can do.
 */
@ApiStatus.Internal
interface BackendSessionPolicy {
  fun isTokenValid(token: SessionToken): Boolean

  fun projectAvailability(path: String): ProjectAvailability
}

/**
 * Decides the reply to a client offer, per contract section 3.
 *
 * The order of the checks is part of the contract, not an implementation detail. A refusal must be
 * decided before the host touches any project state, so the version and the credential are checked
 * before the project is looked up. A test asserts that ordering.
 */
@ApiStatus.Internal
class HandshakeResponder(
  private val policy: BackendSessionPolicy,
  private val supportedVersions: List<ProtocolVersion>,
  private val backendProductVersion: String,
  private val nextSessionId: () -> SessionId,
) {
  fun respond(offer: ClientOffer): HandshakeReply {
    val negotiated = VersionNegotiator.select(offer.supportedProtocolVersions, supportedVersions)
    if (negotiated == null) {
      return refuse(SessionFailure.VERSION_MISMATCH, offer)
    }
    if (!policy.isTokenValid(offer.sessionToken)) {
      return refuse(SessionFailure.AUTH_REJECTED, offer)
    }
    val unavailable = availabilityFailure(offer.requestedProjectPath)
    if (unavailable != null) {
      return refuse(unavailable, offer)
    }
    return HandshakeAccepted(negotiated, backendProductVersion, nextSessionId(), capabilities = emptySet())
  }

  private fun availabilityFailure(path: String): SessionFailure? = when (policy.projectAvailability(path)) {
    ProjectAvailability.AVAILABLE -> null
    ProjectAvailability.NOT_FOUND -> SessionFailure.PROJECT_NOT_FOUND
    ProjectAvailability.LOCKED -> SessionFailure.PROJECT_LOCKED
  }

  /** A refusal echoes the offered versions, never the offered token. FR-008. */
  private fun refuse(reason: SessionFailure, offer: ClientOffer): HandshakeRefused =
    HandshakeRefused(reason, offer.supportedProtocolVersions, supportedVersions)
}
