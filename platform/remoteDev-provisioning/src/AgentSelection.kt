// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.provisioning

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/** The operating systems a host can run. FR-013 requires SSH, WSL and container hosts, which span these. */
@ApiStatus.Internal
enum class HostOs { LINUX, MACOS, WINDOWS }

/** The processor architectures a host can run. */
@ApiStatus.Internal
enum class HostArch { X64, AARCH64 }

/**
 * What a host runs.
 *
 * This type carries no default. A caller supplies what it read from the host through the platform
 * execution environment layer. The client platform is not evidence of the host platform: a
 * developer on macOS routinely drives a Linux host, and guessing would deploy a binary that cannot
 * execute.
 */
@ApiStatus.Internal
data class HostPlatform(val os: HostOs, val arch: HostArch)

/**
 * Chooses the agent binary for a host, and refuses one that does not match.
 */
@ApiStatus.Internal
object AgentSelection {
  private const val EXECUTABLE_SUFFIX: String = ".exe"

  /** The binary name for [platform]. Distinct for every platform, which a test asserts. */
  fun binaryName(platform: HostPlatform): String {
    val base = "ijent-agent-${platform.os.name.lowercase()}-${platform.arch.name.lowercase()}"
    return if (platform.os == HostOs.WINDOWS) base + EXECUTABLE_SUFFIX else base
  }

  /**
   * Returns text describing why [agent] cannot run on [host], or null when it can.
   *
   * The text names both platforms, because FR-010 requires a failure to state a next action and
   * "wrong platform" alone tells the user nothing.
   */
  @Nls
  fun mismatch(agent: HostPlatform, host: HostPlatform): String? =
    if (agent == host) null
    else RemoteDevProvisioningBundle.message("agent.platform.mismatch", describe(agent), describe(host))

  private fun describe(platform: HostPlatform): String =
    "${platform.os.name.lowercase()} ${platform.arch.name.lowercase()}"
}
