package com.unlockmusic.android.decrypt

import com.unlockmusic.android.util.AudioUtils
import java.nio.charset.StandardCharsets

object XmDecrypt {

    private val MAGIC_HEADER = byteArrayOf(0x69, 0x66, 0x6D, 0x74) // "ifmt"
    private val MAGIC_HEADER2 = byteArrayOf(0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte(), 0xFE.toByte())

    private val FILE_TYPE_MAP = mapOf(
        " WAV" to "wav",
        "FLAC" to "flac",
        " MP3" to "mp3",
        " A4M" to "m4a"
    )

    fun decrypt(fileData: ByteArray, filename: String): DecryptionResult {
        try {
            if (!AudioUtils.hasPrefix(fileData, MAGIC_HEADER) ||
                !AudioUtils.hasPrefix(fileData, MAGIC_HEADER2, 8)
            ) {
                return DecryptionResult.fail("此xm文件已损坏")
            }

            val typeText = String(fileData, 4, 4, StandardCharsets.US_ASCII)
            val ext = FILE_TYPE_MAP[typeText]
                ?: return DecryptionResult.fail("未知的.xm文件类型")

            val key = fileData[0xf].toInt() and 0xFF
            val dataOffset = (fileData[0xc].toInt() and 0xFF) or ((fileData[0xd].toInt() and 0xFF) shl 8)

            val audioData = fileData.copyOfRange(0x10, fileData.size)
            for (i in dataOffset until audioData.size) {
                audioData[i] = (((audioData[i].toInt() and 0xFF) - key) xor 0xFF).toByte()
            }

            val outName = AudioUtils.removeExtension(filename) + ".$ext"
            return DecryptionResult.ok(audioData, outName, ext)
        } catch (e: Exception) {
            return DecryptionResult.fail("XM解密失败: ${e.message}")
        }
    }
}
