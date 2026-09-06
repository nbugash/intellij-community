// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.jetbrains.annotations.ApiStatus

/** Where a session stream goes, and whether the socket itself is encrypted. */
@ApiStatus.Internal
data class TransportEndpoint(val host: String, val port: Int, val tls: Boolean)

/** Whether the session may use an endpoint. */
@ApiStatus.Internal
sealed interface TransportVerdict {
  object Allowed : TransportVerdict

  data class Refused(val failure: SessionFailure) : TransportVerdict
}

/**
 * Decides whether the session stream is encrypted in transit, as FR-018 requires.
 *
 * Two shapes satisfy the requirement, and only two:
 *
 * TLS on the socket. The traffic is encrypted wherever it goes.
 *
 * Plain text on a loopback address. The traffic never reaches a network interface, so there is no
 * transit to encrypt. This is the shape the host backend uses: it binds [BIND_ADDRESS], and an SSH
 * tunnel carries the bytes off the machine, encrypted by SSH. Refusing this shape would refuse the
 * design in D2, and would buy nothing, because the plain hop is inside one machine.
 *
 * Everything else is refused. A plain text socket on a routable address puts the session credential
 * on the wire in clear text, and it lets any host on the path read the source code in the stream.
 *
 * Loopback is decided from the text of the address, and never by resolving a name. A name resolves
 * to whatever its owner points it at, so `localhost.attacker.example` would otherwise be a way to
 * turn this check off.
 */
@ApiStatus.Internal
object SessionTransportSecurity {
  /** The address a host backend binds. FR-018: the tunnel reaches it; the network does not. */
  const val BIND_ADDRESS: String = "127.0.0.1"

  private const val LOCALHOST = "localhost"
  private const val IPV6_LOOPBACK = "::1"
  private const val IPV4_LOOPBACK_BLOCK = 127
  private const val IPV4_OCTETS = 4
  private const val LARGEST_OCTET = 255

  fun verify(endpoint: TransportEndpoint): TransportVerdict {
    if (endpoint.tls || isLoopback(endpoint.host)) return TransportVerdict.Allowed
    return TransportVerdict.Refused(SessionFailure.INSECURE_TRANSPORT)
  }

  private fun isLoopback(host: String): Boolean {
    val name = host.removeSurrounding("[", "]").lowercase()
    if (name == LOCALHOST || name == IPV6_LOOPBACK) return true
    return isIpv4Loopback(name)
  }

  private fun isIpv4Loopback(name: String): Boolean {
    val octets = name.split('.')
    if (octets.size != IPV4_OCTETS) return false
    if (!octets.all(::isOctet)) return false
    return octets[0].toInt() == IPV4_LOOPBACK_BLOCK
  }

  private fun isOctet(text: String): Boolean =
    text.isNotEmpty() && text.length <= 3 && text.all(Char::isDigit) && text.toInt() <= LARGEST_OCTET
}
