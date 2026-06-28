package com.unlockmusic.android.decrypt

import android.util.Base64
import com.unlockmusic.android.util.AudioUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Arrays

object QmcCipher {

    private val V2_KEY_PREFIX = byteArrayOf(
        0x51, 0x51, 0x4D, 0x75, 0x73, 0x69, 0x63, 0x20,
        0x45, 0x6E, 0x63, 0x56, 0x32, 0x2C, 0x4B, 0x65, 0x79, 0x3A
    ) // "QQMusic EncV2,Key:"

    private val MIX_KEY_1 = byteArrayOf(
        0x33, 0x38, 0x36, 0x5A, 0x4A, 0x59, 0x21, 0x40,
        0x23, 0x2A, 0x24, 0x25, 0x5E, 0x26, 0x29, 0x28
    )

    private val MIX_KEY_2 = byteArrayOf(
        0x2A, 0x2A, 0x23, 0x21, 0x28, 0x23, 0x24, 0x25,
        0x26, 0x5E, 0x61, 0x31, 0x63, 0x5A, 0x2C, 0x54
    )

    fun deriveKey(raw: ByteArray): ByteArray? {
        try {
            // Step 1: Base64 decode
            val rawDec = base64Decode(raw) ?: return null
            if (rawDec.size < 16) return null

            // Step 2: Try decryptV2Key
            val decV2 = decryptV2Key(rawDec)
            val keyData = decV2 ?: rawDec

            // Step 3: simpleMakeKey(106, 8)
            val simpleKey = simpleMakeKey(106, 8)

            // Step 4: Interleave simpleKey and keyData to make teaKey
            val teaKey = ByteArray(16)
            for (i in 0 until 8) {
                teaKey[i shl 1] = simpleKey[i]
                teaKey[(i shl 1) + 1] = keyData[i]
            }

            // Step 5: TEA decrypt the rest
            val rest = keyData.copyOfRange(8, keyData.size)
            val teaDecrypted = teaDecrypt(rest, teaKey) ?: return null

            // Combine: first 8 bytes + TEA decrypted
            return keyData.copyOf(8) + teaDecrypted
        } catch (e: Exception) {
            return null
        }
    }

    private fun decryptV2Key(key: ByteArray): ByteArray? {
        if (!AudioUtils.hasPrefix(key, V2_KEY_PREFIX)) return null

        val base64Part = key.copyOfRange(V2_KEY_PREFIX.size, key.size)
        val dec1 = teaDecrypt(base64Part, MIX_KEY_1) ?: return null
        val dec2 = teaDecrypt(dec1, MIX_KEY_2) ?: return null
        return base64Decode(dec2)
    }

    private fun simpleMakeKey(salt: Int, length: Int): ByteArray {
        val key = ByteArray(length)
        for (i in 0 until length) {
            val tmp = Math.tan((salt + i * 0.1).toDouble())
            key[i] = (0xFF and (Math.abs(tmp) * 100.0).toInt()).toByte()
        }
        return key
    }

    private fun teaDecrypt(data: ByteArray, key: ByteArray): ByteArray? {
        val paddedLen = ((data.size + 7) / 8) * 8
        val padded = ByteArray(paddedLen)
        System.arraycopy(data, 0, padded, 0, data.size)

        val tea = TeaCipher(key, 64)
        val dstBuf = ByteArray(8)

        for (offset in 0 until paddedLen step 8) {
            val buf = ByteBuffer.wrap(padded, offset, 8).order(ByteOrder.BIG_ENDIAN)
            tea.decrypt(dstBuf, buf)
        }

        // Find actual length (trim trailing zeros)
        var actualLen = paddedLen
        while (actualLen > 0 && padded[actualLen - 1] == 0.toByte()) {
            actualLen--
        }

        return padded.copyOf(actualLen)
    }

    private fun base64Decode(data: ByteArray): ByteArray? {
        val sb = StringBuilder()
        for (b in data) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '=') {
                sb.append(c)
            }
        }
        return try {
            Base64.decode(sb.toString(), Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    // ===== Static Cipher =====
    class StaticCipher {
        companion object {
            private val STATIC_CIPHER_BOX = intArrayOf(
                0x77, 0x48, 0x32, 0x73, 0xDE, 0xF2, 0xC0, 0xC8,
                0x95, 0xEC, 0x30, 0xB2, 0x51, 0xC3, 0xE1, 0xA0,
                0x9E, 0xE6, 0x9D, 0xCF, 0xFA, 0x7F, 0x14, 0xD1,
                0xCE, 0xB8, 0xDC, 0xC3, 0x4A, 0x67, 0x93, 0xD6,
                0x28, 0xC2, 0x91, 0x70, 0xCA, 0x8D, 0xA2, 0xA4,
                0xF0, 0x08, 0x61, 0x90, 0x7E, 0x6F, 0xA2, 0xE0,
                0xEB, 0xAE, 0x3E, 0xB6, 0x67, 0xC7, 0x92, 0xF4,
                0x91, 0xB5, 0xF6, 0x6C, 0x5E, 0x84, 0x40, 0xF7,
                0xF3, 0x1B, 0x02, 0x7F, 0xD5, 0xAB, 0x41, 0x89,
                0x28, 0xF4, 0x25, 0xCC, 0x52, 0x11, 0xAD, 0x43,
                0x68, 0xA6, 0x41, 0x8B, 0x84, 0xB5, 0xFF, 0x2C,
                0x92, 0x4A, 0x26, 0xD8, 0x47, 0x6A, 0x7C, 0x95,
                0x61, 0xCC, 0xE6, 0xCB, 0xBB, 0x3F, 0x47, 0x58,
                0x89, 0x75, 0xC3, 0x75, 0xA1, 0xD9, 0xAF, 0xCC,
                0x08, 0x73, 0x17, 0xDC, 0xAA, 0x9A, 0xA2, 0x16,
                0x41, 0xD8, 0xA2, 0x06, 0xC6, 0x8B, 0xFC, 0x66,
                0x34, 0x9F, 0xCF, 0x18, 0x23, 0xA0, 0x0A, 0x74,
                0xE7, 0x2B, 0x27, 0x70, 0x92, 0xE9, 0xAF, 0x37,
                0xE6, 0x8C, 0xA7, 0xBC, 0x62, 0x65, 0x9C, 0xC2,
                0x08, 0xC9, 0x88, 0xB3, 0xF3, 0x43, 0xAC, 0x74,
                0x2C, 0x0F, 0xD4, 0xAF, 0xA1, 0xC3, 0x01, 0x64,
                0x95, 0x4E, 0x48, 0x9F, 0xF4, 0x35, 0x78, 0x95,
                0x7A, 0x39, 0xD6, 0x6A, 0xA0, 0x6D, 0x40, 0xE8,
                0x4F, 0xA8, 0xEF, 0x11, 0x1D, 0xF3, 0x1B, 0x3F,
                0x3F, 0x07, 0xDD, 0x6F, 0x5B, 0x19, 0x30, 0x19,
                0xFB, 0xEF, 0x0E, 0x37, 0xF0, 0x0E, 0xCD, 0x16,
                0x49, 0xFE, 0x53, 0x47, 0x13, 0x1A, 0xBD, 0xA4,
                0xF1, 0x40, 0x19, 0x60, 0x0E, 0xED, 0x68, 0x09,
                0x06, 0x5F, 0x4D, 0xCF, 0x3D, 0x1A, 0xFE, 0x20,
                0x77, 0xE4, 0xD9, 0xDA, 0xF9, 0xA4, 0x2B, 0x76,
                0x1C, 0x71, 0xDB, 0x00, 0xBC, 0xFD, 0x0C, 0x6C,
                0xA5, 0x47, 0xF7, 0xF6, 0x00, 0x79, 0x4A, 0x11
            )
        }

        private fun getMask(offset: Long): Int {
            var off = offset
            if (off > 0x7fff) off %= 0x7fff
            return STATIC_CIPHER_BOX[((off * off + 27) and 0xff).toInt()]
        }

        fun decrypt(buf: ByteArray, offset: Int) {
            for (i in buf.indices) {
                buf[i] = (buf[i].toInt() xor getMask((offset + i).toLong())).toByte()
            }
        }
    }

    // ===== Map Cipher =====
    class MapCipher(private val key: ByteArray) {
        private fun rotate(value: Int, bits: Int): Int {
            val rotateBits = (bits + 4) % 8
            val left = value shl rotateBits
            val right = value ushr rotateBits
            return (left or right) and 0xff
        }

        private fun getMask(offset: Long): Int {
            var off = offset
            if (off > 0x7fff) off %= 0x7fff
            val idx = ((off * off + 71214) % key.size).toInt()
            return rotate(key[idx].toInt() and 0xFF, idx and 0x7)
        }

        fun decrypt(buf: ByteArray, offset: Int) {
            for (i in buf.indices) {
                buf[i] = (buf[i].toInt() xor getMask((offset + i).toLong())).toByte()
            }
        }
    }

    // ===== RC4 Cipher =====
    class RC4Cipher(private val key: ByteArray) {
        companion object {
            private const val FIRST_SEGMENT_SIZE = 0x80
            private const val SEGMENT_SIZE = 5120
        }

        private val S: ByteArray
        private var hash: Long = 1

        init {
            S = ByteArray(key.size)
            for (i in key.indices) {
                S[i] = (i and 0xff).toByte()
            }
            var j = 0
            for (i in key.indices) {
                j = ((S[i].toInt() and 0xFF) + j + (key[i % key.size].toInt() and 0xFF)) % key.size
                val temp = S[i]
                S[i] = S[j]
                S[j] = temp
            }

            hash = 1
            for (i in key.indices) {
                val value = key[i].toInt() and 0xFF
                if (value == 0) continue
                val nextHash = hash * value
                if (nextHash == 0L || nextHash <= hash) break
                hash = nextHash
            }
        }

        private fun getSegmentKey(id: Int): Long {
            val seed = key[id % key.size].toInt() and 0xFF
            val actualSeed = if (seed == 0) 1 else seed
            val idx = (hash.toDouble() / ((id + 1) * actualSeed.toLong()) * 100.0).toLong()
            return idx % key.size
        }

        private fun procFirstSegment(buf: ByteArray, offset: Int) {
            for (i in buf.indices) {
                buf[i] = (buf[i].toInt() xor key[getSegmentKey(offset + i).toInt()].toInt()).toByte()
            }
        }

        private fun procASegment(buf: ByteArray, offset: Int) {
            val nS = S.copyOf()

            val skipLen = (offset % SEGMENT_SIZE) + getSegmentKey(offset / SEGMENT_SIZE)

            var j = 0
            var k = 0
            var i = (-skipLen).toInt()
            while (i < buf.size) {
                j = (j + 1) % key.size
                k = ((nS[j].toInt() and 0xFF) + k) % key.size
                val temp = nS[k]
                nS[k] = nS[j]
                nS[j] = temp

                if (i >= 0) {
                    buf[i] = (buf[i].toInt() xor nS[((nS[j].toInt() and 0xFF) + (nS[k].toInt() and 0xFF)) % key.size].toInt()).toByte()
                }
                i++
            }
        }

        fun decrypt(buf: ByteArray, offset: Int) {
            var toProcess = buf.size
            var processed = 0
            var currentOffset = offset

            // First segment
            if (currentOffset < FIRST_SEGMENT_SIZE) {
                val len = minOf(FIRST_SEGMENT_SIZE - currentOffset, toProcess)
                val tmp = buf.copyOfRange(processed, processed + len)
                procFirstSegment(tmp, currentOffset)
                System.arraycopy(tmp, 0, buf, processed, len)
                toProcess -= len
                processed += len
                currentOffset += len
                if (toProcess == 0) return
            }

            // Align to segment boundary
            if (currentOffset % SEGMENT_SIZE != 0) {
                val len = minOf(SEGMENT_SIZE - (currentOffset % SEGMENT_SIZE), toProcess)
                val tmp = buf.copyOfRange(processed, processed + len)
                procASegment(tmp, currentOffset)
                System.arraycopy(tmp, 0, buf, processed, len)
                toProcess -= len
                processed += len
                currentOffset += len
                if (toProcess == 0) return
            }

            // Full segments
            while (toProcess > SEGMENT_SIZE) {
                val tmp = buf.copyOfRange(processed, processed + SEGMENT_SIZE)
                procASegment(tmp, currentOffset)
                System.arraycopy(tmp, 0, buf, processed, SEGMENT_SIZE)
                toProcess -= SEGMENT_SIZE
                processed += SEGMENT_SIZE
                currentOffset += SEGMENT_SIZE
            }

            // Remaining
            if (toProcess > 0) {
                val tmp = buf.copyOfRange(processed, processed + toProcess)
                procASegment(tmp, currentOffset)
                System.arraycopy(tmp, 0, buf, processed, toProcess)
            }
        }
    }
}
