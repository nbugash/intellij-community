// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.CredentialStore
import com.intellij.remoteDev.protocol.HostKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * FR-008. A credential lives in the platform credential store, and a host record holds only a
 * reference to it.
 *
 * The test drives the platform's own [CredentialStore] interface with an in-memory implementation.
 * There is no seam of our own here on purpose: a wrapper interface over a platform interface would
 * add a name and no behaviour.
 */
class CredentialStorageTest {
  private class InMemoryCredentialStore : CredentialStore {
    val entries: MutableMap<CredentialAttributes, Credentials> = mutableMapOf()

    override fun get(attributes: CredentialAttributes): Credentials? = entries[attributes]

    override fun set(attributes: CredentialAttributes, credentials: Credentials?) {
      if (credentials == null) entries.remove(attributes) else entries[attributes] = credentials
    }
  }

  private val store = InMemoryCredentialStore()
  private val storage = CredentialStorage(store)
  private val host = HostId("build-01")
  private val secret = "correct-horse-battery-staple"

  @Test
  fun `the reference does not carry the secret`() {
    val ref = storage.store(host, secret)

    assertFalse(ref.value.contains(secret))
    assertEquals(CredentialRef.REDACTED, ref.toString())
  }

  @Test
  fun `a stored secret comes back`() {
    val ref = storage.store(host, secret)

    assertEquals(secret, storage.retrieve(ref))
  }

  @Test
  fun `the secret is not in the key the store is indexed by`() {
    // A key holding the secret would leak it to anything that lists the store.
    storage.store(host, secret)

    assertFalse(store.entries.keys.single().serviceName.contains(secret))
  }

  @Test
  fun `a revoked secret is gone at once`() {
    // FR-018 requires revocation. A revocation that leaves the value behind is not one.
    val ref = storage.store(host, secret)

    storage.revoke(ref)

    assertNull(storage.retrieve(ref))
    assert(store.entries.isEmpty())
  }

  @Test
  fun `two hosts do not share a reference`() {
    val first = storage.store(HostId("build-01"), secret)
    val second = storage.store(HostId("build-02"), secret)

    assertNotEquals(first, second)
    assertEquals(2, store.entries.size)
  }

  @Test
  fun `storing again for one host replaces the secret`() {
    // A second entry for one host would leave the old credential valid and unreachable.
    storage.store(host, secret)
    val ref = storage.store(host, "a-new-secret-value")

    assertEquals(1, store.entries.size)
    assertEquals("a-new-secret-value", storage.retrieve(ref))
  }

  @Test
  fun `an unknown reference yields nothing`() {
    assertNull(storage.retrieve(CredentialRef("never-stored")))
  }

  @Test
  fun `a host record holds the reference and not the secret`() {
    val ref = storage.store(host, secret)
    val record = HostRecord(host, HostKind.SSH, "Build one", ref)

    assertFalse(record.toString().contains(secret))
  }
}
