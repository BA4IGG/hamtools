package com.ham.tools.model

import androidx.compose.ui.graphics.vector.ImageVector

/** 工具分组。 */
enum class ToolCategory(val label: String) {
    CALCULATOR("计算工具"),
    REFERENCE("查询手册"),
    AUDIO("音频与硬件"),
    NETWORK("联网与地图")
}

/**
 * 首页宫格里一个工具的元数据描述。
 *
 * @param implemented 是否已经有真实实现。为 false 时在首页呈现「即将推出」状态，
 *                    点击后进入占位页而不是具体功能页。
 */
data class ToolMeta(
    val id: String,
    val name: String,
    val description: String,
    val category: ToolCategory,
    val icon: ImageVector,
    val implemented: Boolean = false
)
