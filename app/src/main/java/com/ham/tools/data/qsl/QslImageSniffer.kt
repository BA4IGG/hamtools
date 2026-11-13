package com.ham.tools.data.qsl

/**
 * 上传前的本地图片校验：只认真实文件头，不信任系统给的 MIME。
 *
 * 服务端 qsl.js 的 detectImage 做的是同一套判断，客户端先过一遍是为了
 * 在选到 PDF / HEIC / 超大图时立刻给出可读的提示，而不是发完 5 MB 才被拒。
 * 这里只做格式白名单，不做解码，因此是纯 JVM 代码，可以直接写单测。
 */
object QslImageSniffer {

    const val MAX_BYTES = 5 * 1024 * 1024

    fun isTooLarge(bytes: ByteArray): Boolean = bytes.size > MAX_BYTES

    /** 返回规范化后的 MIME；不支持的格式返回 null。 */
    fun mimeOf(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        // JPEG: FF D8 FF
        if (b(bytes[0]) == 0xFF && b(bytes[1]) == 0xD8 && b(bytes[2]) == 0xFF) {
            return "image/jpeg"
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (b(bytes[0]) == 0x89 && b(bytes[1]) == 0x50 &&
            b(bytes[2]) == 0x4E && b(bytes[3]) == 0x47 &&
            b(bytes[4]) == 0x0D && b(bytes[5]) == 0x0A &&
            b(bytes[6]) == 0x1A && b(bytes[7]) == 0x0A
        ) {
            return "image/png"
        }
        // WebP: RIFF????WEBP
        if (ascii(bytes, 0, 4) == "RIFF" && ascii(bytes, 8, 4) == "WEBP") {
            return "image/webp"
        }
        return null
    }

    /**
     * 供界面直接使用的一体化结果。
     * error 为 null 表示可以上传，mime 是最终要写进请求头的值。
     */
    data class Check(val mime: String?, val error: String?) {
        val ok: Boolean get() = mime != null && error == null
    }

    fun check(bytes: ByteArray): Check {
        if (isTooLarge(bytes)) return Check(null, "图片超过 5 MB，请压缩后再上传")
        val mime = mimeOf(bytes)
            ?: return Check(null, "只支持 JPG / PNG / WebP 图片")
        return Check(mime, null)
    }

    private fun b(value: Byte): Int = value.toInt() and 0xFF

    private fun ascii(bytes: ByteArray, start: Int, length: Int): String {
        val chars = CharArray(length)
        for (i in 0 until length) chars[i] = (b(bytes[start + i])).toChar()
        return String(chars)
    }
}
