package com.ham.tools.data

/**
 * 作者与项目署名信息。
 *
 * 首页右上角、「我的」页与「关于」页都从这里取值，
 * 要改署名时只改这一处即可，不需要动界面代码。
 */
object AppConfig {

    /**
     * 账号服务器地址，写死在这里。
     *
     * 注意不要带结尾斜杠，也不要带 /api —— 请求路径由 AuthRepository 内部拼接
     * （例如 /api/health、/api/auth/login）。
     * 换服务器时只改这一行即可，界面上不再提供修改入口。
     */
    const val SERVER_BASE_URL = "https://api.ba4igg.cn"

    /**
     * 联网板块的数据聚合接口（可选）。
     * 服务端未部署该接口时，各工具会自动降级到内置离线数据并在界面上如实标注。
     */
    val HAM_DATA_URL = "$SERVER_BASE_URL/api/ham/overview"

    /** 太阳活动公开数据源：hamqsl.com 的 solar XML，业余无线电界通用、无需密钥。 */
    const val SOLAR_XML_URL = "https://www.hamqsl.com/solarxml.php"

    /** 备用数据源：NOAA SWPC 的行星 K 指数（1 分钟分辨率）。 */
    const val SOLAR_NOAA_URL = "https://services.swpc.noaa.gov/json/planetary_k_index_1m.json"

    /**
     * 呼号资料在线源：HamDB REST API，免费公开、无需密钥。
     * 形如 https://api.hamdb.org/v1/W1AW/json/hamtools，返回执照登记资料。
     * 注意这是「执照登记信息」，不是实时位置，界面必须这样标注。
     */
    const val HAMDB_BASE_URL = "https://api.hamdb.org/v1/"

    /** HamDB 要求在 URL 末段带应用标识，用于对方统计与限流。 */
    const val HAMDB_APP_NAME = "hamtools-ba4igg"

    /**
     * DX spots 在线源：DXWatch 公开的实时 DX cluster 页面（HTML，无需密钥）。
     * 页面结构可能调整，解析失败时客户端会静默降级到服务端聚合或内置样例。
     */
    const val DXWATCH_SPOTS_URL = "https://www.dxwatch.com/dx"

    /** 服务端呼号查询代理（可选部署，国内网络更稳）；失败不影响 HamDB 直连。 */
    val CALLSIGN_LOOKUP_URL = "$SERVER_BASE_URL/api/ham/callsign"

    /**
     * 检测更新：GitHub Releases 列表接口，只读、无需密钥、无需登录。
     * 用列表而不是 `/releases/latest`，是为了同时支持「正式通道」与「测试通道」：
     * 正式通道筛掉 Pre-release，测试通道包含 Pre-release，都由客户端按 `prerelease`
     * 字段判断，不硬编码任何版本黑名单。
     */
    const val GITHUB_RELEASE_API_URL =
        "https://api.github.com/repos/BA4IGG/hamtools/releases?per_page=20"

    /**
     * 备用源：GitHub 官方 releases Atom feed。
     * REST API 未认证时限流为 60 次/小时/IP，且部分网络下 api.github.com 不可达；
     * Atom 无限流，同样带更新日志，但没有附件 APK 直链，也没有 `prerelease` 字段，
     * 因此界面必须如实标注「来源差异 + 通道无法确认」，不能冒充 API 直连结果。
     */
    const val GITHUB_RELEASE_ATOM_URL = "https://github.com/BA4IGG/hamtools/releases.atom"

    /** 仓库主页，用于「查看发布页」等兜底跳转。 */
    const val GITHUB_REPO_URL = "https://github.com/BA4IGG/hamtools"

    /** 作者呼号，同时用作「我的」页的署名标题。 */
    const val AUTHOR_CALLSIGN = "BA4IGG"

    /** 作者昵称 / 署名。 */
    const val AUTHOR_NAME = "BA4IGG"

    /** 一句话简介，展示在「我的」页的名片里。 */
    const val AUTHOR_BIO = "业余无线电爱好者，本工具的作者与维护者"

    /** 联系方式（邮箱或主页），留空则不展示该行。 */
    const val AUTHOR_CONTACT = ""

    /** 版权起止年份，展示在关于页。 */
    const val COPYRIGHT_YEAR = "2026"
}
