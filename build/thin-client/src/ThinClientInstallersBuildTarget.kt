// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.jetbrains.intellij.build.impl.buildDistributions
import org.jetbrains.intellij.build.impl.createBuildContext

/** Builds the thin client installers. See `thin-client-installers.cmd`. */
object ThinClientInstallersBuildTarget {
  @JvmStatic
  fun main(args: Array<String>) {
    runBlocking(Dispatchers.Default) {
      val options = BuildOptions().apply {
        incrementalCompilation = true
        useCompiledClassesFromProjectOutput = false
        // The fork signs nothing yet, and it ships no cross-platform archive.
        buildStepsToSkip += listOf(
          BuildOptions.MAC_SIGN_STEP,
          BuildOptions.WIN_SIGN_STEP,
          BuildOptions.CROSS_PLATFORM_DISTRIBUTION_STEP,
          // The searchable-options index is built by starting the IDE headlessly and walking every
          // settings page. A frontend product cannot start on its own yet, because it renders state
          // that a backend supplies. Re-enable this once the client can boot against a host.
          BuildOptions.SEARCHABLE_OPTIONS_INDEX_STEP,
          // A thin client ships no marketplace plugins, so nothing here needs publishing. The step
          // also fails in this checkout for a reason unrelated to this product: patching
          // intellij.gradle.plugin needs intellij.gradle.completion.ex, which the project lacks.
          BuildOptions.NON_BUNDLED_PLUGINS_STEP,
          // Same root cause as the searchable-options step: this one runs IntellijLoader with
          // 'listBundledPlugins' to enumerate the product's modules, which boots the product on its
          // own. A frontend product cannot boot without a host.
          BuildOptions.PROVIDED_MODULES_LIST_STEP,
        )
        // Default to Linux and macOS. BuildOptions defaults to every operating system, and a
        // Windows installer needs NSIS plus a Windows launcher that this fork does not exercise.
        // The standard property still wins, so -Dintellij.build.target.os=all behaves as usual.
        if (System.getProperty(BuildOptions.TARGET_OS_PROPERTY).isNullOrEmpty()) {
          targetOs = persistentListOf(OsFamily.LINUX, OsFamily.MACOS)
        }
      }
      val context = createBuildContext(
        projectHome = COMMUNITY_ROOT.communityRoot,
        productProperties = ThinClientProperties(COMMUNITY_ROOT.communityRoot),
        options = options,
      )
      buildDistributions(context)
    }
  }
}
