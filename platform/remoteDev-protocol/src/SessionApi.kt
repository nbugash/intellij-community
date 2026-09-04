// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import org.jetbrains.annotations.ApiStatus

/**
 * The session service that a host exposes to a thin client.
 *
 * The host registers an implementation through the `com.intellij.platform.rpc.backend.remoteApiProvider`
 * extension point. The client resolves it through `RemoteApiProviderService`.
 *
 * This interface carries the session envelope only. A feature service, such as the editor or the
 * debugger, declares its own `@Rpc` interface in its own module, which keeps this module free of any
 * feature dependency and keeps the dependency pointing inward.
 */
@ApiStatus.Internal
@Rpc
interface SessionApi : RemoteApi<Unit> {
  /** Contract section 3. Runs once per session, before any other call. */
  suspend fun handshake(offer: ClientOffer): HandshakeReply
}
