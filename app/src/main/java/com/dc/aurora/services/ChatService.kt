package com.dc.aurora.services

import android.content.Context
import com.dc.aurora.data.ScheduleEntity
import com.dc.aurora.data.toKst
import com.dc.aurora.network.AnthropicClient
import com.dc.aurora.notifications.NotificationHelper
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ChatService(private val repo: ScheduleRepository) {

    private val gson = Gson()
    private val isoFmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val messages = mutableListOf<AnthropicClient.Message>()

    // 충돌 대기 상태
    private var pendingSchedule: ScheduleEntity? = null
    private var pendingModify: PendingModify? = null
    private var conflictAlts: List<Long> = emptyList()  // epoch millis

    data class PendingModify(
        val scheduleId: Long,
        val originalStart: Long?,
        val originalEnd: Long?,
        val changes: Map<String, String?>,
    )

    suspend fun send(userMessage: String, context: Context): String {
        messages.add(AnthropicClient.Message("user", userMessage))

        val system = buildSystem()
        val raw = runCatching {
            AnthropicClient.call(
                model = AnthropicClient.SONNET,
                system = system,
                messages = messages.toList(),
            )
        }.getOrElse {
            messages.removeLastOrNull()   // 히스토리 오염 방지
            throw it
        }

        val parsed = parseResponse(raw)
        val intent = parsed?.get("intent")?.asString ?: "chat"
        val extract = parsed?.getAsJsonObject("extract") ?: JsonObject()
        var reply = parsed?.get("reply")?.asString ?: raw

        // 새로 파악한 사용자 정보 자동 저장
        parsed?.getAsJsonArray("memories")?.forEach { el ->
            val fact = el.asString.trim()
            if (fact.isNotBlank()) repo.insertMemory(fact)
        }

        reply = when (intent) {
            "add_schedule"     -> handleAdd(extract, userMessage, context)
            "conflict_resolve" -> {
                val chosen = extract.get("chosen_index")?.asInt ?: 0
                if (chosen == -1) {
                    clearPending()
                    "알겠어요, 저장하지 않을게요."
                } else {
                    handleConflictResolve(chosen, context)
                }
            }
            "view_schedule"    -> handleView(extract)
            "check_dday"       -> handleDday()
            "modify_schedule"  -> handleModify(extract, context)
            "cancel_schedule"  -> handleCancel(extract)
            else               -> reply
        }

        messages.add(AnthropicClient.Message("assistant", reply))
        return reply
    }

    // ── 인텐트 핸들러 ─────────────────────────────────────────────────────────

    private suspend fun handleAdd(extract: JsonObject, rawMsg: String, context: Context): String {
        if (extract.get("ambiguous")?.asBoolean == true) {
            return extract.get("clarification_needed")?.asString
                ?: "날짜를 좀 더 구체적으로 말씀해 주시겠어요?"
        }
        val schedules = extract.getAsJsonArray("schedules") ?: return "일정 정보를 찾지 못했어요."

        val savedParts = mutableListOf<String>()
        val conflictParts = mutableListOf<String>()

        for (item in schedules) {
            val obj = item.asJsonObject
            val startDt = obj.str("start_dt")?.parseIso() ?: continue
            val endDt = obj.str("end_dt")?.parseIso() ?: (startDt + 3_600_000L)
            val title = obj.str("title") ?: "일정"

            val conflicts = repo.getConflicting(startDt, endDt)
            if (conflicts.isNotEmpty()) {
                val alts = ConflictChecker.suggest(startDt, endDt, repo)
                pendingSchedule = ScheduleEntity(
                    title = title,
                    startDt = startDt,
                    endDt = endDt,
                    allDay = obj.get("all_day")?.asBoolean ?: false,
                    location = obj.str("location"),
                    importance = obj.str("importance") ?: "medium",
                    category = obj.str("category"),
                    notes = obj.str("notes"),
                    rawMessage = rawMsg,
                    notificationAt = startDt - 30 * 60_000L,
                )
                conflictAlts = alts
                conflictParts.add(
                    "⚠️ **$title** — **${conflicts.joinToString("·") { it.title }}**과 시간이 겹쳐요.\n" +
                    formatAlts(alts) +
                    "\n또는 '취소'를 입력하면 저장하지 않을게요."
                )
            } else {
                val entity = ScheduleEntity(
                    title = title,
                    startDt = startDt,
                    endDt = endDt,
                    allDay = obj.get("all_day")?.asBoolean ?: false,
                    location = obj.str("location"),
                    importance = obj.str("importance") ?: "medium",
                    category = obj.str("category"),
                    notes = obj.str("notes"),
                    rawMessage = rawMsg,
                    notificationAt = startDt - 30 * 60_000L,
                )
                val id = repo.insert(entity)
                NotificationHelper.scheduleReminder(context, entity.copy(id = id))
                savedParts.add("✅ **$title**${dDayText(startDt)}")
            }
        }

        return buildList {
            if (savedParts.isNotEmpty()) add(savedParts.joinToString("\n"))
            if (conflictParts.isNotEmpty()) add(conflictParts.joinToString("\n\n"))
        }.joinToString("\n\n").ifBlank { "일정을 저장했어요." }
    }

    private suspend fun handleConflictResolve(chosenIndex: Int, context: Context): String {
        if (conflictAlts.isEmpty()) {
            clearPending()
            return "선택할 대기 중인 일정이 없어요."
        }
        val idx = chosenIndex.coerceIn(0, conflictAlts.lastIndex)
        val chosenStart = conflictAlts[idx]

        // 수정 pending
        pendingModify?.let { pm ->
            val s = repo.getById(pm.scheduleId) ?: run {
                clearPending()
                return "해당 일정을 찾지 못했어요."
            }
            val duration = if (pm.originalStart != null && pm.originalEnd != null)
                pm.originalEnd - pm.originalStart else 3_600_000L
            val updated = applyChanges(s, pm.changes + mapOf(
                "start_dt" to chosenStart.toKst().format(isoFmt),
                "end_dt"   to (chosenStart + duration).toKst().format(isoFmt),
            ))
            repo.update(updated)
            if (updated.notificationAt != null)
                NotificationHelper.scheduleReminder(context, updated)
            clearPending()
            return "✅ **${s.title}**을 ${fmtTime(chosenStart)}으로 변경했어요."
        }

        // 추가 pending
        pendingSchedule?.let { ps ->
            val duration = if (ps.startDt != null && ps.endDt != null)
                ps.endDt - ps.startDt else 3_600_000L
            val entity = ps.copy(
                startDt = chosenStart,
                endDt = chosenStart + duration,
                notificationAt = chosenStart - 30 * 60_000L,
            )
            val id = repo.insert(entity)
            NotificationHelper.scheduleReminder(context, entity.copy(id = id))
            clearPending()
            return "✅ **${ps.title}**을 ${fmtTime(chosenStart)}으로 저장했어요${dDayText(chosenStart)}."
        }

        return "선택할 대기 중인 일정이 없어요."
    }

    private suspend fun handleView(extract: JsonObject): String {
        val now = System.currentTimeMillis()
        val todayStart = ZonedDateTime.now(ScheduleEntity.KST)
            .withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
        val todayEnd = todayStart + 86_399_999L

        val fromIso = extract.str("date_from")
        val toIso = extract.str("date_to")
        val dateFrom = fromIso?.parseIso() ?: todayStart
        val dateTo = toIso?.parseIso() ?: todayEnd
        val keyword = extract.str("keyword")
        val importance = extract.str("importance")

        val isPast = dateTo < ZonedDateTime.now(ScheduleEntity.KST)
            .withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()

        var rows = repo.getForRange(dateFrom, dateTo, includeDone = isPast)
        if (!keyword.isNullOrBlank())
            rows = rows.filter { it.title.contains(keyword, ignoreCase = true) }
        if (!importance.isNullOrBlank())
            rows = rows.filter { it.importance == importance }

        val label = buildLabel(dateFrom, dateTo, keyword, importance, todayStart, todayEnd)

        if (rows.isEmpty()) return "**$label** 일정이 없어요."

        val lines = mutableListOf("📅 **$label (${rows.size}개)**\n")
        var prevDate = ""
        val fromDate = dateFrom.toKst().toLocalDate()
        val toDate = dateTo.toKst().toLocalDate()
        val multiDay = fromDate != toDate

        for (s in rows) {
            val kst = s.startDt?.toKst()
            val dateStr = kst?.toLocalDate()?.toString() ?: ""
            if (multiDay && dateStr != prevDate && kst != null) {
                lines.add("\n**${kst.monthValue}월 ${kst.dayOfMonth}일**")
                prevDate = dateStr
            }
            val t = kst?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "종일"
            val imp = if (s.importance == "high") " ⭐" else ""
            val loc = if (!s.location.isNullOrBlank()) " (${s.location})" else ""
            val doneMark = if (s.status == "done") " ✓" else ""
            val dday = if (s.dDay != null && !isPast) dDayText(s.startDt ?: 0) else ""
            lines.add("• $t ${s.title}$imp$loc$doneMark$dday")
        }
        return lines.joinToString("\n")
    }

    private suspend fun handleDday(): String {
        val now = System.currentTimeMillis()
        val rows = repo.getDday(now)
        if (rows.isEmpty()) return "다가오는 일정이 없어요."
        val lines = mutableListOf("📌 **D-day 현황**\n")
        for (s in rows) {
            val d = s.dDay ?: continue
            val imp = if (s.importance == "high") "⭐ " else ""
            val dday = when {
                d == 0 -> "D-Day"
                d < 0  -> "D+${-d}"
                else   -> "D-$d"
            }
            lines.add("• **$dday** $imp${s.title}")
        }
        return lines.joinToString("\n")
    }

    private suspend fun handleModify(extract: JsonObject, context: Context): String {
        val sid = extract.get("schedule_id")?.takeIf { !it.isJsonNull }?.runCatching { asLong }?.getOrNull()
        val title = extract.str("title")
        val changes = extract.getAsJsonObject("changes") ?: JsonObject()
        val changesMap = changes.entrySet().associate { e ->
            e.key to e.value?.takeIf { !it.isJsonNull }?.asString
        }

        val s = repo.findSchedule(sid, title)
            ?: return if (!title.isNullOrBlank()) "**$title** 일정을 찾지 못했어요."
                      else "어떤 일정을 수정할지 확인하지 못했어요."

        val newStartIso = changesMap["start_dt"]
        if (newStartIso != null) {
            val newStart = newStartIso.parseIso()
            val duration = if (s.startDt != null && s.endDt != null)
                s.endDt - s.startDt else 3_600_000L
            val newEnd = changesMap["end_dt"]?.parseIso() ?: (newStart + duration)
            val conflicts = repo.getConflicting(newStart, newEnd, s.id)
            if (conflicts.isNotEmpty()) {
                val alts = ConflictChecker.suggest(newStart, newEnd, repo, excludeId = s.id)
                pendingModify = PendingModify(
                    scheduleId = s.id,
                    originalStart = s.startDt,
                    originalEnd = s.endDt,
                    changes = changesMap,
                )
                conflictAlts = alts
                return "⚠️ **${s.title}** 수정 시간이 **${conflicts.joinToString("·") { it.title }}**과 겹쳐요.\n" +
                    formatAlts(alts) + "\n또는 '취소'를 입력하면 변경하지 않을게요."
            }
        }

        val updated = applyChanges(s, changesMap)
        repo.update(updated)
        if (updated.notificationAt != null && !updated.notificationSent)
            NotificationHelper.scheduleReminder(context, updated)

        return if (changesMap["status"] == "done") "👏 **${s.title}** 완료로 기록했어요!"
               else "✅ **${s.title}** 일정을 수정했어요."
    }

    private suspend fun handleCancel(extract: JsonObject): String {
        val sid = extract.get("schedule_id")?.takeIf { !it.isJsonNull }?.runCatching { asLong }?.getOrNull()
        val title = extract.str("title")
        val s = repo.findSchedule(sid, title)
            ?: return if (!title.isNullOrBlank()) "**$title** 일정을 찾지 못했어요."
                      else "어떤 일정을 취소할지 확인하지 못했어요."
        repo.update(s.copy(status = "cancelled"))
        return "🗑 **${s.title}** 일정을 취소했어요."
    }

    // ── 컨텍스트 빌드 ─────────────────────────────────────────────────────────

    private suspend fun buildSystem(): String {
        val now = System.currentTimeMillis()
        val kst = now.toKst()
        val todayStr = "${kst.year}년 ${kst.monthValue}월 ${kst.dayOfMonth}일 ${kst.dayOfWeek}"

        val todayStart = kst.withHour(0).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()
        val todayEnd = todayStart + 86_399_999L

        val todayRows = repo.getForRange(todayStart, todayEnd)
        val upcomingRows = repo.getUpcoming(8)  // DAO가 이미 now 이후만 반환
        val memories = repo.getAllMemories()

        val memoriesCtx = if (memories.isEmpty()) "없음"
        else memories.joinToString("\n") { "- ${it.content}" }

        val ctx = buildString {
            if (todayRows.isNotEmpty()) {
                appendLine("오늘 일정:")
                for (s in todayRows) {
                    val t = s.startDt?.let {
                        val z = it.toKst()
                        String.format("%02d:%02d", z.hour, z.minute)
                    } ?: "종일"
                    val imp = if (s.importance == "high") "⭐" else ""
                    val loc = if (!s.location.isNullOrBlank()) " ${s.location}" else ""
                    appendLine("  [id=${s.id}] $t ${s.title}$imp$loc")
                }
            }
            val upcoming = upcomingRows.filter { it.startDt != null && it.startDt > todayEnd }
            if (upcoming.isNotEmpty()) {
                appendLine("다가오는 일정:")
                for (s in upcoming) {
                    val d = s.dDay
                    val dday = if (d != null && d > 0) "D-$d " else if (d == 0) "오늘 " else ""
                    val imp = if (s.importance == "high") "⭐" else ""
                    appendLine("  [id=${s.id}] $dday${s.title}$imp")
                }
            }
        }.trim().ifBlank { "저장된 일정 없음" }

        return SYSTEM_PROMPT
            .replace("{today}", todayStr)
            .replace("{context}", ctx)
            .replace("{memories}", memoriesCtx)
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────────

    private fun parseResponse(raw: String): JsonObject? {
        var text = raw.trim()
        if ("```" in text) {
            text = text.substringAfter("```").let {
                if (it.startsWith("json")) it.drop(4) else it
            }.substringBefore("```")
        }
        return runCatching { gson.fromJson(text.trim(), JsonObject::class.java) }.getOrNull()
    }

    private fun applyChanges(s: ScheduleEntity, changes: Map<String, String?>): ScheduleEntity {
        var updated = s
        changes.forEach { (k, v) ->
            updated = when (k) {
                "title"    -> updated.copy(title = v ?: updated.title)
                "start_dt" -> updated.copy(startDt = v?.parseIso())
                "end_dt"   -> updated.copy(endDt = v?.parseIso())
                "location" -> updated.copy(location = v)
                "status"   -> updated.copy(status = v ?: updated.status)
                "notes"    -> updated.copy(notes = v)
                "importance" -> updated.copy(importance = v ?: updated.importance)
                "category"  -> updated.copy(category = v)
                else        -> updated
            }
        }
        // 시작 시간 바뀌면 알림 재계산
        if ("start_dt" in changes && updated.startDt != null) {
            updated = updated.copy(
                notificationAt = updated.startDt - 30 * 60_000L,
                notificationSent = false,
            )
        }
        return updated
    }

    private fun clearPending() {
        pendingSchedule = null
        pendingModify = null
        conflictAlts = emptyList()
    }

    private fun String.parseIso(): Long =
        runCatching { ZonedDateTime.parse(this, isoFmt).toInstant().toEpochMilli() }
            .getOrElse { toLongOrNull() ?: 0L }

    private fun fmtTime(epochMillis: Long): String {
        val z = epochMillis.toKst()
        val ampm = if (z.hour < 12) "오전" else "오후"
        val h = if (z.hour % 12 == 0) 12 else z.hour % 12
        return "${z.monthValue}월 ${z.dayOfMonth}일 $ampm ${h}:${String.format("%02d", z.minute)}"
    }

    private fun dDayText(epochMillis: Long): String {
        val today = ZonedDateTime.now(ScheduleEntity.KST).toLocalDate()
        val schedDate = epochMillis.toKst().toLocalDate()
        return when (val d = ChronoUnit.DAYS.between(today, schedDate).toInt()) {
            0    -> " (오늘!)"
            1    -> " (내일)"
            in 2..999 -> " (D-$d)"
            else -> ""
        }
    }

    private fun formatAlts(alts: List<Long>): String {
        val labels = listOf("1️⃣", "2️⃣", "3️⃣")
        return alts.take(3).mapIndexed { i, ms ->
            val z = ms.toKst()
            val ampm = if (z.hour < 12) "오전" else "오후"
            val h = if (z.hour % 12 == 0) 12 else z.hour % 12
            "${labels[i]} ${z.monthValue}월 ${z.dayOfMonth}일 $ampm $h:${String.format("%02d", z.minute)}"
        }.joinToString("\n")
    }

    private fun buildLabel(
        from: Long, to: Long, keyword: String?, importance: String?,
        todayStart: Long, todayEnd: Long,
    ): String {
        if (!keyword.isNullOrBlank()) return "'$keyword' 검색"
        if (importance == "high") return "중요 일정"
        val f = from.toKst().toLocalDate()
        val t = to.toKst().toLocalDate()
        val today = ZonedDateTime.now(ScheduleEntity.KST).toLocalDate()
        return if (f == t) when (f) {
            today -> "오늘"
            today.plusDays(1) -> "내일"
            today.minusDays(1) -> "어제"
            else -> "${f.monthValue}월 ${f.dayOfMonth}일"
        } else "${f.monthValue}/${f.dayOfMonth}~${t.monthValue}/${t.dayOfMonth}"
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    fun clearHistory() { messages.clear() }
}

// ── 시스템 프롬프트 ────────────────────────────────────────────────────────────

private val SYSTEM_PROMPT = """
당신은 Aurora — 사용자의 초개인화 AI 일정관리 비서입니다.
오늘(KST): {today}

사용자를 깊이 이해하고 기억하며, 마치 오래된 비서처럼 맥락을 파악해 먼저 챙겨줍니다.
문자나 메시지를 공유하면 일정을 파악해 저장하고, 겹치는 일정은 조율하며,
물어보면 한눈에 보여줍니다. 말투는 친근하고 간결하게, 불필요한 인사말 없이.

━━ 처리 가능한 인텐트 ━━

add_schedule      — 일정 추가 (문자 공유, 직접 입력)
view_schedule     — 일정 조회 (오늘/내일/이번주/다음주/특정날짜)
check_dday        — D-day 확인 (다가오는 중요 일정)
modify_schedule   — 일정 수정 (시간·장소·제목 변경)
cancel_schedule   — 일정 취소·삭제
conflict_resolve  — 충돌 조율 (대안 시간 선택: "1", "2", "3" 또는 자연어)
chat              — 일반 대화, 질문, 안부

━━ 응답 형식 (반드시 JSON만, 다른 텍스트 없음) ━━

{"intent": "add_schedule", "reply": "한국어 응답", "extract": {}, "memories": []}

━━ intent별 extract 구조 ━━

add_schedule: {"schedules": [{"title": "제목", "start_dt": "2024-03-15T14:00:00+09:00", "end_dt": "...", "all_day": false, "location": null, "importance": "high|medium|low", "category": "work|health|social|personal|study|other", "notes": null}], "ambiguous": false, "clarification_needed": null}
view_schedule: {"date_from": "2024-03-15T00:00:00+09:00", "date_to": "2024-03-21T23:59:59+09:00", "keyword": null, "importance": null}
← "이번주" = 이번 월요일~일요일, "다음주" = 다음 월요일~일요일
check_dday: {}
modify_schedule: {"schedule_id": 42, "title": "치과", "changes": {"start_dt": "...", "location": "..."}}
cancel_schedule: {"schedule_id": 42, "title": "치과"}
conflict_resolve: {"chosen_index": 0}  ← -1이면 취소
chat: {}

━━ 사용자 장기 기억 ━━
{memories}

━━ 현재 일정 현황 ━━
{context}

━━ 규칙 ━━
- 상대적 날짜는 오늘 기준으로 정확히 계산
- KST(UTC+9) 기준 ISO 8601 datetime
- 중요도: 면접·시험·계약·수술=high / 점심·모임=medium / 기타=low
- 컨텍스트에 [id=N] 형식으로 ID가 표시됨 — 수정/취소 시 반드시 이 ID 사용
- "완료했어"/"다녀왔어" → modify_schedule changes: {"status": "done"}
- 과거 일정 조회는 done 상태도 포함
- memories 배열: 대화 중 새로 파악한 사용자 정보(이름·직업·선호도·인간관계·습관 등)를
  간결하게 추가. 이미 장기 기억에 있는 내용은 제외. 없으면 빈 배열 [].
  예: ["이름: 김민준", "직업: 대학생 (청주대)", "오전 9시 이전 약속 싫어함"]
""".trimIndent()
