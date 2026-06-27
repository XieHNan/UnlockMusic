package com.unlockmusic.android.decrypt

import com.unlockmusic.android.util.AudioUtils

object MusicDecryptor {

    private val NCM_EXTENSIONS = setOf("ncm")
    private val QMC_EXTENSIONS = setOf(
        "qmc0", "qmc2", "qmc3", "qmc4", "qmc6", "qmc8",
        "qmcflac", "qmcogg", "mflac", "mflac0", "mgg", "mgg0", "mggl", "mgg1", "mmp4",
        "bkcmp3", "bkcm4a", "bkcflac", "bkcwav", "bkcape", "bkcogg", "bkcwma", "tkm",
        "cache", "tm"
    )
    private val KGM_EXTENSIONS = setOf("kgm", "kgma", "vpr")
    private val KWM_EXTENSIONS = setOf("kwm")
    private val XM_EXTENSIONS = setOf("xm", "xmal")

    /** 所有加密扩展名 */
    private val ALL_ENCRYPTED_EXTS = NCM_EXTENSIONS + QMC_EXTENSIONS + KGM_EXTENSIONS + KWM_EXTENSIONS + XM_EXTENSIONS

    /** 常见音频格式（无需解密，透传） */
    private val COMMON_AUDIO_EXTS = setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "wma", "ape", "wv", "opus")

    fun decrypt(fileData: ByteArray, filename: String): DecryptionResult {
        val ext = AudioUtils.getFileExtension(filename)

        return when {
            ext in NCM_EXTENSIONS -> NcmDecrypt.decrypt(fileData, filename)
            ext in QMC_EXTENSIONS -> QmcDecrypt.decrypt(fileData, filename)
            ext in KGM_EXTENSIONS -> KgmDecrypt.decrypt(fileData, filename)
            ext in KWM_EXTENSIONS -> KwmDecrypt.decrypt(fileData, filename)
            ext in XM_EXTENSIONS -> XmDecrypt.decrypt(fileData, filename)
            // 复合扩展名：明天你好.kgm.flac → 检测到 kgm 后用 KGM 解密
            getEncryptionExt(filename) != null -> decryptByExt(fileData, filename, getEncryptionExt(filename)!!)
            // 已是常见音频格式，无需解密，直接透传
            ext in COMMON_AUDIO_EXTS -> DecryptionResult.ok(fileData, filename, ext)
            else -> DecryptionResult.fail("不支持的格式: $ext")
        }
    }

    fun isSupportedFormat(filename: String): Boolean {
        val ext = AudioUtils.getFileExtension(filename)
        return ext in NCM_EXTENSIONS ||
                ext in QMC_EXTENSIONS ||
                ext in KGM_EXTENSIONS ||
                ext in KWM_EXTENSIONS ||
                ext in XM_EXTENSIONS ||
                getEncryptionExt(filename) != null ||
                ext in COMMON_AUDIO_EXTS
    }

    /** 检测文件名中的加密扩展名（支持复合扩展名如 .kgm.flac） */
    private fun getEncryptionExt(filename: String): String? {
        val ext = AudioUtils.getFileExtension(filename)
        if (ext in ALL_ENCRYPTED_EXTS) return ext
        // 去掉最后一个扩展名再检查：明天你好.kgm.flac → 明天你好.kgm → kgm
        val base = AudioUtils.removeExtension(filename)
        val innerExt = AudioUtils.getFileExtension(base)
        if (innerExt in ALL_ENCRYPTED_EXTS) return innerExt
        return null
    }

    /** 按已知加密扩展名分发解密（复合扩展名解密后输出格式取原始最后一层扩展名） */
    private fun decryptByExt(data: ByteArray, filename: String, encExt: String): DecryptionResult {
        val lastExt = AudioUtils.getFileExtension(filename)
        // 复合扩展名：去掉最后一层传给解密器（明天你好.kgm.flac → 明天你好.kgm）
        val decFilename = if (encExt == lastExt) filename else AudioUtils.removeExtension(filename)
        val result = when {
            encExt in NCM_EXTENSIONS -> NcmDecrypt.decrypt(data, decFilename)
            encExt in QMC_EXTENSIONS -> QmcDecrypt.decrypt(data, decFilename)
            encExt in KGM_EXTENSIONS -> KgmDecrypt.decrypt(data, decFilename)
            encExt in KWM_EXTENSIONS -> KwmDecrypt.decrypt(data, decFilename)
            encExt in XM_EXTENSIONS -> XmDecrypt.decrypt(data, decFilename)
            else -> DecryptionResult.fail("不支持的格式: $encExt")
        }
        // 复合扩展名：输出格式用原始最后扩展名（明天你好.kgm.flac → 明天你好.flac）
        if (encExt != lastExt && result.success) {
            val outName = AudioUtils.removeExtension(decFilename) + ".$lastExt"
            return DecryptionResult.ok(result.data!!, outName, lastExt)
        }
        return result
    }
}
