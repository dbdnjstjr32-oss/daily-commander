package com.dc.aurora.services

import android.content.Context
import com.dc.aurora.data.AppDatabase
import com.dc.aurora.data.MemoryDao
import com.dc.aurora.data.MemoryEntity
import com.dc.aurora.data.ScheduleDao
import com.dc.aurora.data.ScheduleEntity

class ScheduleRepository(context: Context) {

    private val dao: ScheduleDao = AppDatabase.getInstance(context).scheduleDao()
    private val memoryDao: MemoryDao = AppDatabase.getInstance(context).memoryDao()

    suspend fun getAllMemories() = memoryDao.getAll()
    suspend fun insertMemory(content: String) = memoryDao.insert(MemoryEntity(content = content))
    suspend fun deleteMemory(id: Long) = memoryDao.delete(id)
    suspend fun clearMemories() = memoryDao.deleteAll()

    suspend fun insert(s: ScheduleEntity): Long = dao.insert(s)

    suspend fun update(s: ScheduleEntity) = dao.update(s)

    suspend fun getById(id: Long): ScheduleEntity? = dao.getById(id)

    suspend fun getForRange(from: Long, to: Long, includeDone: Boolean = false) =
        if (includeDone) dao.getForRangeIncludingDone(from, to)
        else dao.getForRange(from, to)

    suspend fun getUpcoming(limit: Int = 20) = dao.getUpcoming(System.currentTimeMillis(), limit)

    suspend fun getConflicting(startDt: Long, endDt: Long, excludeId: Long = -1) =
        dao.getConflicting(startDt, endDt, excludeId)

    suspend fun searchByTitle(query: String, includeDone: Boolean = false) =
        if (includeDone) dao.searchByTitleAll(query) else dao.searchByTitle(query)

    suspend fun getDday(now: Long) = dao.getDday(now)

    suspend fun getDueForNotification(now: Long) = dao.getDueForNotification(now)

    suspend fun getDueForCompletion(cutoff: Long) = dao.getDueForCompletion(cutoff)

    suspend fun markNotificationSent(id: Long) = dao.markNotificationSent(id)

    suspend fun markCompletionNotified(id: Long) = dao.markCompletionNotified(id)

    suspend fun findSchedule(scheduleId: Long?, title: String?): ScheduleEntity? {
        if (scheduleId != null && scheduleId > 0) {
            val s = dao.getById(scheduleId)
            if (s != null && s.status != "cancelled") return s
        }
        if (!title.isNullOrBlank()) {
            return dao.searchByTitleAll(title).firstOrNull()
        }
        return null
    }
}
