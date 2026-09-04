// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Contract section 2. Length-prefixed framing over one duplex stream. */
class SessionFramingTest {
  private fun framed(vararg payloads: ByteArray): ByteArrayInputStream {
    val out = ByteArrayOutputStream()
    payloads.forEach { SessionFraming.writeFrame(out, it) }
    return ByteArrayInputStream(out.toByteArray())
  }

  @Test
  fun `a frame survives a round trip`() {
    val payload = "handshake".toByteArray()

    assertArrayEquals(payload, SessionFraming.readFrame(framed(payload)))
  }

  @Test
  fun `several frames read back in order`() {
    val input = framed("one".toByteArray(), "two".toByteArray(), "three".toByteArray())

    assertEquals("one", String(SessionFraming.readFrame(input)!!))
    assertEquals("two", String(SessionFraming.readFrame(input)!!))
    assertEquals("three", String(SessionFraming.readFrame(input)!!))
  }

  @Test
  fun `an empty payload is a valid frame`() {
    assertArrayEquals(ByteArray(0), SessionFraming.readFrame(framed(ByteArray(0))))
  }

  @Test
  fun `a clean end of stream reads as no frame`() {
    assertNull(SessionFraming.readFrame(ByteArrayInputStream(ByteArray(0))))
  }

  @Test
  fun `a truncated length prefix is refused`() {
    assertThrows(SessionFramingException::class.java) {
      SessionFraming.readFrame(ByteArrayInputStream(byteArrayOf(0, 0)))
    }
  }

  @Test
  fun `a truncated payload is refused`() {
    val out = ByteArrayOutputStream()
    SessionFraming.writeFrame(out, "abcdef".toByteArray())
    val cut = out.toByteArray().copyOf(out.toByteArray().size - 2)

    assertThrows(SessionFramingException::class.java) { SessionFraming.readFrame(ByteArrayInputStream(cut)) }
  }

  @Test
  fun `a declared length beyond the limit is refused before any allocation`() {
    // A hostile peer that declares a huge frame must not make the host allocate it.
    val hostile = ByteArrayOutputStream()
    val tooBig = SessionFraming.MAX_FRAME_BYTES + 1
    hostile.write(byteArrayOf((tooBig ushr 24).toByte(), (tooBig ushr 16).toByte(), (tooBig ushr 8).toByte(), tooBig.toByte()))

    assertThrows(SessionFramingException::class.java) { SessionFraming.readFrame(ByteArrayInputStream(hostile.toByteArray())) }
  }

  @Test
  fun `a negative declared length is refused`() {
    val hostile = byteArrayOf(-1, -1, -1, -1)

    assertThrows(SessionFramingException::class.java) { SessionFraming.readFrame(ByteArrayInputStream(hostile)) }
  }

  @Test
  fun `writing a payload beyond the limit is refused`() {
    assertThrows(SessionFramingException::class.java) {
      SessionFraming.writeFrame(ByteArrayOutputStream(), ByteArray(SessionFraming.MAX_FRAME_BYTES + 1))
    }
  }
}
