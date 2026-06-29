package com.dc.aurora.services

import com.dc.aurora.data.toKst
import java.time.ZonedDateTime

object ConflictChecker {

    private const val WORK_START = 9
    private const val WORK_END = 22

    suspend fun suggest(
        startDt: Long,
        endDt: Long,
        repo: ScheduleRepository,
        excludeId: Long = -1,
    ): List<Long> {
        val conflicts = repo.getConflicting(startDt, endDt, excludeId)
        if (conflicts.isEmpty()) return emptyList()

        val duration = endDt - startDt
        val alts = mutableListOf<Long>()

        // 1. 마지막 충돌 일정 직후
        val lastEnd = conflicts.mapNotNull { it.endDt }.maxOrNull()
        if (lastEnd != null) addIfValid(alts, lastEnd, duration)

        // 2. 첫 번째 충돌 직전
        val firstStart = conflicts.mapNotNull { it.startDt }.minOrNull()
        if (firstStart != null) addIfValid(alts, firstStart - duration, duration)

        // 3. 내일 같은 시각
        val tomorrow = startDt + 86_400_000L
        addIfValid(alts, tomorrow, duration)

        // 4. 내일 +1시간
        if (alts.size < 3) addIfValid(alts, tomorrow + 3_600_000L, duration)

        return alts.take(3)
    }

    private fun addIfValid(result: MutableList<Long>, candidate: Long, duration: Long) {
        if (candidate in result) return
        val z = candidate.toKst()
        val endHour = z.hour + (duration / 3_600_000.0)
        if (z.hour < WORK_START || endHour > WORK_END) return
        if (candidate < System.currentTimeMillis()) return
        result.add(candidate)
    }
}
