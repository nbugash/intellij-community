// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.frontend

import org.jetbrains.annotations.ApiStatus
import java.net.URI

/**
 * Where a thin client connects, and which project it asks for.
 *
 * The scheme is this project's own. FR-052 states that this client connects only to backends this
 * project produces, so a link carrying another scheme is refused rather than attempted. A link
 * carries no credential: the session token travels in the handshake, never in a URI that a user
 * might paste into a chat window.
 */
@ApiStatus.Internal
data class HostLink(val host: String, val port: Int, val projectPath: String) {
  fun toUri(): URI = URI(SCHEME, null, host, port, projectPath, null, null)

  companion object {
    const val SCHEME: String = "splitclient"

    fun parse(uri: URI): HostLink {
      require(uri.scheme == SCHEME) { "A host link uses the '$SCHEME' scheme, got '${uri.scheme}'" }
      val host = uri.host
      require(!host.isNullOrBlank()) { "A host link needs a host: $uri" }
      require(uri.port > 0) { "A host link needs a port: $uri" }
      val path = uri.path
      require(!path.isNullOrBlank() && path != "/") { "A host link needs a project path: $uri" }
      return HostLink(host, uri.port, path)
    }
  }
}
