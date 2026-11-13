package com.ham.tools.ui.qsl

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ham.tools.data.qsl.QslStatus
import com.ham.tools.ui.theme.AppShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** QSL 板块统一的表单卡片，标题、说明、右侧动作可选。 */
@Composable
fun QslSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                action?.invoke()
            }
            content()
        }
    }
}

/** 单行/多行输入框；maxLength 在客户端先截断，避免提交时才被服务端拒绝。 */
@Composable
fun QslField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int = 0,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Next
) {
    val placeholderContent: (@Composable () -> Unit)? =
        placeholder?.let { hint -> { Text(text = hint) } }
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            if (maxLength <= 0 || next.length <= maxLength) onValueChange(next)
        },
        label = { Text(text = label) },
        placeholder = placeholderContent,
        singleLine = singleLine,
        minLines = minLines,
        enabled = enabled,
        shape = AppShapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        modifier = modifier.fillMaxWidth()
    )
}

/** 带说明的开关行。 */
@Composable
fun QslToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 顶部统计小方块。 */
@Composable
fun QslStatTile(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    highlight: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = AppShapes.medium,
        color = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = if (highlight) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlight) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/** 交换状态标签：进行中/完成/终止用不同的容器色，一眼能分辨。 */
@Composable
fun QslStatusChip(status: QslStatus, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val container = when {
        status == QslStatus.PENDING -> scheme.tertiaryContainer
        status.isOpen -> scheme.primaryContainer
        status == QslStatus.COMPLETED -> scheme.secondaryContainer
        status == QslStatus.RECORDED -> scheme.surfaceVariant
        else -> scheme.errorContainer
    }
    val content = when {
        status == QslStatus.PENDING -> scheme.onTertiaryContainer
        status.isOpen -> scheme.onPrimaryContainer
        status == QslStatus.COMPLETED -> scheme.onSecondaryContainer
        status == QslStatus.RECORDED -> scheme.onSurfaceVariant
        else -> scheme.onErrorContainer
    }
    Surface(modifier = modifier, shape = AppShapes.small, color = container) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * 按卡片 ID 异步加载并展示图片，我的资料页与交换记录里的「查看卡片」共用。
 * 大图先降采样再解码，避免 5 MB 照片直接吃进内存；下载需要带令牌，由调用方传入 load。
 */
@Composable
fun QslCardImage(
    cardId: Long,
    load: suspend (Long) -> ByteArray?,
    modifier: Modifier = Modifier,
    imageHeight: Dp = 180.dp,
    emptyText: String = "图片暂时无法显示"
) {
    var bitmap by remember(cardId) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(cardId) { mutableStateOf(false) }
    var loading by remember(cardId) { mutableStateOf(true) }

    LaunchedEffect(cardId) {
        loading = true
        failed = false
        val bytes = load(cardId)
        if (bytes == null) {
            failed = true
        } else {
            bitmap = withContext(Dispatchers.IO) { decodeQslPreview(bytes) }
            failed = bitmap == null
        }
        loading = false
    }

    when {
        bitmap != null -> Image(
            bitmap = bitmap!!,
            contentDescription = "QSL 卡片图片",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxWidth()
                .height(imageHeight)
        )
        failed -> QslEmptyHint(text = emptyText, modifier = modifier)
        else -> Box(
            modifier = modifier
                .fillMaxWidth()
                .height(imageHeight),
            contentAlignment = Alignment.Center
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

internal fun decodeQslPreview(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null
    val sample = maxOf(1, longest / 1200)
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

@Composable
fun QslEmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    )
}
