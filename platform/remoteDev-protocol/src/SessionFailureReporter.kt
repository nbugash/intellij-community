// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.jetbrains.annotations.ApiStatus

/**
 * Turns a refusal into text for the user.
 *
 * FR-010 requires every failure to state the operation, the probable cause, and one next action. The
 * text lives in `messages/RemoteDevProtocolBundle.properties`, so it can be localised.
 *
 * The reporter takes the offer as well as the refusal because a refusal carries only what the wire
 * needs. The project path and the client build stay out of the wire message, and widening the wire
 * contract to carry them would be a change to the contract for the sake of a log line.
 *
 * FR-008: no branch here reads [ClientOffer.sessionToken]. A test drives every code and asserts it.
 */
@ApiStatus.Internal
object SessionFailureReporter {
  fun describe(refusal: HandshakeRefused, offer: ClientOffer, backendProductVersion: String): String =
    when (refusal.reason) {
      SessionFailure.VERSION_MISMATCH -> message(refusal, versions(refusal.offeredVersions), versions(refusal.backendSupportedVersions))
      SessionFailure.PRODUCT_MISMATCH -> message(refusal, offer.clientProductVersion, backendProductVersion)
      SessionFailure.PROJECT_NOT_FOUND -> message(refusal, offer.requestedProjectPath)
      SessionFailure.PROJECT_LOCKED -> message(refusal, offer.requestedProjectPath)
      SessionFailure.TRUST_REQUIRED -> message(refusal, offer.requestedProjectPath)
      SessionFailure.SESSION_EXPIRED -> message(refusal, RETENTION_WINDOW_TEXT)
      SessionFailure.AUTH_REJECTED -> message(refusal)
      SessionFailure.BACKEND_NOT_READY -> message(refusal)
      // The address is not a parameter here. This refusal is decided before the handshake, by
      // SessionTransportSecurity, and the user is looking at the address they typed.
      SessionFailure.INSECURE_TRANSPORT -> message(refusal)
    }

  private fun message(refusal: HandshakeRefused, vararg params: Any): String =
    RemoteDevProtocolBundle.message(refusal.reason.messageKey, *params)

  private fun versions(versions: List<ProtocolVersion>): String = versions.joinToString(", ")

  /** Contract section 4 states "at least five minutes". The exact value is a P1.3 decision. */
  private const val RETENTION_WINDOW_TEXT: String = "five minutes"
}
