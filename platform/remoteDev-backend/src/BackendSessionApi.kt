// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.backend

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Answers the handshake using the state of this host.
 *
 * Token validation was fail-closed until T103. It now asks [SessionTokenRegistry], which holds a
 * digest rather than the token, and which the host owner can revoke at any moment as FR-018
 * requires. An unknown, revoked or expired token still yields `AUTH_REJECTED`.
 */
@ApiStatus.Internal
internal class HostSessionPolicy(
  private val registry: BackendSessionRegistry,
  private val tokens: SessionTokenRegistry,
  private val now: () -> Duration,
) : BackendSessionPolicy {
  override fun isTokenValid(token: SessionToken): Boolean = tokens.validate(token, now()) != null

  override fun projectAvailability(path: String): ProjectAvailability = when {
    registry.holdsProject(path) -> ProjectAvailability.LOCKED
    isOpenHere(path) -> ProjectAvailability.AVAILABLE
    else -> ProjectAvailability.NOT_FOUND
  }

  /**
   * Whether this host already has the project at [path] open.
   *
   * `getOpenProjects` documents its own contract: a caller that is not inside a read action must
   * check [Project.isDisposed] on each project before using it. The array is a snapshot, and a
   * project can be disposed the moment after it is taken.
   *
   * The disposal check is used rather than a read action on purpose. This runs on the RPC thread
   * that is answering a handshake, and taking the read lock there would block that answer behind
   * any write the host happens to be doing. The platform offers the check as the alternative, so
   * this takes it.
   */
  private fun isOpenHere(path: String): Boolean =
    ProjectManager.getInstance().openProjects.any { !it.isDisposed && it.basePath == path }
}

/** The host side of [SessionApi]. */
@ApiStatus.Internal
internal class BackendSessionApi : SessionApi {
  override suspend fun handshake(offer: ClientOffer): HandshakeReply = responder().respond(offer)

  private fun responder(): HandshakeResponder {
    val application = ApplicationManager.getApplication()
    val registry = application.service<BackendSessionRegistry>()
    return HandshakeResponder(
      policy = HostSessionPolicy(registry, application.service<SessionTokenRegistry>(), ::uptime),
      supportedVersions = ProtocolVersions.supported(),
      backendProductVersion = ApplicationInfo.getInstance().build.asString(),
      nextSessionId = { SessionId(UUID.randomUUID().toString()) },
    )
  }
}

/** A monotonic clock. Wall time would let a clock change extend or shorten a token's life. */
private fun uptime(): Duration = System.nanoTime().nanoseconds

/** Publishes [SessionApi] to a connected client through the platform RPC layer. */
@ApiStatus.Internal
internal class BackendSessionApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<SessionApi>()) { BackendSessionApi() }
  }
}
