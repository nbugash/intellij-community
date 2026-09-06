// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

/** One third-party component as the build reports it in `*-third-party-libraries.json`. */
data class LibraryLicense(val name: String, val version: String?, val license: String)

/** What a validation run found. */
data class LicenseReport(val checked: Int, val unrecognised: List<LibraryLicense>) {
  val isClean: Boolean get() = unrecognised.isEmpty()

  val summary: String
    get() = if (isClean) "Checked $checked component(s). Every licence is on the allowed list."
    else "Checked $checked component(s). ${unrecognised.size} carry a licence that needs a decision: " +
         unrecognised.joinToString(", ") { "${it.name} (${it.license})" } + "."
}

/**
 * Checks that every distributed component carries a licence this fork may ship.
 *
 * SC-014 asks for this on every build, and constitution Verification Standards say compliance must
 * not rest on review. The list is an allow list rather than a deny list on purpose. A deny list
 * passes anything nobody thought to forbid, and the failure mode of that is shipping a component
 * this fork has no right to distribute. An unrecognised licence is therefore reported for a human
 * decision, not assumed to be safe.
 */
object LicenseValidator {
  /**
   * Licences this fork may distribute in a binary.
   *
   * The permissive ones need no argument. The weak-copyleft ones, EPL, LGPL, CDDL and GPL with the
   * Classpath exception, are file-level or linking-level and are satisfied by shipping the component
   * as its own artifact, which is what a distribution does.
   */
  val ALLOWED: Set<String> = setOf(
    "Apache 2.0",
    "BSD 2-Clause",
    "BSD 3-Clause",
    "MIT",
    "OFL",
    "Public Domain (CC0)",
    "Unicode",
    "EPL 1.0",
    "EPL 2.0",
    "LGPL 2.1",
    "CDDL 1.1",
    "GPL 2.0 + Classpath",
    // Added after the first real run against a distribution reported them. Each is permissive or
    // file-level copyleft, so each is satisfied by shipping the component as its own artifact.
    "Creative Commons 2.5 Attribution",
    "JDOM License",
    "zlib/libpng",
    "MPL 2.0",
    "codehaus",
  )

  fun validate(components: List<LibraryLicense>): LicenseReport =
    LicenseReport(components.size, components.filterNot { it.license.trim() in ALLOWED })
}
