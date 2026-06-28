package com.unlockmusic.android.decrypt

import com.unlockmusic.android.util.AudioUtils

object NcmCacheDecrypt {
    fun decrypt(fileData: ByteArray, filename: String): DecryptionResult {
        if (fileData.size < 16) {
            return DecryptionResult.fail("Invalid NCM cache file")
        }

        val firstBlock = fileData.copyOfRange(0, 16)
        val secondBlock = fileData.copyOfRange(16, 32)

        val key = ByteArray(16) { i -> (firstBlock[i].toInt() xor secondBlock[i].toInt()).toByte() }

        val result = ByteArray(fileData.size - 16)
        for (i in result.indices) {
            result[i] = (fileData[i + 16].toInt() xor key[i % 16].toInt()).toByte()
        }

        val ext = AudioUtils.detectAudioExtension(result)
        val outName = AudioUtils.removeExtension(filename) + ".$ext"
        return DecryptionResult.ok(result, outName, ext)
    }
}
