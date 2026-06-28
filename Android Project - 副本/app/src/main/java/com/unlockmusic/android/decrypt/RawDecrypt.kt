package com.unlockmusic.android.decrypt

import com.unlockmusic.android.util.AudioUtils

object RawDecrypt {
    fun decrypt(fileData: ByteArray, filename: String): DecryptionResult {
        val ext = AudioUtils.getFileExtension(filename)
        return DecryptionResult.ok(fileData, filename, ext)
    }
}
