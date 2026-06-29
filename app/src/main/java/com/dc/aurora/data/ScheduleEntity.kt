package com.dc.aurora.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val startDt: Long? = null,      // epoch millis UTC
    val endDt: Long? = null,
    val allDay: Boolean = false,
    val location: String? = null,
    val importance: String = "medium",  // high | medium | low
    val status: String = "upcoming",    // upcoming | done | cancelled
    val category: String? = null,
    val notes: String? = null,
    val rawMessage: String? = null,
    val notificationAt: Long? = null,
    val notificationSent: Boolean = false,
    val completionNotified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val dDay: Int?
        get() {
            if (startDt == null) return null
            val today = ZonedDateTime.now(KST).toLocalDate()
            val scheduleDate = Instant.ofEpochMilli(startDt).atZone(KST).toLocalDate()
            return ChronoUnit.DAYS.between(today, scheduleDate).toInt()
        }

    companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}

fun Long.toKst(): ZonedDateTime = Instant.ofEpochMilli(this).atZone(ScheduleEntity.KST)
