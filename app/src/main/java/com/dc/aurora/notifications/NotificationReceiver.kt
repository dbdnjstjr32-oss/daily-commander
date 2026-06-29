package com.dc.aurora.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dc.aurora.data.ScheduleEntity
import com.dc.aurora.data.toKst
import com.dc.aurora.services.ScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.ensureChannels(context)
        when (intent.action) {
            ACTION_REMINDER    -> onReminder(context, intent)
            ACTION_COMPLETION  -> onCompletion(context, intent)
            ACTION_MORNING     -> onMorningBriefing(context)
            Intent.ACTION_BOOT_COMPLETED -> rescheduleAll(context)
        }
    }

    private fun onReminder(context: Context, intent: Intent) {
        val id    = intent.getLongExtra("id", 0)
        val title = intent.getStringExtra("title") ?: return
        val startDt = intent.getLongExtra("start_dt", 0)
        val location = intent.getStringExtra("location")
        val endDt = intent.getLongExtra("end_dt", 0)

        val timeStr = if (startDt > 0) {
            val z = startDt.toKst()
            String.format("%02d:%02d", z.hour, z.minute)
        } else "종일"
        val body = "$timeStr 시작${if (!location.isNullOrBlank()) " · $location" else ""}"

        NotificationHelper.showNotification(
            context, id.toInt(), NotificationHelper.CHANNEL_SCHEDULE,
            "⏰ $title", body,
        )

        // 종료 후 완료 확인 예약
        if (endDt > 0) {
            val fakeSchedule = ScheduleEntity(id = id, title = title, endDt = endDt)
            NotificationHelper.scheduleCompletionCheck(context, fakeSchedule)
        }

        // 알림 발송 표시 — runBlocking으로 리시버 종료 전 DB 업데이트 완료 보장
        val repo = ScheduleRepository(context)
        runBlocking(Dispatchers.IO) { repo.markNotificationSent(id) }
    }

    private fun onCompletion(context: Context, intent: Intent) {
        val id    = intent.getLongExtra("id", 0)
        val title = intent.getStringExtra("title") ?: return
        val prefill = "$title 완료했어"

        NotificationHelper.showNotification(
            context, (id + 100_000L).toInt(), NotificationHelper.CHANNEL_SCHEDULE,
            "✅ $title 다녀오셨나요?",
            "'완료'라고 답하면 기록할게요.",
            prefill = prefill,
        )
        val repo = ScheduleRepository(context)
        runBlocking(Dispatchers.IO) { repo.markCompletionNotified(id) }
    }

    private fun onMorningBriefing(context: Context) {
        val repo = ScheduleRepository(context)
        val kst = System.currentTimeMillis().toKst()
        val todayStart = kst.withHour(0).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()
        val todayEnd = todayStart + 86_399_999L

        val rows = runBlocking(Dispatchers.IO) { repo.getForRange(todayStart, todayEnd) }
        val title: String
        val body: String
        if (rows.isEmpty()) {
            title = "📅 오늘 일정"
            body = "오늘은 예정된 일정이 없어요. 여유로운 하루 보내세요!"
        } else {
            title = "📅 오늘 일정 ${rows.size}개"
            body = rows.joinToString("  ·  ") { s ->
                val t = s.startDt?.let {
                    val z = it.toKst()
                    String.format("%02d:%02d", z.hour, z.minute)
                } ?: "종일"
                val imp = if (s.importance == "high") "⭐" else ""
                "$t ${s.title}$imp"
            }
        }
        NotificationHelper.showNotification(
            context, 999_999, NotificationHelper.CHANNEL_BRIEFING, title, body,
        )
        NotificationHelper.scheduleMorningBriefingTomorrow(context)
    }

    private fun rescheduleAll(context: Context) {
        val repo = ScheduleRepository(context)
        val now = System.currentTimeMillis()
        runBlocking(Dispatchers.IO) {
            repo.getDueForNotification(Long.MAX_VALUE).forEach { s ->
                if ((s.notificationAt ?: 0) > now)
                    NotificationHelper.scheduleReminder(context, s)
            }
        }
        NotificationHelper.scheduleMorningBriefingTomorrow(context)
    }

    companion object {
        const val ACTION_REMINDER   = "com.dc.aurora.SCHEDULE_REMINDER"
        const val ACTION_COMPLETION = "com.dc.aurora.COMPLETION_CHECK"
        const val ACTION_MORNING    = "com.dc.aurora.MORNING_BRIEFING"
    }
}
