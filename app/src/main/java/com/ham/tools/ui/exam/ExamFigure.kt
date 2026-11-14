package com.ham.tools.ui.exam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.ham.tools.data.exam.ExamFigure
import com.ham.tools.data.exam.ExamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 附图来自官方「总题库附图标记.pdf」：三页原理图，每题按 [J] 编号对应其中一块区域。
 * 题库 JSON 里存的是 PDF points 裁切框，这里按渲染 dpi 换算成像素裁切，并自动去掉白边。
 *
 * 只缓存最近使用的一页位图（其余页不常驻内存），且整个板块完全离线。
 */
object ExamFigureLoader {
    @Volatile
    private var cachedPage = -1

    @Volatile
    private var cachedBitmap: Bitmap? = null

    @Synchronized
    fun load(context: Context, figure: ExamFigure, dpi: Int): ImageBitmap? =
        loadBitmap(context, figure, dpi)?.asImageBitmap()

    /** 供 PDF 组卷使用：与界面共用同一套「按 PDF points 裁切 + 去白边」逻辑。 */
    @Synchronized
    fun loadBitmap(context: Context, figure: ExamFigure, dpi: Int): Bitmap? = runCatching {
        val page = if (cachedPage == figure.page) {
            cachedBitmap
        } else {
            context.assets.open("${ExamRepository.FIGURE_ASSET_PREFIX}${figure.page + 1}.png")
                .use { BitmapFactory.decodeStream(it) }
                .also {
                    cachedPage = figure.page
                    cachedBitmap = it
                }
        } ?: return null
        val scale = dpi / 72f
        val left = max(0, (figure.x0 * scale).toInt()).coerceAtMost(page.width - 1)
        val top = max(0, (figure.y0 * scale).toInt()).coerceAtMost(page.height - 1)
        val right = min(page.width, (figure.x1 * scale).toInt()).coerceAtLeast(left + 1)
        val bottom = min(page.height, (figure.y1 * scale).toInt()).coerceAtLeast(top + 1)
        val region = Bitmap.createBitmap(page, left, top, right - left, bottom - top)
        val trimmed = trimWhitespace(region)
        if (trimmed !== region) region.recycle()
        trimmed
    }.getOrNull()

    /** 扫描非白像素，把裁切框进一步收紧，避免附图四周留一大片空白。 */
    private fun trimWhitespace(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val c = pixels[row + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r < 244 || g < 244 || b < 244) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return bitmap
        val pad = (w * 0.012f).toInt().coerceAtLeast(3)
        val l = max(0, minX - pad)
        val t = max(0, minY - pad)
        val r = min(w, maxX + pad + 1)
        val b = min(h, maxY + pad + 1)
        return Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
    }
}

@Composable
fun rememberExamFigure(figure: ExamFigure?, dpi: Int): ImageBitmap? {
    if (figure == null) return null
    val context = LocalContext.current
    var bitmap by remember(figure, dpi) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(figure, dpi) {
        bitmap = withContext(Dispatchers.IO) {
            ExamFigureLoader.load(context, figure, dpi)
        }
    }
    return bitmap
}
