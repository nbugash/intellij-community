// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.protocol

import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** A frame could not be read or written as contract section 2 requires. */
@ApiStatus.Internal
class SessionFramingException(message: String) : IOException(message)

/**
 * Length-prefixed framing for the session stream, per contract section 2.
 *
 * Each frame is a four byte big-endian length followed by that many payload bytes. The payload is
 * the serialized message; this object does not interpret it.
 *
 * The size limit is checked before any buffer is allocated. A peer that declares a huge frame would
 * otherwise make the host allocate it, which is a denial of service that costs the attacker four
 * bytes.
 */
@ApiStatus.Internal
object SessionFraming {
  /** The largest payload a single frame may carry. */
  const val MAX_FRAME_BYTES: Int = 16 * 1024 * 1024

  private const val LENGTH_BYTES: Int = 4
  private const val BYTE_MASK: Int = 0xFF

  fun writeFrame(out: OutputStream, payload: ByteArray) {
    if (payload.size > MAX_FRAME_BYTES) {
      throw SessionFramingException("A frame of ${payload.size} bytes exceeds the limit of $MAX_FRAME_BYTES")
    }
    out.write(encodeLength(payload.size))
    out.write(payload)
    out.flush()
  }

  /** Reads one frame. Returns null at a clean end of stream, meaning the peer closed between frames. */
  fun readFrame(input: InputStream): ByteArray? {
    val prefix = readExactly(input, LENGTH_BYTES) ?: return null
    val length = decodeLength(prefix)
    if (length < 0) {
      throw SessionFramingException("A frame declared a negative length of $length")
    }
    if (length > MAX_FRAME_BYTES) {
      throw SessionFramingException("A frame declared $length bytes, above the limit of $MAX_FRAME_BYTES")
    }
    return readExactly(input, length) ?: throw SessionFramingException("The stream ended inside a frame payload")
  }

  /** Returns null only when the stream ended cleanly before any byte of this read. */
  private fun readExactly(input: InputStream, count: Int): ByteArray? {
    val buffer = ByteArray(count)
    var filled = 0
    while (filled < count) {
      val read = input.read(buffer, filled, count - filled)
      if (read < 0) {
        return if (filled == 0) null else throw SessionFramingException("The stream ended after $filled of $count bytes")
      }
      filled += read
    }
    return buffer
  }

  private fun encodeLength(length: Int): ByteArray = byteArrayOf(
    (length ushr 24).toByte(),
    (length ushr 16).toByte(),
    (length ushr 8).toByte(),
    length.toByte(),
  )

  private fun decodeLength(prefix: ByteArray): Int =
    (prefix[0].toInt() and BYTE_MASK shl 24) or
    (prefix[1].toInt() and BYTE_MASK shl 16) or
    (prefix[2].toInt() and BYTE_MASK shl 8) or
    (prefix[3].toInt() and BYTE_MASK)
}
