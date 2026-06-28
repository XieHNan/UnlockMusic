package com.unlockmusic.android.decrypt

import java.nio.ByteBuffer
import java.nio.ByteOrder

class TeaCipher(key: ByteArray, private val rounds: Int) {
    companion object {
        // 0x9E3779B9 超出 Int 范围，用其有符号 Int 表示
        private const val DELTA = -0x61C88647
    }

    private val k0: Int
    private val k1: Int
    private val k2: Int
    private val k3: Int

    init {
        require(key.size == 16) { "incorrect key size" }
        require(rounds % 2 == 0) { "odd number of rounds" }

        val buf = ByteBuffer.wrap(key).order(ByteOrder.BIG_ENDIAN)
        k0 = buf.getInt(0)
        k1 = buf.getInt(4)
        k2 = buf.getInt(8)
        k3 = buf.getInt(12)
    }

    fun encrypt(dstBuf: ByteArray, dst: ByteBuffer, src: ByteBuffer) {
        var v0 = src.getInt(0)
        var v1 = src.getInt(4)

        var sum = 0
        repeat(rounds / 2) {
            sum += DELTA
            v0 += ((v1 shl 4) + k0) xor (v1 + sum) xor ((v1 ushr 5) + k1)
            v1 += ((v0 shl 4) + k2) xor (v0 + sum) xor ((v0 ushr 5) + k3)
        }

        dst.putInt(0, v0)
        dst.putInt(4, v1)
    }

    fun decrypt(dstBuf: ByteArray, dst: ByteBuffer) {
        var v0 = dst.getInt(0)
        var v1 = dst.getInt(4)

        var sum = (DELTA.toLong() * rounds / 2).toInt()
        repeat(rounds / 2) {
            v1 -= ((v0 shl 4) + k2) xor (v0 + sum) xor ((v0 ushr 5) + k3)
            v0 -= ((v1 shl 4) + k0) xor (v1 + sum) xor ((v1 ushr 5) + k1)
            sum -= DELTA
        }

        dst.putInt(0, v0)
        dst.putInt(4, v1)
    }
}
