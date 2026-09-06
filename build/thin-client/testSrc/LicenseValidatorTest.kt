// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** SC-014. Every distributed component carries a licence this fork may ship. */
class LicenseValidatorTest {
  private fun lib(license: String, name: String = "thing") = LibraryLicense(name, "1.0", license)

  @Test
  fun `the licences the real distribution carries are all allowed`() {
    // These twelve are what the thin client's own third-party report contains today.
    val real = listOf(
      "Apache 2.0", "BSD 3-Clause", "MIT", "OFL", "LGPL 2.1", "EPL 2.0",
      "Public Domain (CC0)", "GPL 2.0 + Classpath", "EPL 1.0", "BSD 2-Clause", "Unicode", "CDDL 1.1",
      // The tail of the real report. A first pass took only the common licences and missed these,
      // and the verifier caught that on its first run against a distribution.
      "Creative Commons 2.5 Attribution", "JDOM License", "zlib/libpng", "MPL 2.0", "codehaus",
    ).map(::lib)

    assertTrue(LicenseValidator.validate(real).isClean, LicenseValidator.validate(real).summary)
  }

  @Test
  fun `a strong copyleft licence is reported`() {
    // Plain GPL without the Classpath exception is the case this check exists to catch.
    val report = LicenseValidator.validate(listOf(lib("GPL 3.0", name = "risky")))

    assertFalse(report.isClean)
    assertEquals("risky", report.unrecognised.single().name)
  }

  @Test
  fun `an unknown licence is reported rather than assumed safe`() {
    // An allow list, not a deny list. Anything nobody has decided about needs a human.
    assertFalse(LicenseValidator.validate(listOf(lib("Some New Licence 1.0"))).isClean)
  }

  @Test
  fun `the classpath exception is what makes that GPL entry allowed`() {
    assertTrue(LicenseValidator.ALLOWED.contains("GPL 2.0 + Classpath"))
    assertFalse(LicenseValidator.ALLOWED.contains("GPL 2.0"))
  }

  @Test
  fun `surrounding space does not hide a licence`() {
    assertTrue(LicenseValidator.validate(listOf(lib("  Apache 2.0  "))).isClean)
  }

  @Test
  fun `the report states its numbers and names the offender`() {
    val report = LicenseValidator.validate(listOf(lib("Apache 2.0"), lib("AGPL 3.0", name = "bad")))

    assertTrue(report.summary.contains("2"), report.summary)
    assertTrue(report.summary.contains("bad"), report.summary)
  }
}
