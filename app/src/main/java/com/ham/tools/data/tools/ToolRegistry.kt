package com.ham.tools.data.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LeakAdd
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.WbSunny
import com.ham.tools.model.ToolCategory
import com.ham.tools.model.ToolMeta

/**
 * 全量工具清单。新增工具时只需在此登记，
 * 首页宫格与导航路由会自动带上它们。
 */
object ToolRegistry {

    val tools: List<ToolMeta> = listOf(
        ToolMeta(
            id = ToolIds.WAVELENGTH,
            name = "频率波长",
            description = "频率与波长互算，含频段归属提示",
            category = ToolCategory.CALCULATOR,
            implemented = true,
            icon = Icons.Filled.Straighten
        ),
        ToolMeta(
            id = ToolIds.ANTENNA_LENGTH,
            name = "天线长度",
            description = "偶极子、倒 V、垂直天线振子长度",
            category = ToolCategory.CALCULATOR,
            implemented = true,
            icon = Icons.Filled.Cable
        ),
        ToolMeta(
            id = ToolIds.VSWR,
            name = "驻波比回损",
            description = "VSWR、回波损耗、反射系数互算",
            category = ToolCategory.CALCULATOR,
            implemented = true,
            icon = Icons.Filled.Speed
        ),
        ToolMeta(
            id = ToolIds.FEEDLINE_LOSS,
            name = "馈线损耗",
            description = "按线型与长度估算线缆衰减",
            category = ToolCategory.CALCULATOR,
            implemented = true,
            icon = Icons.Filled.Timeline
        ),
        ToolMeta(
            id = ToolIds.LINK_BUDGET,
            name = "链路预算",
            description = "弗里斯传输方程与接收电平",
            category = ToolCategory.CALCULATOR,
            implemented = true,
            icon = Icons.Filled.LeakAdd
        ),

        ToolMeta(
            id = "band_plan",
            name = "业余频段划分",
            description = "中国与 IARU 各区频段划分表",
            category = ToolCategory.REFERENCE,
            icon = Icons.Filled.ViewWeek
        ),
        ToolMeta(
            id = "callsign_prefix",
            name = "呼号前缀",
            description = "国家和地区呼号前缀检索",
            category = ToolCategory.REFERENCE,
            icon = Icons.Filled.TravelExplore
        ),
        ToolMeta(
            id = "qcode",
            name = "Q 简语",
            description = "常用 Q 简语与字母解释法",
            category = ToolCategory.REFERENCE,
            icon = Icons.AutoMirrored.Filled.Chat
        ),
        ToolMeta(
            id = "morse_code",
            name = "摩尔斯对照",
            description = "字符与电码对照表，可发声",
            category = ToolCategory.REFERENCE,
            icon = Icons.Filled.GraphicEq
        ),
        ToolMeta(
            id = "sat_repeater",
            name = "卫星与中继",
            description = "业余卫星与常用中继名录",
            category = ToolCategory.REFERENCE,
            icon = Icons.Filled.SatelliteAlt
        ),

        ToolMeta(
            id = "cw_keyer",
            name = "CW 电键",
            description = "触屏电键发射 CW，可调速度",
            category = ToolCategory.AUDIO,
            icon = Icons.Filled.PlayArrow
        ),
        ToolMeta(
            id = "cw_trainer",
            name = "CW 听抄训练",
            description = "科赫法与随机码听抄练习",
            category = ToolCategory.AUDIO,
            icon = Icons.Filled.Headphones
        ),
        ToolMeta(
            id = "dtmf",
            name = "DTMF",
            description = "双音多频生成与解码",
            category = ToolCategory.AUDIO,
            icon = Icons.Filled.Dialpad
        ),
        ToolMeta(
            id = "tone_generator",
            name = "音调发生器",
            description = "单音与扫频信号输出",
            category = ToolCategory.AUDIO,
            icon = Icons.Filled.MusicNote
        ),

        ToolMeta(
            id = "propagation",
            name = "传播指数",
            description = "太阳通量、K/A 指数与传播预测",
            category = ToolCategory.NETWORK,
            icon = Icons.Filled.WbSunny
        ),
        ToolMeta(
            id = "dx_cluster",
            name = "DX 集群",
            description = "实时通联 spots 与台站查询",
            category = ToolCategory.NETWORK,
            icon = Icons.Filled.Public
        ),
        ToolMeta(
            id = "grid_square",
            name = "网格与方位",
            description = "梅登海德网格与大圆距离方位",
            category = ToolCategory.NETWORK,
            icon = Icons.Filled.Explore
        ),
        ToolMeta(
            id = "aprs_map",
            name = "APRS 地图",
            description = "离线地图上展示 APRS 台站",
            category = ToolCategory.NETWORK,
            icon = Icons.Filled.Map
        ),
        ToolMeta(
            id = "callsign_lookup",
            name = "呼号查询",
            description = "联网检索呼号归属与资料",
            category = ToolCategory.NETWORK,
            icon = Icons.Filled.Badge
        )
    )

    val categories: List<ToolCategory> = ToolCategory.entries

    fun toolsOf(category: ToolCategory): List<ToolMeta> = tools.filter { it.category == category }

    fun find(toolId: String?): ToolMeta? = tools.firstOrNull { it.id == toolId }
}

/** 工具 id 常量，路由统一从这里取，避免拼写不一致。 */
object ToolIds {
    const val WAVELENGTH = "wavelength"
    const val ANTENNA_LENGTH = "antenna_length"
    const val VSWR = "vswr"
    const val FEEDLINE_LOSS = "feedline_loss"
    const val LINK_BUDGET = "link_budget"
}
