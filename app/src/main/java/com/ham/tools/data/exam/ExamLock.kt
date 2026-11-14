package com.ham.tools.data.exam

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 模拟考试期间的全局「界面锁定」状态。
 *
 * 需要同时影响三层：Activity 的系统返回键、顶层 Pager 的 Dock 与左右滑动、
 * 考试页自身的退出确认。逐层透传回调会让每层都耦合考试状态，
 * 因此用一个进程内单例承载，由考试页在开始/结束/销毁时置位。
 *
 * 注意：Android 不允许第三方应用拦截 Home 键、下拉通知栏或强制切后台，
 * 这些系统级操作无法禁止；倒计时基于绝对开始时间，切回来后仍按真实时间继续。
 */
object ExamLock {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    fun set(value: Boolean) {
        _locked.value = value
    }
}
