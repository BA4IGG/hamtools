package com.ham.tools.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 解码结果：单声道 16bit PCM 与采样率。 */
data class WavData(val pcm: ShortArray, val sampleRate: Int)

/**
 * 极简 WAV 读写（RIFF / PCM 16bit），用于把组好的 FT8、SSTV 基带音频保存到「下载」，
 * 以及从本地 WAV 导入解码（不需要麦克风权限）。
 * 只认 16bit PCM；其它格式返回 null 并由界面如实提示，不做静默猜测。
 */
object WavIo {

    fun encode(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = pcm.size * 2
        val out = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(36 + dataBytes)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16)
        out.putShort(1)                 // PCM
        out.putShort(1)                 // mono
        out.putInt(sampleRate)
        out.putInt(sampleRate * 2)      // byte rate
        out.putShort(2)                 // block align
        out.putShort(16)                // bits per sample
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(dataBytes)
        for (s in pcm) out.putShort(s)
        return out.array()
    }

    fun decode(bytes: ByteArray): WavData? {
        if (bytes.size < 44) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val riff = ByteArray(4).also { buf.get(it) }
        if (String(riff, Charsets.US_ASCII) != "RIFF") return null
        buf.int
        val wave = ByteArray(4).also { buf.get(it) }
        if (String(wave, Charsets.US_ASCII) != "WAVE") return null

        var channels = 0
        var sampleRate = 0
        var bits = 0
        var format = 0
        var dataOffset = -1
        var dataSize = 0

        while (buf.remaining() >= 8) {
            val id = ByteArray(4).also { buf.get(it) }
            val size = buf.int
            val chunkStart = buf.position()
            val name = String(id, Charsets.US_ASCII)
            if (name == "fmt " && size >= 16) {
                format = buf.short.toInt()
                channels = buf.short.toInt()
                sampleRate = buf.int
                buf.int                 // byte rate
                buf.short                // block align
                bits = buf.short.toInt()
            } else if (name == "data") {
                dataOffset = chunkStart
                dataSize = size
            }
            val next = chunkStart + size + (size and 1)
            if (next > bytes.size || next <= buf.position()) break
            buf.position(next)
        }

        if (format != 1 || bits != 16 || channels !in 1..2 || sampleRate <= 0) return null
        if (dataOffset < 0 || dataSize < 2) return null
        val available = ((bytes.size - dataOffset) / 2).coerceAtMost(dataSize / 2)
        val pcm = ShortArray(available / channels)
        val view = ByteBuffer.wrap(bytes, dataOffset, available * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in pcm.indices) {
            pcm[i] = view.short
            if (channels == 2) view.short   // 立体声只取左声道
        }
        return WavData(pcm, sampleRate)
    }
}
