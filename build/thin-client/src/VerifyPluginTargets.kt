// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

import com.intellij.openapi.util.BuildNumber

/** A plugin slice as its descriptor declares it. */
data class PluginTarget(
  val id: String,
  val sinceBuild: String?,
  val untilBuild: String?,
  val moduleDependencies: List<String> = emptyList(),
)

/** A reason one plugin fails FR-056. */
sealed interface TargetViolation {
  val pluginId: String

  /** The declared range excludes one of the two builds the plugin must run on. */
  data class OutsideDeclaredRange(
    override val pluginId: String,
    val target: String,
    val build: String,
    val range: String,
  ) : TargetViolation

  /** The plugin needs a module that exists only in the fork, so it cannot load on stock. */
  data class ForkOnlyDependency(override val pluginId: String, val module: String) : TargetViolation
}

/**
 * Checks that a plugin slice can load on stock Community Edition and on this fork, per FR-056.
 *
 * Two things can break that promise, and they break it differently.
 *
 * A declared build range that excludes either build is the visible one. The platform's own
 * [BuildNumber] does the parsing and the comparison, including the `263.*` wildcard form, so this
 * only states the rule.
 *
 * A dependency on a fork-only module is the quiet one, and it is the reason this check exists at
 * all. Such a plugin installs on stock Community Edition, declares a range that admits it, and then
 * fails to load with a missing-module error at run time. The range check would never see it.
 *
 * ### Wiring
 *
 * There is no entry point that walks a distribution yet, because there is no plugin slice yet to
 * walk: slices arrive in P2. Building the scanner now would mean writing descriptor parsing against
 * zero real descriptors. The rule is the part worth having early, so the rule is what this is.
 */
object VerifyPluginTargets {
  /**
   * Modules this fork adds. A plugin that depends on one of these cannot load on stock Community
   * Edition.
   *
   * The list is written out rather than derived. Deriving it would mean reading the fork's module
   * graph at check time, and a check that computes its own expectations from the thing it is
   * checking cannot fail. Adding a module to the fork means adding a line here, and
   * `docs/fork-platform-changes.md` is where that obligation is recorded.
   */
  val FORK_ONLY_MODULES: Set<String> = setOf(
    "intellij.platform.remoteDev.protocol",
    "intellij.platform.remoteDev.backend",
    "intellij.platform.remoteDev.frontend",
    "intellij.platform.remoteDev.provisioning",
    "intellij.platform.ijent.agent",
  )

  fun verify(plugin: PluginTarget, stock: BuildNumber, fork: BuildNumber): List<TargetViolation> =
    rangeViolations(plugin, stock, fork) + dependencyViolations(plugin)

  private fun rangeViolations(plugin: PluginTarget, stock: BuildNumber, fork: BuildNumber): List<TargetViolation> =
    listOf("stock" to stock, "fork" to fork).mapNotNull { (name, build) ->
      if (admits(plugin, build)) null
      else TargetViolation.OutsideDeclaredRange(plugin.id, name, build.asString(), describeRange(plugin))
    }

  private fun dependencyViolations(plugin: PluginTarget): List<TargetViolation> =
    plugin.moduleDependencies.filter { it in FORK_ONLY_MODULES }
      .map { TargetViolation.ForkOnlyDependency(plugin.id, it) }

  /** An absent bound is open, which is what the platform means by omitting it. */
  private fun admits(plugin: PluginTarget, build: BuildNumber): Boolean {
    val since = plugin.sinceBuild?.let { BuildNumber.fromString(it, plugin.id, null) }
    if (since != null && since > build) return false
    val until = plugin.untilBuild?.let { BuildNumber.fromString(it, plugin.id, null) }
    return until == null || until >= build
  }

  private fun describeRange(plugin: PluginTarget): String =
    "${plugin.sinceBuild ?: "any"}..${plugin.untilBuild ?: "any"}"
}
