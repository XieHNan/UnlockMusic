package com.unlockmusic.android.decrypt

/** 解密结果封装 */
data class DecryptionResult(
    val success: Boolean,
    val message: String = "",
    val data: ByteArray? = null,
    val filename: String = "",
    val ext: String = ""
) {
    companion object {
        fun fail(message: String) = DecryptionResult(success = false, message = message)
        fun ok(data: ByteArray, filename: String, ext: String) =
            DecryptionResult(success = true, data = data, filename = filename, ext = ext)
    }

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}
