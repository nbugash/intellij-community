// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.remoteBackend

import com.intellij.platform.ijent.community.buildConstants.IJENT_BOOT_CLASSPATH_MODULE
import com.intellij.platform.runtime.product.ProductMode
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.CompatibleBuildRange
import org.jetbrains.intellij.build.LinuxDistributionCustomizer
import org.jetbrains.intellij.build.MacDistributionCustomizer
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.impl.PlatformJarNames.PLATFORM_CORE_NIO_FS
import org.jetbrains.intellij.build.WindowsDistributionCustomizer
import org.jetbrains.intellij.build.linuxCustomizer
import org.jetbrains.intellij.build.macCustomizer
import org.jetbrains.intellij.build.productLayout.CommunityModuleSets
import org.jetbrains.intellij.build.productLayout.ProductModulesContentSpec
import org.jetbrains.intellij.build.productLayout.productModules
import org.jetbrains.intellij.build.windowsCustomizer
import java.nio.file.Path

/**
 * The remote development host of the open fork.
 *
 * It extends [ProductProperties] rather than `JetBrainsProductProperties`, because that subclass
 * applies JetBrains branding and FR-002 forbids presenting this product as a JetBrains product.
 *
 * It lives in its own module so that adding it needs no change to an upstream file. The thin client
 * does the same from `build/thin-client`, and PyCharm from `python/build`. Constraint C2 keeps the
 * count of changed upstream files low, and this product adds none: the only upstream file involved
 * is `build/dev-build.json`, which this fork already changed for the client.
 *
 * The host runs in [ProductMode.BACKEND]. It indexes, analyses and executes; it renders nothing,
 * because the thin client builds the user interface from the state this sends. See research
 * decision D2.
 */
class BackendProperties(communityHome: Path) : ProductProperties() {
  /**
   * Also the launcher name. `HostProvisioner.startCommand` runs `<remoteDirectory>/bin/remote-backend`
   * and `RemoteBackendApplicationInfo.xml` names the same script. All three have to agree or
   * provisioning fails at the START step with a file-not-found that names none of them.
   */
  override val baseFileName: String = "remote-backend"

  init {
    platformPrefix = "RemoteBackend"
    applicationInfoModule = "intellij.platform.remoteDev.backend"
    imagesDirectoryPath = communityHome.resolve("thin-client-images")

    productMode = ProductMode.BACKEND
    // The loader reads META-INF/<root module>/product-modules.xml from the root module's sources.
    // Pointing this at the upstream intellij.platform.backend.main would mean adding a resource to
    // an upstream module. Our own module is the root instead, and its product-modules.xml lists the
    // platform backend content.
    rootModuleForModularLoader = "intellij.platform.remoteDev.backend"

    // A modular-loader product declares its content in product-modules.xml, so this stays empty.
    productLayout.bundledPluginModules = persistentListOf()
    productLayout.skipUnresolvedContentModules = true

    scrambleMainJar = false
    buildCrossPlatformDistribution = false

    // The same reasoning as the client's, in ThinClientProperties. A host and a client that
    // disagreed about plugin compatibility would refuse each other's slices.
    customCompatibleBuildRange = CompatibleBuildRange.NEWER_WITH_SAME_BASELINE

    // Without this there is no lib/nio-fs.jar, and the launcher's own
    // -Xbootclasspath/a:$IDE_HOME/lib/nio-fs.jar points at nothing. The product then dies in
    // System.initPhase3 with ClassNotFoundException MultiRoutingFileSystemProvider, before any of
    // its own code runs.
    //
    // JetBrainsProductProperties does this in its init. This fork does not extend that class,
    // because it also applies JetBrains branding that FR-002 forbids. The lesson is that the class
    // mixes branding with platform wiring, so declining the branding silently declined the wiring
    // too. A distribution that builds is not a distribution that starts, and only launching it
    // tells the difference.
    productLayout.addPlatformSpec { layout, _ -> layout.withModule(IJENT_BOOT_CLASSPATH_MODULE, PLATFORM_CORE_NIO_FS) }

    // com.intellij.idea.MainImpl lives in intellij.platform.starter, and
    // CommunityModuleSets.essential() does not carry it: a product normally gets the starter from
    // its own base fragment, the way IdeaCommunityProperties does through
    // intellijCommunityBaseFragment. Without it the product starts and dies with
    // ClassNotFoundException com.intellij.idea.MainImpl.
    //
    // It goes in the platform layout and not in getProductContentDescriptor(). A content module has
    // to carry a descriptor XML named after itself, and the starter has none, so listing it there
    // fails the build with "Cannot find file intellij.platform.starter.xml in module
    // intellij.platform.starter". It is platform code, not a content module.
    productLayout.addPlatformSpec { layout, _ -> layout.withModule("intellij.platform.starter") }

    // ModuleBasedProductLoadingStrategy defaults the core plugin descriptor module to
    // "intellij.frontend.split.customization", which is a JetBrains Client module and is not in
    // Community. Without this override the product starts, reads its module descriptors, and dies
    // with "The core plugin header is not found ... by module intellij.frontend.split.customization".
    //
    // The module named here is the one carrying this product's plugin descriptor, so it has to stay
    // in step with rootModuleForModularLoader above.
    additionalVmOptions = additionalVmOptions.add("-Dintellij.platform.core.plugin.descriptor.module=intellij.platform.remoteDev.backend")

  }

  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "remoteBackend-$buildNumber"

  /** The default is the product's full name in lower case, which carries spaces. A build writes here. */
  override fun getOutputDirectoryName(appInfo: ApplicationInfoProperties): String = "remote-backend"

  /**
   * Keeps the host's configuration apart from a locally installed IDE's.
   *
   * FR-002 forbids the JetBrains name here too: the selector becomes a directory under the user's
   * home on the host.
   */
  override fun getSystemSelector(appInfo: ApplicationInfoProperties, buildNumber: String): String =
    "RemoteBackend${appInfo.majorVersion}.${appInfo.minorVersionMainPart}"

  override fun getProductContentDescriptor(): ProductModulesContentSpec = productModules {
    moduleSet(CommunityModuleSets.essential())
    // Without these the product is the platform backend with none of this fork's code in it, and a
    // green build hides that. The client's build target learned this the same way.
    module("intellij.platform.remoteDev.backend")
    module("intellij.platform.remoteDev.protocol")

    // intellij.platform.backend.main is deliberately NOT listed. It is an aggregator whose entries
    // are all RUNTIME scope, and adding it took the content set from 20 modules to 283 and made the
    // build exit 0 after "Generated 20 content blocks" having written no distribution at all. A
    // build that reports success and produces nothing is worse than one that fails, so this stays
    // out until someone understands why. product-modules.xml still names it as a main root module,
    // which is what decides loading; this list decides packaging.
  }

  /**
   * A host is a directory that provisioning unpacks and runs, not an installed application, so each
   * customizer only has to name the root directory predictably.
   */
  override fun createLinuxCustomizer(projectHome: Path): LinuxDistributionCustomizer =
    linuxCustomizer(projectHome) {
      rootDirectoryName { _, _ -> "remote-backend" }
    }

  override fun createWindowsCustomizer(projectHome: Path): WindowsDistributionCustomizer =
    windowsCustomizer(projectHome) {
      fullName { "Remote Development Backend" }
      installDirNameHandler { "Remote Development Backend" }
    }

  override fun createMacCustomizer(projectHome: Path): MacDistributionCustomizer =
    macCustomizer(projectHome) {
      bundleIdentifier = "org.intellij.community.remotedev.backend"
      rootDirectoryName { _, _ -> "Remote Development Backend.app" }
    }
}
