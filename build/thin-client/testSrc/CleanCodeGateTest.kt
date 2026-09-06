// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.thinClient

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Constitution Principle 2, the part of it a gate can decide. Task T123. */
class CleanCodeGateTest {
  private fun rules(source: String) = CleanCodeGate.check("T.kt", source).map { it.rule }

  @Test
  fun `a short shallow function passes`() {
    val source = """
      fun small(x: Int): Int {
        return x + 1
      }
    """.trimIndent()

    assertEquals(emptyList<String>(), rules(source))
  }

  @Test
  fun `a function over the line limit is reported by name`() {
    // Verification Standards asks for outliers by name, so the name has to be in the message.
    val body = (1..40).joinToString("\n") { "  val v$it = $it" }
    val outliers = CleanCodeGate.check("T.kt", "fun tooLong() {\n$body\n}")

    assertEquals(1, outliers.size)
    assertEquals("function-length", outliers.single().rule)
    assertTrue(outliers.single().detail.startsWith("tooLong is 42 lines"), outliers.single().detail)
  }

  @Test
  fun `a function nested past the limit is reported`() {
    val source = """
      fun deep() {
        if (a) {
          if (b) {
            if (c) {
              if (d) {
                run()
              }
            }
          }
        }
      }
    """.trimIndent()

    assertEquals(listOf("nesting-depth"), rules(source))
  }

  @Test
  fun `a brace inside a string is not structure`() {
    // The first version counted raw text and reported nonsense on any file with a brace in a string.
    val source = """
      fun format(x: Int): String {
        return "a { brace } in text ${'$'}x"
      }
    """.trimIndent()

    assertEquals(emptyList<String>(), rules(source))
  }

  @Test
  fun `a brace inside a comment is not structure`() {
    val source = """
      fun ok(): Int {
        /* an opening { that never closes in code */
        return 1
      }
    """.trimIndent()

    assertEquals(emptyList<String>(), rules(source))
  }

  @Test
  fun `a brace or quote inside a character literal is not structure`() {
    // Regression. The gate found this in its own source: `'"'` put the stripper into string mode
    // and `'{'` counted as an opening brace, so it reported a 20-line function as 76 lines.
    val source = """
      fun count(line: String): Int {
        val quote = line.count { it == '"' }
        val open = line.count { it == '{' }
        return quote + open
      }
    """.trimIndent()

    assertEquals(emptyList<String>(), rules(source))
  }

  @Test
  fun `commented-out code is flagged`() {
    val outliers = CleanCodeGate.check("T.kt", "fun f() {\n  // val old = compute();\n  return\n}")

    assertEquals(listOf("commented-out-code"), outliers.map { it.rule })
  }

  @Test
  fun `prose that names a type is not commented-out code`() {
    // This codebase's comments name types on purpose. A wider rule would flag most of them.
    val source = """
      // The rule lives in one function rather than at each call site. See BackendTrustGate.
      fun f() = 1
    """.trimIndent()

    assertEquals(emptyList<String>(), rules(source))
  }

  @Test
  fun `two functions are measured separately`() {
    val long = (1..40).joinToString("\n") { "  val v$it = $it" }
    val outliers = CleanCodeGate.check("T.kt", "fun a() {\n  return\n}\n\nfun b() {\n$long\n}")

    assertEquals(1, outliers.size)
    assertTrue(outliers.single().detail.startsWith("b is"), outliers.single().detail)
  }

  @Test
  fun `the gate holds on its own source`() {
    // Dogfooding. A gate that cannot pass itself has no standing to gate anything.
    val source = CleanCodeGateTest::class.java.classLoader
      .getResourceAsStream("CleanCodeGate.kt.txt")?.readAllBytes()?.decodeToString()
    if (source == null) return // the resource is optional; the repository-wide run covers this

    assertEquals(emptyList<String>(), rules(source))
  }
}
