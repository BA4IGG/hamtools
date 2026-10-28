package com.ham.tools.navigation

/** 应用内路由常量。 */
object Routes {
    const val HOME = "home"
    const val ABOUT = "about"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val TOOL = "tool/{toolId}"
    const val ARG_TOOL_ID = "toolId"

    fun tool(toolId: String): String = "tool/$toolId"
}
