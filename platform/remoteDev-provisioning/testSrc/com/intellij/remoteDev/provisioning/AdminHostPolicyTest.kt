// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.remoteDev.protocol.TransportEndpoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FR-020. An administrator can fix which hosts a machine may reach, and can require TLS.
 *
 * The tests drive the lookup directly. The lookup that reads the operating system is one lambda,
 * built in [AdminHostPolicy.forProduct], and a test of it would test the platform, not this.
 */
class AdminHostPolicyTest {
  private fun policyOf(vararg settings: Pair<String, String>): AdminHostPolicy {
    val values = settings.toMap()
    return AdminHostPolicy(values::get)
  }

  @Test
  fun `no administrator setting leaves every host reachable`() {
    // A machine with no administrator must keep working. Absence is not a deny.
    val policy = policyOf()

    assertNull(policy.allowedHosts())
    assertTrue(policy.permits(endpoint("build-01.example.com")))
  }

  @Test
  fun `a host on the list is permitted`() {
    val policy = policyOf(AdminHostPolicy.ALLOWED_HOSTS_KEY to "build-01.example.com,build-02.example.com")

    assertTrue(policy.permits(endpoint("build-02.example.com")))
  }

  @Test
  fun `a host that is not on the list is refused`() {
    val policy = policyOf(AdminHostPolicy.ALLOWED_HOSTS_KEY to "build-01.example.com")

    assertFalse(policy.permits(endpoint("build-99.example.com")))
  }

  @Test
  fun `an empty list refuses every host`() {
    // The setting is present and names no host. That reads as "none", and it is the safe reading.
    val policy = policyOf(AdminHostPolicy.ALLOWED_HOSTS_KEY to "")

    assertEquals(emptyList<String>(), policy.allowedHosts())
    assertFalse(policy.permits(endpoint("build-01.example.com")))
  }

  @Test
  fun `spaces around a name do not change it`() {
    val policy = policyOf(AdminHostPolicy.ALLOWED_HOSTS_KEY to " build-01.example.com , build-02.example.com ")

    assertTrue(policy.permits(endpoint("build-01.example.com")))
    assertTrue(policy.permits(endpoint("build-02.example.com")))
  }

  @Test
  fun `the comparison ignores letter case`() {
    // A host name is not case sensitive, and an administrator must not have to know that.
    val policy = policyOf(AdminHostPolicy.ALLOWED_HOSTS_KEY to "Build-01.Example.COM")

    assertTrue(policy.permits(endpoint("build-01.example.com")))
  }

  @Test
  fun `a name is matched whole`() {
    // "example.com" on the list must not admit "example.com.attacker.example".
    val policy = policyOf(AdminHostPolicy.ALLOWED_HOSTS_KEY to "example.com")

    assertFalse(policy.permits(endpoint("example.com.attacker.example")))
    assertFalse(policy.permits(endpoint("notexample.com")))
  }

  @Test
  fun `TLS is not required by default`() {
    // The normal shape is a loopback address reached through a tunnel. See SessionTransportSecurity.
    assertFalse(policyOf().requiresTls())
  }

  @Test
  fun `an administrator can require TLS`() {
    val policy = policyOf(AdminHostPolicy.REQUIRE_TLS_KEY to "true")

    assertTrue(policy.requiresTls())
    assertFalse(policy.permits(TransportEndpoint("127.0.0.1", 5990, tls = false)))
    assertTrue(policy.permits(TransportEndpoint("127.0.0.1", 5990, tls = true)))
  }

  @Test
  fun `a value that is not true or false leaves TLS optional`() {
    // The platform provider warns and falls back. This asserts the fallback we chose.
    assertFalse(policyOf(AdminHostPolicy.REQUIRE_TLS_KEY to "yes").requiresTls())
  }

  @Test
  fun `both rules must pass`() {
    val policy = policyOf(
      AdminHostPolicy.ALLOWED_HOSTS_KEY to "build-01.example.com",
      AdminHostPolicy.REQUIRE_TLS_KEY to "true",
    )

    assertFalse(policy.permits(TransportEndpoint("build-01.example.com", 5990, tls = false)))
    assertFalse(policy.permits(TransportEndpoint("build-99.example.com", 5990, tls = true)))
    assertTrue(policy.permits(TransportEndpoint("build-01.example.com", 5990, tls = true)))
  }

  @Test
  fun `every key an administrator sets is a legal key for the platform provider`() {
    // OsRegistryConfigProvider rejects a key that is not a word, and it does so at run time.
    val wordCharacters = Regex("^\\w+$")

    assertTrue(AdminHostPolicy.ALLOWED_HOSTS_KEY.matches(wordCharacters))
    assertTrue(AdminHostPolicy.REQUIRE_TLS_KEY.matches(wordCharacters))
  }

  private fun endpoint(host: String) = TransportEndpoint(host, port = 5990, tls = false)
}
