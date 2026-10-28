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
