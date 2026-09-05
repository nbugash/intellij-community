// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * FR-018. The session stream is encrypted in transit.
 *
 * Two shapes satisfy that, and this decides between them. See the class comment on
 * [SessionTransportSecurity] for why a plain loopback endpoint is one of them.
 */
class SessionTransportSecurityTest {
  private fun verify(host: String, tls: Boolean) =
    SessionTransportSecurity.verify(TransportEndpoint(host, port = 5990, tls = tls))

  @Test
  fun `TLS is allowed on a routable address`() {
    assertEquals(TransportVerdict.Allowed, verify("build-01.example.com", tls = true))
  }

  @Test
  fun `plaintext on a routable address is refused`() {
    assertEquals(TransportVerdict.Refused(SessionFailure.INSECURE_TRANSPORT), verify("build-01.example.com", tls = false))
  }

  @Test
  fun `plaintext on IPv4 loopback is allowed`() {
    assertEquals(TransportVerdict.Allowed, verify("127.0.0.1", tls = false))
  }

  @Test
  fun `plaintext on the whole IPv4 loopback range is allowed`() {
    // The whole 127 block is loopback, not only 127.0.0.1.
    assertEquals(TransportVerdict.Allowed, verify("127.4.5.6", tls = false))
  }

  @Test
  fun `plaintext on IPv6 loopback is allowed`() {
    assertEquals(TransportVerdict.Allowed, verify("::1", tls = false))
    assertEquals(TransportVerdict.Allowed, verify("[::1]", tls = false))
  }

  @Test
  fun `plaintext on the localhost name is allowed`() {
    assertEquals(TransportVerdict.Allowed, verify("localhost", tls = false))
    assertEquals(TransportVerdict.Allowed, verify("LOCALHOST", tls = false))
  }

  @Test
  fun `a name that only starts with localhost is refused`() {
    // "localhost.attacker.example" resolves wherever its owner points it.
    assertEquals(TransportVerdict.Refused(SessionFailure.INSECURE_TRANSPORT), verify("localhost.attacker.example", tls = false))
  }

  @Test
  fun `an address that only starts with the loopback digits is refused`() {
    // 127.0.0.1.attacker.example, and 1270018 style tricks, are not loopback.
    assertEquals(TransportVerdict.Refused(SessionFailure.INSECURE_TRANSPORT), verify("127.0.0.1.attacker.example", tls = false))
  }

  @Test
  fun `the bind address for a host backend is loopback`() {
    // FR-018 requires the backend to bind loopback. The tunnel reaches it; the network does not.
    assertEquals("127.0.0.1", SessionTransportSecurity.BIND_ADDRESS)
  }

  @Test
  fun `the refusal is terminal`() {
    // Retrying a plaintext connection would keep sending the credential in clear text.
    assert(SessionFailure.INSECURE_TRANSPORT.terminal)
  }
}
