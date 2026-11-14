package com.ham.tools.ui.exam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.ham.tools.data.exam.ExamPaper
import com.ham.tools.data.exam.ExamPaperBuilder
import com.ham.tools.data.exam.ExamQuestion
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * 把组好的试卷渲染成 A4 PDF，完全离线、不申请任何存储权限。
 *
 * 用平台自带的 android.graphics.pdf.PdfDocument + Canvas.drawText 绘制，
 * 中文字形走系统字体回退；题目与选项按「半角单位」折行，
 * 附图按题库里的 PDF points 裁切框插入，答案单独放在最后一页方便撕开。
 * 生成的字节流由调用方通过系统文件选择器（SAF）写到用户选的位置。
 */
object ExamPdf {
    private const val PAGE_WIDTH = 595      // A4, 72 dpi points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 42f
    private const val BODY_SIZE = 10.5f
    private const val LINE_HEIGHT = 15.5f
    private const val TITLE_SIZE = 16f
    private const val TITLE_LINE_HEIGHT = 24f
    private const val MAX_UNITS = 92
    private const val MAX_FIGURE_HEIGHT = 110f

    fun render(context: Context, paper: ExamPaper, dpi: Int = 180): ByteArray {
        val document = PdfDocument()
        val writer = PageWriter(document, "试卷 ${paper.serial}")
        try {
            writer.text(paper.title, writer.titlePaint, TITLE_LINE_HEIGHT, 6f)
            paper.intro.forEach { writer.text(it, writer.subtlePaint, LINE_HEIGHT) }
            writer.gap(8f)
            paper.questions.forEachIndexed { index, question ->
                writer.question(index + 1, question, context, dpi)
            }
            writer.answers(paper)
            writer.close()
            val output = ByteArrayOutputStream()
            document.writeTo(output)
            return output.toByteArray()
        } finally {
            document.close()
        }
    }

    private class PageWriter(private val document: PdfDocument, private val footer: String) {
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TITLE_SIZE
            typeface = Typeface.DEFAULT_BOLD
            color = Color.rgb(24, 26, 32)
        }
        private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = BODY_SIZE
            typeface = Typeface.SANS_SERIF
            color = Color.rgb(28, 30, 36)
        }
        val subtlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = BODY_SIZE - 0.5f
            typeface = Typeface.SANS_SERIF
            color = Color.rgb(92, 96, 106)
        }
        private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 8.5f
            color = Color.rgb(130, 134, 142)
        }
        private val rulePaint = Paint().apply { color = Color.rgb(214, 218, 226) }
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f

        init {
            newPage()
        }

        fun gap(amount: Float) {
            y += amount
        }

        /** 按半角单位折行后逐行绘制；行高与字号由调用方指定。 */
        fun text(value: String, paint: Paint = bodyPaint, lineHeight: Float = LINE_HEIGHT, gapAfter: Float = 0f) {
            ExamPaperBuilder.wrap(value, MAX_UNITS).forEach { line ->
                ensure(lineHeight)
                canvas?.drawText(line, MARGIN, y + paint.textSize, paint)
                y += lineHeight
            }
            if (gapAfter > 0f) gap(gapAfter)
        }

        fun question(number: Int, question: ExamQuestion, context: Context, dpi: Int) {
            val tag = if (question.multi) "【多选】" else "【单选】"
            val header = "$number. $tag ${question.text}"
            val lines = ExamPaperBuilder.wrap(header, MAX_UNITS).size +
                question.options.sumOf { option ->
                    ExamPaperBuilder.wrap(option, MAX_UNITS - 6).size
                }
            val figure = question.figure
            ensure(lines * LINE_HEIGHT + (if (figure != null) MAX_FIGURE_HEIGHT else 0f) + 16f)

            text(header, bodyPaint, LINE_HEIGHT)
            question.options.forEachIndexed { index, option ->
                val prefix = "    ${'A' + index}. "
                // 悬挂缩进：续行比首行多留 4 个半角单位
                val wrapped = ExamPaperBuilder.wrap(option, MAX_UNITS - 10)
                wrapped.forEachIndexed { lineIndex, line ->
                    ensure(LINE_HEIGHT)
                    val indent = MARGIN + if (lineIndex == 0) 0f else 16f
                    val shown = if (lineIndex == 0) prefix + line else line
                    canvas?.drawText(shown, indent, y + BODY_SIZE, bodyPaint)
                    y += LINE_HEIGHT
                }
            }
            if (figure != null) {
                val bitmap = ExamFigureLoader.loadBitmap(context, figure, dpi)
                if (bitmap != null) drawFigure(bitmap)
            }
            gap(9f)
        }

        private fun drawFigure(bitmap: Bitmap) {
            if (bitmap.width <= 0 || bitmap.height <= 0) return
            val maxWidth = PAGE_WIDTH - MARGIN * 2
            val scale = min(maxWidth / bitmap.width, MAX_FIGURE_HEIGHT / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            ensure(height + 8f)
            canvas?.drawBitmap(bitmap, null, RectF(MARGIN, y, MARGIN + width, y + height), bitmapPaint)
            y += height + 8f
        }

        fun answers(paper: ExamPaper) {
            newPage()
            text("参考答案与评分说明", titlePaint, TITLE_LINE_HEIGHT, 10f)
            ExamPaperBuilder.answerLines(paper).forEach { line ->
                text(line, bodyPaint, LINE_HEIGHT)
            }
            gap(10f)
            text(
                "单选题 ${paper.singleCount} 题、多选题 ${paper.multiCount} 题，共 ${paper.questions.size} 题；" +
                    "答对 ${paper.level.passScore} 题合格，建议用时 ${paper.level.minutes} 分钟。",
                subtlePaint,
                LINE_HEIGHT
            )
            text(
                "多选题必须与答案完全一致，漏选、少选、多选均不得分。",
                subtlePaint,
                LINE_HEIGHT
            )
            text(
                "本卷由 HAM 工具合集按官方配额从 v20250728 题库随机生成，" +
                    "难度排序为估算值，不代表官方难度分级。",
                subtlePaint,
                LINE_HEIGHT
            )
        }

        fun close() {
            finishCurrent()
        }

        private fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - 44f) newPage()
        }

        private fun newPage() {
            finishCurrent()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val started = document.startPage(info)
            page = started
            canvas = started.canvas
            y = MARGIN
        }

        private fun finishCurrent() {
            val current = page ?: return
            val currentCanvas = canvas ?: return
            currentCanvas.drawLine(
                MARGIN,
                PAGE_HEIGHT - 30f,
                PAGE_WIDTH - MARGIN,
                PAGE_HEIGHT - 30f,
                rulePaint
            )
            currentCanvas.drawText("第 $pageNumber 页 · $footer", MARGIN, PAGE_HEIGHT - 16f, footerPaint)
            document.finishPage(current)
            page = null
            canvas = null
        }
    }
}
