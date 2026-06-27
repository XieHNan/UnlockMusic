package com.unlockmusic.android.model

import android.net.Uri

data class MusicFile(
    val name: String,
    val path: String,
    val size: Long = 0,
    var displayName: String = name,
    var status: String = "待解密",
    var outputPath: String? = null,
    var outputUri: Uri? = null,
    var isDecrypted: Boolean = false,
    var decryptedData: ByteArray? = null,
    var decryptedExt: String = ""
) {
    fun reset() {
        status = "待解密"
        isDecrypted = false
        outputPath = null
        outputUri = null
        decryptedData = null
        decryptedExt = ""
    }
}
