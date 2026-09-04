// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The failure codes from contract section 6.
 *
 * FR-010 requires every failure to state the operation, the probable cause, and one next action. The
 * [messageKey] resolves to that text in `messages/RemoteDevProtocolBundle.properties`. A code with no
 * message cannot state a next action, which a test asserts.
 *
 * A [terminal] failure ends the session. A non-terminal one lets the client try again.
 */
@ApiStatus.Internal
@Serializable
enum class SessionFailure(val messageKey: String, val terminal: Boolean) {
  VERSION_MISMATCH("session.failure.version.mismatch", terminal = true),
  PRODUCT_MISMATCH("session.failure.product.mismatch", terminal = true),
  AUTH_REJECTED("session.failure.auth.rejected", terminal = true),
  PROJECT_NOT_FOUND("session.failure.project.not.found", terminal = true),
  PROJECT_LOCKED("session.failure.project.locked", terminal = true),
  BACKEND_NOT_READY("session.failure.backend.not.ready", terminal = false),
  SESSION_EXPIRED("session.failure.session.expired", terminal = true),
  TRUST_REQUIRED("session.failure.trust.required", terminal = false),
}
