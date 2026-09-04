// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

import com.intellij.platform.runtime.product.ProductMode
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.linuxCustomizer
import org.jetbrains.intellij.build.macCustomizer
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.productModules
import org.jetbrains.intellij.build.windowsCustomizer
import java.nio.file.Path

/**
 * The thin client of the open remote development fork.
 *
 * It extends [ProductProperties] rather than `JetBrainsProductProperties`, because that subclass
 * applies JetBrains branding and FR-002 forbids presenting this product as a JetBrains product.
 *
 * It lives in its own module rather than in `intellij.idea.community.build`, so that adding it needs
 * no change to an upstream file. Constraint C2 keeps the count of changed upstream files low, and
 * the PyCharm product does the same thing from `python/build`.
 *
 * The client runs in [ProductMode.FRONTEND]. It holds no project source: the backend indexes,
 * analyses and executes, and this product renders the state it is sent.
 */
class ThinClientProperties(communityHome: Path) : ProductProperties() {
  override val baseFileName: String = "remote-client"

  init {
    platformPrefix = "ThinClient"
    applicationInfoModule = "intellij.platform.remoteDev.frontend"
    imagesDirectoryPath = communityHome.resolve("thin-client-images")

    productMode = ProductMode.FRONTEND
    // The loader reads META-INF/<root module>/product-modules.xml from the root module's sources.
    // Pointing this at the upstream intellij.platform.frontend.main would mean adding a resource to
    // an upstream module. Our own module is the root instead, and its product-modules.xml includes
    // the platform frontend content. Constraint C2 keeps upstream changes to what is unavoidable.
    rootModuleForModularLoader = "intellij.platform.remoteDev.frontend"

    // A modular-loader product declares its content in product-modules.xml, so this stays empty.
    productLayout.bundledPluginModules = persistentListOf()
    productLayout.skipUnresolvedContentModules = true

    scrambleMainJar = false
    buildCrossPlatformDistribution = false
  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "remoteClient-$buildNumber"

  /**
   * The default is the product's full name in lower case, which for this product carries spaces.
   * A build writes to this directory and a continuous integration job globs inside it, so a space
   * invites a quoting mistake in a shell step.
   */
  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "remote-client"

  /**
   * The default selector is the product's full name plus the version, and this product's full name
   * carries spaces. A selector names the configuration and caches directory, so a space in it is
   * rejected. The name is compressed rather than the full name shortened, because the full name is
   * what a user sees.
   */
  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "RemoteClient${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    moduleSet(CommunityModuleSets.essential())
    // Without this the product is the platform frontend with none of this fork's code in it. The
    // first successful build shipped 277 library jars and not this one, which a green build hides.
    module("intellij.platform.remoteDev.frontend")
    module("intellij.platform.remoteDev.protocol")
  }

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer =
    windowsCustomizer(projectHome) {
      fullName { "Remote Development Client" }
      installDirNameHandler { "Remote Development Client" }
    }

  override fun createLinuxCustomizer(projectHome: Path): LinuxDistributionCustomizer =
    linuxCustomizer(projectHome) {
      rootDirectoryName { _, _ -> "remote-client" }
    }

  override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer =
    macCustomizer(projectHome) {
      bundleIdentifier = "org.intellij.community.remotedev.client"
      rootDirectoryName { _, _ -> "Remote Development Client.app" }
    }
}
