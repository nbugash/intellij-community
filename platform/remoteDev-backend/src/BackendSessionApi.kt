// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.remoteDev.protocol.ClientOffer
import com.intellij.remoteDev.protocol.HandshakeReply
import com.intellij.remoteDev.protocol.ProtocolVersions
import com.intellij.remoteDev.protocol.SessionApi
import com.intellij.remoteDev.protocol.SessionId
import com.intellij.remoteDev.protocol.SessionToken
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus
import java.util.UUID

/**
 * Answers the handshake using the state of this host.
 *
 * Token validation fails closed on purpose. The credential store is wired in P1.8 by T103, and until
 * then no token can be proved valid. Accepting one would be a security hole, so the host refuses
 * with `AUTH_REJECTED` rather than trusting an unverified credential. FR-018 requires a revocable
 * credential, which a permissive default would defeat.
 */
@ApiStatus.Internal
internal class HostSessionPolicy(private val registry: BackendSessionRegistry) : BackendSessionPolicy {
  override fun isTokenValid(token: SessionToken): Boolean = false

  override fun projectAvailability(path: String): ProjectAvailability = when {
    registry.holdsProject(path) -> ProjectAvailability.LOCKED
    isOpenHere(path) -> ProjectAvailability.AVAILABLE
    else -> ProjectAvailability.NOT_FOUND
  }

  private fun isOpenHere(path: String): Boolean =
    ProjectManager.getInstance().openProjects.any { it.basePath == path }
}

/** The host side of [SessionApi]. */
@ApiStatus.Internal
internal class BackendSessionApi : SessionApi {
  override suspend fun handshake(offer: ClientOffer): HandshakeReply = responder().respond(offer)

  private fun responder(): HandshakeResponder {
    val registry = ApplicationManager.getApplication().service<BackendSessionRegistry>()
    return HandshakeResponder(
      policy = HostSessionPolicy(registry),
      supportedVersions = ProtocolVersions.supported(),
      backendProductVersion = ApplicationInfo.getInstance().build.asString(),
      nextSessionId = { SessionId(UUID.randomUUID().toString()) },
    )
  }
}

/** Publishes [SessionApi] to a connected client through the platform RPC layer. */
@ApiStatus.Internal
internal class BackendSessionApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<SessionApi>()) { BackendSessionApi() }
  }
}
