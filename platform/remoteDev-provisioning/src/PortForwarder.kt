// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.remoteDev.protocol.SessionId
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/** Who asked for a port to be forwarded. */
@ApiStatus.Internal
enum class PortOrigin {
  /** The user asked for it. Asking is the consent. */
  USER,

  /** A process the host launched opened it. FR-016 offers it rather than taking it. */
  DETECTED,
}

/** A port on the host that the local machine can reach. */
@ApiStatus.Internal
data class ForwardedPort(
  val sessionId: SessionId,
  val hostPort: Int,
  val localPort: Int,
  val origin: PortOrigin,
)

/**
 * The tunnels themselves.
 *
 * This is the seam over the platform's tunnel support. The rules about who may open one and when
 * they close carry the risk, and a test must be able to drive them without a host.
 */
@ApiStatus.Internal
interface Tunnels {
  /** Opens a tunnel to [hostPort] and returns the local port that reaches it. */
  suspend fun open(hostPort: Int): Int

  suspend fun close(localPort: Int)
}

/**
 * Decides whether a detected port may be forwarded. A user interface implements this by asking.
 */
@ApiStatus.Internal
fun interface ForwardConsent {
  fun mayForward(hostPort: Int): Boolean
}

/**
 * Forwards host ports, and closes them with their session.
 *
 * A tunnel that outlives its session is a leak, and it leaves a route into the host open after the
 * session that justified it has gone. [closeSession] is therefore not an optimisation, and a test
 * asserts that nothing survives it.
 *
 * Ports are held per session, because FR-019 allows several backends on one host and two of them
 * may serve the same host port.
 */
@ApiStatus.Internal
class PortForwarder(private val tunnels: Tunnels, private val consent: ForwardConsent) {
  private val forwarded = ConcurrentHashMap<SessionId, MutableMap<Int, ForwardedPort>>()

  /** Returns the forwarded port, or null when a detected port was not consented to. */
  suspend fun forward(sessionId: SessionId, hostPort: Int, origin: PortOrigin): ForwardedPort? {
    val perSession = forwarded.computeIfAbsent(sessionId) { ConcurrentHashMap() }
    perSession[hostPort]?.let { return it }
    if (origin == PortOrigin.DETECTED && !consent.mayForward(hostPort)) return null
    val port = ForwardedPort(sessionId, hostPort, tunnels.open(hostPort), origin)
    perSession[hostPort] = port
    return port
  }

  fun active(sessionId: SessionId): List<ForwardedPort> = forwarded[sessionId]?.values?.toList() ?: emptyList()

  /** Closes every tunnel this session opened. */
  suspend fun closeSession(sessionId: SessionId) {
    val perSession = forwarded.remove(sessionId) ?: return
    perSession.values.forEach { tunnels.close(it.localPort) }
  }
}
