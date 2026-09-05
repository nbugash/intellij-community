// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import com.intellij.remoteDev.OsRegistryConfigProvider
import com.intellij.remoteDev.protocol.TransportEndpoint
import org.jetbrains.annotations.ApiStatus

/**
 * The rules an administrator sets for the whole machine, per FR-020.
 *
 * The settings come from the operating system, through the platform's [OsRegistryConfigProvider]:
 * the registry on Windows, `/etc/xdg` on Linux, and Application Support on macOS. A user cannot
 * change them from inside the product, which is the point of them.
 *
 * Absence and emptiness mean different things here, and the difference is deliberate:
 *
 * No setting at all means no rule. A machine with no administrator must keep working.
 *
 * A setting that names no host means no host. The administrator wrote the setting, and the list
 * they wrote is empty. This is also the safe reading of the two.
 *
 * Nothing secret goes through these keys. [OsRegistryConfigProvider.get] writes the value it read
 * into the log, so a credential put here would land in the log and break FR-008.
 *
 * The lookup is a parameter so a test can drive the rules without a machine to configure.
 */
@ApiStatus.Internal
class AdminHostPolicy(private val lookup: (String) -> String?) {
  /** The hosts the machine may reach, or null when the administrator set no rule. */
  fun allowedHosts(): List<String>? =
    lookup(ALLOWED_HOSTS_KEY)?.split(SEPARATOR)?.map { it.trim() }?.filter(String::isNotEmpty)

  fun requiresTls(): Boolean = lookup(REQUIRE_TLS_KEY)?.lowercase() == "true"

  /** Whether both rules let the session use [endpoint]. */
  fun permits(endpoint: TransportEndpoint): Boolean {
    if (requiresTls() && !endpoint.tls) return false
    val allowed = allowedHosts() ?: return true
    return allowed.any { it.equals(endpoint.host, ignoreCase = true) }
  }

  companion object {
    /** A comma separated list of host names. See the class comment on absence and emptiness. */
    const val ALLOWED_HOSTS_KEY: String = "remote_dev_allowed_hosts"

    /** "true" refuses an endpoint that is not TLS, loopback included. */
    const val REQUIRE_TLS_KEY: String = "remote_dev_require_tls"

    private const val SEPARATOR = ','

    /**
     * Reads the rules from the operating system.
     *
     * [configName] chooses the place they are read from, and it must match the product. The
     * provider is built once, because each lookup reads the registry or the disk.
     */
    fun forProduct(configName: String): AdminHostPolicy {
      val provider = OsRegistryConfigProvider(configName)
      return AdminHostPolicy { key -> provider.get(key)?.value }
    }
  }
}
