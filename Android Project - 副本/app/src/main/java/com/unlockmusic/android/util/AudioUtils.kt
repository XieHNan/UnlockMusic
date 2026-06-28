package com.unlockmusic.android.util

/**
 * 音频工具类 - 提取自各解密类中的重复方法
 * 包含：音频格式检测、文件扩展名操作、字节操作等
 */
object AudioUtils {

    // ==================== 文件名操作 ====================

    /** 获取文件扩展名（不含点号，小写） */
    fun getFileExtension(filename: String): String {
        val lastDot = filename.lastIndexOf('.')
        if (lastDot == -1) return ""
        return filename.substring(lastDot + 1).lowercase()
    }

    /** 移除文件扩展名 */
    fun removeExtension(filename: String): String {
        val lastDot = filename.lastIndexOf('.')
        if (lastDot == -1) return filename
        return filename.substring(0, lastDot)
    }

    // ==================== 音频格式检测 ====================

    /** 根据文件头魔数检测音频格式扩展名 */
    fun detectAudioExtension(data: ByteArray): String {
        if (data.size < 4) return "mp3"

        // FLAC: fLaC
        if (data[0] == 0x66.toByte() && data[1] == 0x4C.toByte() &&
            data[2] == 0x61.toByte() && data[3] == 0x43.toByte()
        ) return "flac"

        // OGG: OggS
        if (data[0] == 0x4F.toByte() && data[1] == 0x67.toByte() &&
            data[2] == 0x67.toByte() && data[3] == 0x53.toByte()
        ) return "ogg"

        // MP3: ID3 tag
        if (data[0] == 0x49.toByte() && data[1] == 0x44.toByte() && data[2] == 0x33.toByte()
        ) return "mp3"

        // MP3: sync word
        if ((data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xE0) == 0xE0
        ) return "mp3"

        // WAV: RIFF
        if (data[0] == 0x52.toByte() && data[1] == 0x49.toByte() &&
            data[2] == 0x46.toByte() && data[3] == 0x46.toByte()
        ) return "wav"

        // M4A: ftyp at offset 4
        if (data.size >= 8 &&
            data[4] == 0x66.toByte() && data[5] == 0x74.toByte() &&
            data[6] == 0x79.toByte() && data[7] == 0x70.toByte()
        ) return "m4a"

        // AAC: ADTS sync
        if ((data[0].toInt() and 0xFF) == 0xFF && (data[1].toInt() and 0xF0) == 0xF0
        ) return "aac"

        return "mp3"
    }

    /** 获取 MIME 类型 */
    fun getMimeType(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "wma" -> "audio/x-ms-wma"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        else -> "audio/*"
    }

    // ==================== 字节操作 ====================

    /** 读取小端序 Int32 */
    fun readInt32LE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)

    /** 读取大端序 Int32 */
    fun readInt32BE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

    /** 检查字节数组是否有指定前缀 */
    fun hasPrefix(data: ByteArray, prefix: ByteArray, offset: Int = 0): Boolean {
        if (data.size < offset + prefix.size) return false
        for (i in prefix.indices) {
            if (data[offset + i] != prefix[i]) return false
        }
        return true
    }

    /** 十六进制字符串转字节数组 */
    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
        }
        return data
    }

    /** 格式化时间（毫秒 → mm:ss） */
    fun formatTime(millis: Int): String {
        val totalSeconds = millis / 1000
        val min = totalSeconds / 60
        val sec = totalSeconds % 60
        return String.format("%02d:%02d", min, sec)
    }
}
