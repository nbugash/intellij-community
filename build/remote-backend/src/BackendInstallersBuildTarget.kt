// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.remoteBackend

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.createBuildContext

/**
 * Builds the host distribution.
 *
 * A host is deployed to a machine and started by `HostProvisioner`, so unlike the client it needs no
 * installer, only a directory with `bin/remote-backend` in it.
 */
object BackendInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      val context = createBuildContext(
        projectHome = COMMUNITY_ROOT.communityRoot,
        productProperties = BackendProperties(COMMUNITY_ROOT.communityRoot),
        options = buildOptions(),
      )
      buildDistributions(context)
    }
  }

  private fun buildOptions(): BuildOptions = BuildOptions().apply {
    incrementalCompilation = true
    useCompiledClassesFromProjectOutput = false
    buildStepsToSkip += skippedSteps()
    // A host runs where it is provisioned. Linux is what this fork's provisioning reaches today, and
    // building every operating system would triple the time for artifacts nothing deploys.
    // -Dintellij.build.target.os=all still wins, as usual.
    if (System.getProperty(BuildOptions.TARGET_OS_PROPERTY).isNullOrEmpty()) {
      targetOs = persistentListOf(OsFamily.LINUX)
    }
  }

  /** The steps this fork cannot run yet, each with the reason it cannot. */
  private fun skippedSteps(): List<String> = listOf(
    // The fork signs nothing yet, and it ships no cross-platform archive.
    BuildOptions.MAC_SIGN_STEP,
    BuildOptions.WIN_SIGN_STEP,
    BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP,
    // Both of these boot the product to enumerate it. A backend boots headless and waits for a
    // client, so neither terminates here. Same root cause as in the client's build target.
    BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP,
    BuildOptions.PROVIDED_MODULES_LIST_STEP,
    // A host ships no marketplace plugins, so nothing here needs publishing.
    BuildOptions.NON_BUNDLED_PLUGINS_STEP,
  )
}
