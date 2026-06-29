package com.dc.aurora.data

import androidx.room.*

@Dao
interface ScheduleDao {

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getById(id: Long): ScheduleEntity?

    @Query("""
        SELECT * FROM schedules
        WHERE status = 'upcoming' AND startDt >= :from AND startDt <= :to
        ORDER BY startDt
    """)
    suspend fun getForRange(from: Long, to: Long): List<ScheduleEntity>

    @Query("""
        SELECT * FROM schedules
        WHERE status IN ('upcoming', 'done') AND startDt >= :from AND startDt <= :to
        ORDER BY startDt
    """)
    suspend fun getForRangeIncludingDone(from: Long, to: Long): List<ScheduleEntity>

    @Query("""
        SELECT * FROM schedules
        WHERE status = 'upcoming' AND startDt >= :from
        ORDER BY startDt
        LIMIT :limit
    """)
    suspend fun getUpcoming(from: Long, limit: Int = 20): List<ScheduleEntity>

    @Query("""
        SELECT * FROM schedules
        WHERE status = 'upcoming'
        AND startDt < :endDt AND endDt > :startDt
        AND (:excludeId = -1 OR id != :excludeId)
        ORDER BY startDt
    """)
    suspend fun getConflicting(startDt: Long, endDt: Long, excludeId: Long = -1): List<ScheduleEntity>

    @Query("""
        SELECT * FROM schedules
        WHERE status = 'upcoming' AND title LIKE '%' || :query || '%'
        ORDER BY startDt
        LIMIT 5
    """)
    suspend fun searchByTitle(query: String): List<ScheduleEntity>

    @Query("""
        SELECT * FROM schedules
        WHERE status IN ('upcoming', 'done') AND title LIKE '%' || :query || '%'
        ORDER BY startDt
        LIMIT 5
    """)
    suspend fun searchByTitleAll(query: String): List<ScheduleEntity>

    @Query("""
        SELECT * FROM schedules
        WHERE status = 'upcoming' AND importance IN ('high', 'medium') AND startDt >= :now
        ORDER BY startDt
        LIMIT 10
    """)
    suspend fun getDday(now: Long): List<ScheduleEntity>

    @Query("""
        SELECT * FROM schedules
        WHERE notificationSent = 0 AND notificationAt <= :now AND status = 'upcoming'
    """)
    suspend fun getDueForNotification(now: Long): List<ScheduleEntity>

    @Query("""
        SELECT * FROM schedules
        WHERE completionNotified = 0 AND endDt <= :cutoff AND status = 'upcoming'
    """)
    suspend fun getDueForCompletion(cutoff: Long): List<ScheduleEntity>

    @Query("UPDATE schedules SET notificationSent = 1 WHERE id = :id")
    suspend fun markNotificationSent(id: Long)

    @Query("UPDATE schedules SET completionNotified = 1 WHERE id = :id")
    suspend fun markCompletionNotified(id: Long)
}
