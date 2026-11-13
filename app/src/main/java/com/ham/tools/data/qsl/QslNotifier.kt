package com.ham.tools.data.qsl

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ham.tools.MainActivity
import com.ham.tools.R

/**
 * QSL 交换申请的本地通知。
 *
 * 说明：本 App 不集成任何厂商/第三方推送 SDK。新申请是通过前台轮询与
 * 后台周期任务向自部署服务端查询后，在**本机**发出的通知，并非服务器主动推送。
 * 因此演示模式（服务端未部署）不会发任何交换通知，避免把 DEMO 数据当成真实申请。
 */
object QslNotifier {

    const val CHANNEL_ID = "qsl_exchange_requests"
    const val EXTRA_OPEN_QSL = "open_qsl"
    private const val NOTIFICATION_ID = 4101
    private const val DEMO_NOTIFICATION_ID = 4102

    /** Android 13 起 POST_NOTIFICATIONS 才是运行时权限。 */
    fun needsRuntimePermission(): Boolean = Build.VERSION.SDK_INT >= 33

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "QSL 交换申请",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "其他用户向你发起 QSL 卡片交换申请时提醒"
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /** 通知总开关 +（API 33+）运行时权限都要通过。 */
    fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (!needsRuntimePermission()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openIntent(context: Context): PendingIntent {
        // 直接重建到主页，避免用户停留在工具详情页时点通知却看不到 QSL 板块
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_QSL, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, 1001, intent, flags)
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_qsl_notification)
            .setAutoCancel(true)
            .setContentIntent(openIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

    /** 有待处理的交换申请。count 是当前待处理数量。 */
    fun notifyNewRequest(context: Context, count: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val builder = baseBuilder(context)
            .setContentTitle("收到 QSL 卡片交换申请")
            .setContentText(
                if (count > 1) "有 $count 条交换申请等你处理，点开查看" else "有 1 条交换申请等你处理，点开查看"
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "有人向你发起了 QSL 卡片交换申请。打开「QSL 交换」即可接受或婉拒；" +
                        "对方接受后才能看到你的邮寄地址。"
                )
            )
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // 权限在检查后被用户从系统设置里撤掉，静默跳过即可
        }
    }

    /** 权限说明页的「发一条测试通知」按钮：文案明确标注是演示，不冒充真实申请。 */
    fun notifyDemoTest(context: Context) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val builder = baseBuilder(context)
            .setContentTitle("【演示】通知已就绪")
            .setContentText("这是一条测试通知，不是真实的交换申请")
        try {
            NotificationManagerCompat.from(context).notify(DEMO_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }
}
