package com.dc.aurora.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.dc.aurora.MainActivity
import com.dc.aurora.data.ScheduleEntity
import com.dc.aurora.data.toKst

object NotificationHelper {

    const val CHANNEL_SCHEDULE = "dc_schedule"
    const val CHANNEL_BRIEFING = "dc_briefing"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_SCHEDULE) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_SCHEDULE, "일정 알림", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "일정 시작 전 알림 및 완료 확인"; enableVibration(true) }
            )
        }
        if (nm.getNotificationChannel(CHANNEL_BRIEFING) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_BRIEFING, "모닝 브리핑", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun showNotification(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        prefill: String? = null,
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            prefill?.let { putExtra(MainActivity.EXTRA_PREFILL_TEXT, it) }
        }
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(id, notif)
    }

    fun scheduleReminder(context: Context, schedule: ScheduleEntity) {
        val notifAt = schedule.notificationAt ?: return
        if (notifAt <= System.currentTimeMillis()) return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_REMINDER
            putExtra("id", schedule.id)
            putExtra("title", schedule.title)
            putExtra("start_dt", schedule.startDt)
            putExtra("location", schedule.location)
            putExtra("end_dt", schedule.endDt)
        }
        val pi = PendingIntent.getBroadcast(
            context, schedule.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, notifAt, pi)
    }

    fun scheduleCompletionCheck(context: Context, schedule: ScheduleEntity) {
        val checkAt = (schedule.endDt ?: return) + 10 * 60_000L
        if (checkAt <= System.currentTimeMillis()) return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_COMPLETION
            putExtra("id", schedule.id)
            putExtra("title", schedule.title)
        }
        val pi = PendingIntent.getBroadcast(
            context, (schedule.id + 100_000L).toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, checkAt, pi)
    }

    fun scheduleMorningBriefingTomorrow(context: Context) {
        val kst = System.currentTimeMillis().toKst()
        val next8am = kst.toLocalDate().plusDays(1)
            .atTime(8, 0)
            .atZone(ScheduleEntity.KST)
            .toInstant().toEpochMilli()

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = NotificationReceiver.ACTION_MORNING
        }
        val pi = PendingIntent.getBroadcast(
            context, 999_999, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next8am, pi)
    }
}
