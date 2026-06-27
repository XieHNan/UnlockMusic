package com.unlockmusic.android.decrypt

import com.unlockmusic.android.util.AudioUtils
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object NcmDecrypt {

    private val MAGIC_HEADER = byteArrayOf(0x43, 0x54, 0x45, 0x4E, 0x46, 0x44, 0x41, 0x4D) // CTENFDAM
    private val CORE_KEY = AudioUtils.hexToBytes("687a4852416d736f356b496e62617857")
    private val META_KEY = AudioUtils.hexToBytes("2331346c6a6b5f215c5d2630553c2728")

    fun decrypt(fileData: ByteArray, filename: String): DecryptionResult {
        if (!AudioUtils.hasPrefix(fileData, MAGIC_HEADER)) {
            return DecryptionResult.fail("不是有效的 NCM 文件")
        }

        try {
            var offset = 10 // skip magic header (8) + 2 bytes

            // Read key data
            val keyLen = AudioUtils.readInt32LE(fileData, offset)
            offset += 4

            val cipherKey = fileData.copyOfRange(offset, offset + keyLen)
            for (i in cipherKey.indices) cipherKey[i] = (cipherKey[i].toInt() xor 0x64).toByte()
            offset += keyLen

            // AES decrypt key data
            val plainKeyData = aes128EcbDecrypt(cipherKey, CORE_KEY)
            val keyData = plainKeyData.copyOfRange(17, plainKeyData.size)

            // Build keybox
            val keyBox = buildKeyBox(keyData)

            // Read metadata
            val metaLen = AudioUtils.readInt32LE(fileData, offset)
            offset += 4

            if (metaLen > 0) {
                val cipherMeta = fileData.copyOfRange(offset, offset + metaLen)
                for (i in cipherMeta.indices) cipherMeta[i] = (cipherMeta[i].toInt() xor 0x63).toByte()

                val base64Data = cipherMeta.copyOfRange(22, cipherMeta.size)
                val base64Str = String(base64Data, Charsets.UTF_8)
                val metaEncrypted = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)

                val plainMeta = aes128EcbDecrypt(metaEncrypted, META_KEY)
                // metaJson available if needed
            }

            offset += metaLen

            // Skip album cover: 5 bytes gap + 4 bytes image size + image data + 4 bytes gap
            val imageSize = AudioUtils.readInt32LE(fileData, offset + 5)
            offset += 13 + imageSize

            // Decrypt audio
            val audioData = fileData.copyOfRange(offset, fileData.size)
            for (i in audioData.indices) {
                audioData[i] = (audioData[i].toInt() xor keyBox[i and 0xFF].toInt()).toByte()
            }

            val ext = AudioUtils.detectAudioExtension(audioData)
            val outName = AudioUtils.removeExtension(filename) + ".$ext"

            return DecryptionResult.ok(audioData, outName, ext)
        } catch (e: Exception) {
            return DecryptionResult.fail("解密失败: ${e.message}")
        }
    }

    private fun buildKeyBox(keyData: ByteArray): ByteArray {
        val box = ByteArray(256) { it.toByte() }

        var j = 0
        for (i in 0 until 256) {
            j = ((box[i].toInt() and 0xFF) + j + (keyData[i % keyData.size].toInt() and 0xFF)) and 0xFF
            val tmp = box[i]
            box[i] = box[j]
            box[j] = tmp
        }

        val keyBox = ByteArray(256)
        for (i in 0 until 256) {
            val idx = (i + 1) and 0xFF
            val si = box[idx].toInt() and 0xFF
            val sj = box[(idx + si) and 0xFF].toInt() and 0xFF
            keyBox[i] = box[(si + sj) and 0xFF]
        }

        return keyBox
    }

    private fun aes128EcbDecrypt(data: ByteArray, key: ByteArray): ByteArray {
        val keySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        return cipher.doFinal(data)
    }
}
