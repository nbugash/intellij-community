// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.remoteDev.protocol.HostKind
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/** The identity of a host this client can provision a backend on. */
@ApiStatus.Internal
@JvmInline
value class HostId(val value: String) {
  init {
    require(value.isNotBlank()) { "A host id must not be blank" }
  }
}

/**
 * A key into the platform credential store.
 *
 * FR-008 forbids a credential in a record, a log, or a version-controlled file, and SC-013 verifies
 * it by scanning the produced artifacts. This type holds the reference only, and [toString] hides
 * even that, so a record that contains one cannot print it by accident.
 */
@ApiStatus.Internal
@JvmInline
value class CredentialRef(val value: String) {
  init {
    require(value.isNotBlank()) { "A credential reference must not be blank" }
  }

  override fun toString(): String = REDACTED

  companion object {
    const val REDACTED: String = "CredentialRef(hidden)"
  }
}

/** What this client knows about one host. It never holds the credential itself. */
@ApiStatus.Internal
data class HostRecord(
  val id: HostId,
  val kind: HostKind,
  val displayName: String,
  val credentialRef: CredentialRef,
)

/**
 * The hosts this client can reach.
 *
 * The map is concurrent because a connection attempt and the user interface read it from different
 * threads.
 */
@ApiStatus.Internal
class HostRegistry {
  private val hosts = ConcurrentHashMap<HostId, HostRecord>()

  fun remember(host: HostRecord) {
    hosts[host.id] = host
  }

  fun forget(id: HostId) {
    hosts.remove(id)
  }

  fun find(id: HostId): HostRecord? = hosts[id]

  fun all(): List<HostRecord> = hosts.values.toList()
}
