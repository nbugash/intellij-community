// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.remoteDev.protocol.SessionFailure
import org.jetbrains.annotations.ApiStatus

/** What came of a client's request. */
@ApiStatus.Internal
sealed interface DispatchResult<out T> {
  data class Ran<out T>(val value: T) : DispatchResult<T>

  data class Refused(val failure: SessionFailure) : DispatchResult<Nothing>
}

/**
 * The one door a client request goes through on the host.
 *
 * [BackendTrustGate] holds the rule. This holds the enforcement, and the two are separate because
 * the rule is worth reading on its own and the enforcement is worth having only one of.
 *
 * The shape carries the guarantee: [action] is a lambda, and it is not called when the gate refuses.
 * A design that ran the action and then checked, or that returned a permission for the caller to
 * honour, would leave FR-007 to the discipline of every future call site. This leaves it to the
 * compiler instead, because a caller cannot reach the action without going through here.
 *
 * Every operation a client can ask for must arrive here. That is the rule a reviewer checks.
 */
@ApiStatus.Internal
class BackendOperationDispatcher(private val isTrusted: (projectPath: String) -> Boolean) {
  fun <T> dispatch(operation: BackendOperation, projectPath: String, action: () -> T): DispatchResult<T> {
    val refusal = BackendTrustGate.refusalFor(operation, isTrusted(projectPath))
    if (refusal != null) return DispatchResult.Refused(refusal)
    return DispatchResult.Ran(action())
  }
}
