package com.ham.tools.data.qsl

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ham.tools.data.preferences.UserPreferences
import com.ham.tools.data.preferences.settingsDataStore
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

/**
 * 「有没有新的交换申请」的统一检查逻辑，前台轮询与后台周期任务共用。
 *
 * 关键约束：只有确认服务端已部署 QSL 接口（ping 成功）才会检查申请，
 * 演示模式或网络不通时直接返回 null，**绝不因为 DEMO 数据发系统通知**。
 */
object QslNoticeController {

    suspend fun check(context: Context, token: String): QslSummary? {
        val api = QslApi(token)
        when (api.ping()) {
            is QslResult.Success -> Unit
            is QslResult.Failure -> return null
        }
        val result = api.summary()
        if (result !is QslResult.Success) return null
        val summary = result.value
        val preferences = UserPreferences(context.settingsDataStore)
        val lastNotified = preferences.qslLastIncomingId.firstOrNull() ?: 0L

        if (summary.incoming > 0 && summary.latestIncomingId > lastNotified) {
            QslNotifier.notifyNewRequest(context, summary.incoming)
            preferences.setQslLastIncomingId(summary.latestIncomingId)
        } else if (summary.incoming == 0 && lastNotified != 0L) {
            // 全部处理完就归零，保证下一批新申请还能被提醒
            preferences.setQslLastIncomingId(0L)
        }
        return summary
    }
}

/**
 * 后台周期检查：WorkManager 的最小周期是 15 分钟，且受系统省电策略/国产 ROM 影响，
 * 所以 App 打开时 QSL 页面还会做 30 秒级的前台轮询。
 * 这是「尽快提醒」而非服务器主动推送，界面与文档都如实说明。
 */
class QslSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val session = UserPreferences(applicationContext.settingsDataStore)
            .session
            .firstOrNull() ?: return Result.success()
        return try {
            QslNoticeController.check(applicationContext, session.token)
            Result.success()
        } catch (_: Exception) {
            // 单次失败不重要，交给下一次周期重试；不 retry() 以免频繁唤醒设备
            Result.success()
        }
    }
}

object QslSyncScheduler {
    private const val WORK_NAME = "qsl_incoming_check"

    fun enable(context: Context) {
        val request = PeriodicWorkRequestBuilder<QslSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }
}
