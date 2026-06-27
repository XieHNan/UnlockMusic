package com.unlockmusic.android.decrypt

import com.unlockmusic.android.util.AudioUtils
import java.nio.charset.StandardCharsets

object QmcDecrypt {

    fun decrypt(fileData: ByteArray, filename: String): DecryptionResult {
        try {
            val ext = AudioUtils.getFileExtension(filename)
            var cipherType = checkType(fileData, ext)

            if (cipherType == "invalid" || cipherType == "STag") {
                return DecryptionResult.fail("文件无效或不支持此格式")
            }

            var tailSize = 0
            var keySize = 0
            var rawKey: ByteArray? = null

            when (cipherType) {
                "QTag" -> {
                    tailSize = 8
                    keySize = AudioUtils.readInt32BE(fileData, fileData.size - 8)
                    rawKey = fileData.copyOfRange(fileData.size - (tailSize + keySize), fileData.size - tailSize)
                    val derivedKey = deriveKeyFromQTag(rawKey)
                        ?: return DecryptionResult.fail("无法解析 QTag 密钥")
                    rawKey = derivedKey
                    cipherType = "Map/RC4"
                }
                "Map/RC4" -> {
                    tailSize = 4
                    keySize = AudioUtils.readInt32LE(fileData, fileData.size - 4)
                    if (keySize >= 0x400) {
                        keySize = 0
                        cipherType = "Static"
                    } else {
                        rawKey = fileData.copyOfRange(fileData.size - (tailSize + keySize), fileData.size - tailSize)
                        rawKey = QmcCipher.deriveKey(rawKey)
                            ?: return DecryptionResult.fail("无法解密嵌入密钥")
                    }
                }
                "cache" -> return decryptCache(fileData, filename)
                "ios" -> return decryptTm(fileData, filename)
                else -> cipherType = "Static"
            }

            val totalTail = keySize + tailSize
            val audioData = fileData.copyOfRange(0, fileData.size - totalTail)

            when (cipherType) {
                "Static" -> QmcCipher.StaticCipher().decrypt(audioData, 0)
                "Map/RC4" -> {
                    val key = rawKey!!
                    if (key.size > 300) {
                        QmcCipher.RC4Cipher(key).decrypt(audioData, 0)
                    } else {
                        QmcCipher.MapCipher(key).decrypt(audioData, 0)
                    }
                }
            }

            val outExt = AudioUtils.detectAudioExtension(audioData)
            val outName = AudioUtils.removeExtension(filename) + ".$outExt"

            return DecryptionResult.ok(audioData, outName, outExt)
        } catch (e: Exception) {
            return DecryptionResult.fail("QMC解密失败: ${e.message}")
        }
    }

    private fun checkType(data: ByteArray, ext: String): String {
        val lowerExt = ext.lowercase()
        if (lowerExt.startsWith("qmc") || lowerExt.startsWith("m")) {
            val tag = String(data, data.size - 4, 4, StandardCharsets.US_ASCII)
            if (tag == "QTag") return "QTag"
            if (tag == "STag") return "STag"
            val keySize = AudioUtils.readInt32LE(data, data.size - 4)
            return if (keySize < 0x400) "Map/RC4" else "Static"
        }
        if (lowerExt == "cache") return "cache"
        if (lowerExt == "tm") return "ios"
        return "invalid"
    }

    private fun deriveKeyFromQTag(rawKey: ByteArray): ByteArray? {
        val keyStr = String(rawKey, StandardCharsets.US_ASCII)
        val parts = keyStr.split(",", limit = 3)
        if (parts.size < 3) return null
        val keyData = parts[0].toByteArray(StandardCharsets.US_ASCII)
        return QmcCipher.deriveKey(keyData)
    }

    private fun decryptCache(fileData: ByteArray, filename: String): DecryptionResult {
        for (i in fileData.indices) {
            var b = (fileData[i].toInt() xor 0xf4) and 0xFF
            fileData[i] = (((b and 0x3F) shl 2) or (b ushr 6)).toByte()
        }
        val outExt = AudioUtils.detectAudioExtension(fileData)
        val outName = AudioUtils.removeExtension(filename) + ".$outExt"
        return DecryptionResult.ok(fileData, outName, outExt)
    }

    private fun decryptTm(fileData: ByteArray, filename: String): DecryptionResult {
        for (i in fileData.indices) {
            fileData[i] = (fileData[i].toInt() xor 0xff).toByte()
        }
        val outExt = AudioUtils.detectAudioExtension(fileData)
        val outName = AudioUtils.removeExtension(filename) + ".$outExt"
        return DecryptionResult.ok(fileData, outName, outExt)
    }
}
