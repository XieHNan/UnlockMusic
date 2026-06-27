package com.unlockmusic.android.decrypt

import com.unlockmusic.android.util.AudioUtils
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

object KwmDecrypt {

    private val MAGIC_HEADER = byteArrayOf(
        0x79, 0x65, 0x65, 0x6C, 0x69, 0x6F, 0x6E, 0x2D,
        0x6B, 0x75, 0x77, 0x6F, 0x2D, 0x74, 0x6D, 0x65
    ) // "yeelion-kuwo-tme"

    private val MAGIC_HEADER2 = byteArrayOf(
        0x79, 0x65, 0x65, 0x6C, 0x69, 0x6F, 0x6E, 0x2D,
        0x6B, 0x75, 0x77, 0x6F, 0x00, 0x00, 0x00, 0x00
    ) // "yeelion-kuwo\0\0\0\0"

    private const val PRE_DEFINED_KEY = "MoOtOiTvINGwd2E6n0E1i7L5t2IoOoNk"

    fun decrypt(fileData: ByteArray, filename: String): DecryptionResult {
        try {
            if (!AudioUtils.hasPrefix(fileData, MAGIC_HEADER) &&
                !AudioUtils.hasPrefix(fileData, MAGIC_HEADER2)
            ) {
                if (isAac(fileData)) return decryptRawAac(fileData, filename)
                return DecryptionResult.fail("不是有效的 KWM 文件")
            }

            val fileKey = fileData.copyOfRange(0x18, 0x20)
            val mask = createMaskFromKey(fileKey)

            val audioData = fileData.copyOfRange(0x400, fileData.size)
            for (i in audioData.indices) {
                audioData[i] = (audioData[i].toInt() xor mask[i % 0x20].toInt()).toByte()
            }

            val ext = AudioUtils.detectAudioExtension(audioData)
            val outName = AudioUtils.removeExtension(filename) + ".$ext"

            return DecryptionResult.ok(audioData, outName, ext)
        } catch (e: Exception) {
            return DecryptionResult.fail("KWM解密失败: ${e.message}")
        }
    }

    private fun createMaskFromKey(keyBytes: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(keyBytes).order(ByteOrder.LITTLE_ENDIAN)
        val keyLong = buf.getLong()
        val keyBig = if (keyLong < 0) {
            val unsigned = ByteArray(9)
            unsigned[0] = 0
            System.arraycopy(keyBytes, 0, unsigned, 1, 8)
            BigInteger(unsigned)
        } else {
            BigInteger.valueOf(keyLong)
        }
        val keyStr = keyBig.toString()
        val keyStrTrim = trimKey(keyStr)

        return ByteArray(32) { i ->
            (PRE_DEFINED_KEY[i].code xor keyStrTrim[i].code).toByte()
        }
    }

    private fun trimKey(keyRaw: String): String {
        return when {
            keyRaw.length > 32 -> keyRaw.substring(0, 32)
            keyRaw.length < 32 -> {
                val sb = StringBuilder(keyRaw)
                while (sb.length < 32) sb.append(keyRaw)
                sb.substring(0, 32)
            }
            else -> keyRaw
        }
    }

    private fun isAac(data: ByteArray): Boolean =
        data.size >= 2 && (data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xF0) == 0xF0

    private fun decryptRawAac(fileData: ByteArray, filename: String): DecryptionResult {
        val outName = AudioUtils.removeExtension(filename) + ".aac"
        return DecryptionResult.ok(fileData, outName, "aac")
    }
}
