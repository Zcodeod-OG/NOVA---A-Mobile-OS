package com.nova.runtime.storage.search

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Date-aware helpers for document search: detects date references in queries
 * ("today's mess menu", "this month's mess menu", "menu for friday") and extracts
 * the matching plain-text section from indexed document content.
 *
 * Mirrors the day/relative-date semantics of
 * com.nova.runtime.understanding.time.NaturalLanguageTimeParser without adding a
 * cross-module dependency from storage to understanding.
 */
object DocumentDateIntelligence {
    /**
     * Resolved date scope for a query. [monthScoped] is true for "this month" /
     * "month's" style references where ranking and snippet extraction should use
     * the calendar month rather than a single day section.
     */
    data class DateTarget(
        val date: LocalDate,
        val monthScoped: Boolean = false,
    ) {
        val yearMonth: YearMonth get() = YearMonth.from(date)
    }

    /** Resolves an explicit date or month reference in [query], or null when none is present. */
    fun resolveDateTarget(query: String, today: LocalDate = LocalDate.now()): DateTarget? {
        val lower = query.lowercase()
        if (THIS_MONTH_REGEX.containsMatchIn(lower)) {
            return DateTarget(date = today, monthScoped = true)
        }
        when {
            Regex("""\btoday'?s?\b|\btonight\b""").containsMatchIn(lower) ->
                return DateTarget(today)
            Regex("""\btomorrow'?s?\b""").containsMatchIn(lower) ->
                return DateTarget(today.plusDays(1))
            Regex("""\byesterday'?s?\b""").containsMatchIn(lower) ->
                return DateTarget(today.minusDays(1))
        }
        for (day in DayOfWeek.entries) {
            val full = day.name.lowercase()
            if (Regex("""\b$full'?s?\b""").containsMatchIn(lower)) {
                return DateTarget(today.with(java.time.temporal.TemporalAdjusters.nextOrSame(day)))
            }
        }
        explicitDateRegexes(today).forEach { (regex, resolver) ->
            regex.find(lower)?.let { match ->
                resolver(match)?.let { return DateTarget(it) }
            }
        }
        return null
    }

    /** Resolves an explicit day-level date reference in [query], or null when none is present. */
    fun resolveTargetDate(query: String, today: LocalDate = LocalDate.now()): LocalDate? =
        resolveDateTarget(query, today)?.date

    /** True when the query refers to the current calendar month ("this month", "month's"). */
    fun isMonthScoped(query: String): Boolean =
        THIS_MONTH_REGEX.containsMatchIn(query.lowercase())

    /**
     * True when ranking should prefer newer documents: explicit recency words
     * ("latest", "current", "recent", "new") or bare timetable/schedule/menu
     * queries that imply "the current one" without naming a past date.
     */
    fun wantsRecencyPreference(query: String): Boolean {
        val lower = query.lowercase()
        if (RECENCY_WORD_REGEX.containsMatchIn(lower)) return true
        // Bare / current-style timetable or menu asks imply newest file.
        val asksTimetable = DocumentContentAnswerExtractor.isTimetableQuery(lower) ||
            Regex("""\b(?:timetable|time\s*table|schedule)\b""").containsMatchIn(lower)
        val asksMenu = Regex("""\b(?:mess\s+menu|menu)\b""").containsMatchIn(lower)
        return (asksTimetable || asksMenu) && resolveDateTarget(lower) == null
    }

    /**
     * Removes date words from a query so lexical/semantic matching focuses on the
     * subject ("todays mess menu" -> "mess menu", "this month's mess menu" -> "mess menu").
     * Returns the original query when stripping would leave it blank.
     */
    fun stripDateWords(query: String): String {
        val stripped = MONTH_PHRASE_REGEX.replace(query, " ")
            .let { DATE_WORD_REGEX.replace(it, " ") }
            .let { RECENCY_WORD_REGEX.replace(it, " ") }
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        return stripped.ifBlank { query }
    }

    /**
     * Extracts the section of [content] describing [targetDate] (day-name or date
     * heading through to the next day/date heading), as normalized plain text.
     * Returns null when the content has no recognizable section for the date.
     */
    fun extractDateSnippet(
        content: String,
        targetDate: LocalDate,
        maxChars: Int = DEFAULT_DATE_SNIPPET_CHARS,
    ): String? {
        if (content.isBlank()) return null
        val lower = content.lowercase()
        val start = findSectionStart(lower, targetDate) ?: return null
        val end = findSectionEnd(lower, start, targetDate)
        val raw = content.substring(start, minOf(end, start + maxChars))
        return normalize(raw).takeIf { it.isNotBlank() }
    }

    /**
     * Extracts the section of [content] for [yearMonth] (month-name heading through
     * the next month heading). Returns null when no month section is found.
     */
    fun extractMonthSnippet(
        content: String,
        yearMonth: YearMonth,
        maxChars: Int = DEFAULT_DATE_SNIPPET_CHARS,
    ): String? {
        if (content.isBlank()) return null
        val lower = content.lowercase()
        val start = findMonthSectionStart(lower, yearMonth.month) ?: return null
        val end = findMonthSectionEnd(lower, start, yearMonth.month)
        val raw = content.substring(start, minOf(end, start + maxChars))
        return normalize(raw).takeIf { it.isNotBlank() }
    }

    /**
     * Best-effort snippet for a resolved [target]: month section when month-scoped,
     * day section otherwise. Day-scoped queries never fall back to a lead snippet
     * (that mislabels Monday's header as "Today's"). Month-scoped and undated
     * queries may use a lead snippet.
     * When [mealType] is set (breakfast/lunch/dinner/snacks), narrows further to that meal.
     * When the query includes a clock range ("between 12pm and 3pm"), filters timetable
     * rows inside the day section to that window.
     *
     * HTML / OCR bodies are normalized to plain text first. Answers are grounded:
     * empty or unusable content yields null (caller must not invent a schedule).
     */
    fun extractScopedSnippet(
        content: String,
        target: DateTarget?,
        maxChars: Int = DEFAULT_DATE_SNIPPET_CHARS,
        mealType: String? = null,
        query: String? = null,
    ): String? {
        if (content.isBlank()) return null
        val plain = DocumentContentNormalizer.toPlainText(content)
        if (!DocumentContentNormalizer.isGroundedAnswerable(plain)) return null
        val meal = mealType ?: query?.let { detectMealType(it) }
        val timeRange = query?.let { DocumentContentAnswerExtractor.parseTimeRange(it) }
        val timetableQuery = query != null && DocumentContentAnswerExtractor.isTimetableQuery(query)
        val scoped = when {
            target == null -> {
                if (timetableQuery) {
                    // Undated timetable ask: prefer schedule-shaped lead, else null (no invention).
                    extractLeadSnippet(plain)?.takeIf {
                        DocumentContentNormalizer.looksLikeScheduleContent(it) ||
                            DocumentContentNormalizer.timetableStructureScore(plain) >= 6f
                    }
                } else {
                    extractLeadSnippet(plain)
                }
            }
            target.monthScoped ->
                extractMonthSnippet(plain, target.yearMonth, maxChars)
                    ?: extractLeadSnippet(plain)
            else ->
                extractDateSnippet(plain, target.date, maxChars)
                    ?: extractLooseDaySection(plain, target.date, maxChars)
                    ?: extractGridDaySnippet(plain, target.date, maxChars)
                    ?: extractOcrInlineDayLines(plain, target.date, maxChars)
                    ?: if (timetableQuery) {
                        extractBestGroundedScheduleParagraph(plain, maxChars)
                    } else {
                        null
                    }
        } ?: return null
        val timeFiltered = if (timeRange != null) {
            DocumentContentAnswerExtractor.filterByTimeRange(scoped, timeRange)
                ?: return null
        } else {
            scoped
        }
        if (meal.isNullOrBlank()) return timeFiltered.take(maxChars)
        extractMealFromSection(timeFiltered, meal, maxChars)?.let { return it }
        return extractBestMatchingParagraph(timeFiltered, query, minOf(maxChars, DEFAULT_MEAL_SNIPPET_CHARS))
            ?.takeIf { paragraph -> paragraph.contains(meal, ignoreCase = true) }
    }

    /**
     * Extract-mode entry point: scoped verbatim text with query-type char budgets.
     * Returns null when content cannot be narrowed to the requested day/meal/topic.
     */
    fun extractExactSection(
        query: String,
        content: String,
        maxChars: Int = maxCharsForQuery(query),
        displayMode: String = DISPLAY_MODE_SCOPED,
        today: LocalDate = LocalDate.now(),
    ): String? {
        if (content.isBlank()) return null
        val plain = DocumentContentNormalizer.toPlainText(content)
        if (!DocumentContentNormalizer.isGroundedAnswerable(plain)) return null
        val budget = minOf(maxChars, maxCharsForQuery(query, maxChars))
        if (displayMode == DISPLAY_MODE_VERBATIM) {
            return extractLeadSnippet(plain, budget)
                ?: normalize(plain.take(budget)).takeIf { it.isNotBlank() }
        }
        val target = resolveDateTarget(query, today)
        val meal = detectMealType(query)
        val scoped = extractScopedSnippet(
            content = plain,
            target = target,
            maxChars = budget,
            mealType = meal,
            query = query,
        ) ?: return null
        if (meal != null && extractMealFromSection(scoped, meal, budget) == null &&
            !scoped.contains(meal, ignoreCase = true)
        ) {
            return extractBestMatchingParagraph(scoped, query, budget)
        }
        return scoped.take(budget).takeIf { it.isNotBlank() }
    }

    /** Char budget by query type: menu ~150, timetable row ~250, generic extract up to 4000. */
    fun maxCharsForQuery(query: String, override: Int? = null): Int {
        override?.takeIf { it > 0 }?.let { return it }
        val lower = query.lowercase()
        return when {
            DocumentContentAnswerExtractor.isTimetableQuery(lower) -> DEFAULT_TIMETABLE_EXTRACT_CHARS
            detectMealType(lower) != null || Regex("""\b(?:mess\s+menu|menu)\b""").containsMatchIn(lower) ->
                DEFAULT_MENU_EXTRACT_CHARS
            else -> DEFAULT_EXTRACT_CHARS
        }
    }

    /**
     * User-facing message when meal/day scoping fails to narrow content.
     */
    fun narrowScopeFailureMessage(
        query: String,
        meal: String? = detectMealType(query),
        today: LocalDate = LocalDate.now(),
    ): String {
        val target = resolveDateTarget(query, today)
        val day = target?.date?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        return when {
            meal != null && day != null ->
                "Couldn't narrow to $meal for $day in the matched document"
            meal != null ->
                "Couldn't narrow to $meal in the matched document"
            day != null ->
                "Couldn't narrow to $day in the matched document"
            else ->
                "Couldn't extract a scoped section for \"$query\""
        }
    }

    private fun extractBestMatchingParagraph(
        section: String,
        query: String?,
        maxChars: Int,
    ): String? {
        val blocks = section.split(Regex("""\n\s*\n"""))
            .map { normalize(it) }
            .filter { it.isNotBlank() }
        if (blocks.isEmpty()) {
            val lines = section.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) return null
            return normalize(lines.take(6).joinToString("\n")).take(maxChars).takeIf { it.isNotBlank() }
        }
        val meal = query?.let { detectMealType(it) }
        if (meal != null) {
            blocks.firstOrNull { block -> block.contains(meal, ignoreCase = true) }
                ?.take(maxChars)
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }
        return blocks.firstOrNull()?.take(maxChars)?.takeIf { it.isNotBlank() }
    }

    /**
     * Extracts one weekday column from a grid timetable where the header row lists
     * days (Mon–Fri) and each subsequent row is `time | mon | tue | …`.
     * Returns null when [targetDate]'s weekday is not a column (e.g. Saturday on a
     * weekday-only semester grid) — caller should say so, not invent slots.
     */
    fun extractGridDaySnippet(
        content: String,
        targetDate: LocalDate,
        maxChars: Int = DEFAULT_DATE_SNIPPET_CHARS,
    ): String? {
        val plain = DocumentContentNormalizer.toPlainText(content)
        if (plain.isBlank()) return null
        val lines = plain.lines().map { it.trim() }.filter { it.isNotBlank() }
        val headerIndex = lines.indexOfFirst { line ->
            countDayTokens(line) >= MIN_GRID_DAY_COLUMNS
        }
        if (headerIndex < 0) return null
        val headerDays = orderedDayTokens(lines[headerIndex])
        if (headerDays.size < MIN_GRID_DAY_COLUMNS) return null
        val targetDay = targetDate.dayOfWeek
        val column = headerDays.indexOfFirst { it == targetDay }
        if (column < 0) return null

        val dayName = targetDay.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val rows = mutableListOf<String>()
        for (i in (headerIndex + 1) until lines.size) {
            val cells = splitGridCells(lines[i])
            if (cells.size < 2) continue
            // time + one cell per day (optionally with a leading "Time" label already stripped)
            val timeCell = cells.first()
            val dayOffset = if (cells.size >= headerDays.size + 1) 1 else 0
            val dayCellIndex = dayOffset + column
            if (dayCellIndex !in cells.indices) continue
            val dayCell = cells[dayCellIndex].trim()
            if (dayCell.isBlank()) continue
            if (isFreeSlot(dayCell)) continue
            val rowTimes = DocumentContentAnswerExtractor.extractTimesFromLine(lines[i])
            if (!TIME_IN_CELL_REGEX.containsMatchIn(timeCell) &&
                rowTimes.isEmpty() &&
                !COURSE_CODE_IN_CELL_REGEX.containsMatchIn(dayCell)
            ) {
                continue
            }
            val timeLabel = when {
                timeCell.isNotBlank() && TIME_IN_CELL_REGEX.containsMatchIn(timeCell) -> collapseSpaces(timeCell)
                rowTimes.isNotEmpty() -> DocumentContentAnswerExtractor.formatClockRange(rowTimes)
                else -> collapseSpaces(timeCell)
            }
            rows += "$timeLabel — ${collapseSpaces(dayCell)}"
            if (rows.joinToString("\n").length >= maxChars) break
        }
        if (rows.isEmpty()) return null
        return (listOf(dayName) + rows).joinToString("\n").take(maxChars)
    }

    private fun countDayTokens(line: String): Int = orderedDayTokens(line).size

    private fun orderedDayTokens(line: String): List<DayOfWeek> {
        val found = mutableListOf<DayOfWeek>()
        val lower = line.lowercase()
        // Scan left-to-right so column order matches the header.
        val regex = Regex(
            """\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tue|wed|thu|fri|sat|sun)\b""",
        )
        for (match in regex.findAll(lower)) {
            dayFromToken(match.groupValues[1])?.let { day ->
                if (day !in found) found += day
            }
        }
        return found
    }

    private fun dayFromToken(token: String): DayOfWeek? =
        when (token.lowercase()) {
            "monday", "mon" -> DayOfWeek.MONDAY
            "tuesday", "tue" -> DayOfWeek.TUESDAY
            "wednesday", "wed" -> DayOfWeek.WEDNESDAY
            "thursday", "thu" -> DayOfWeek.THURSDAY
            "friday", "fri" -> DayOfWeek.FRIDAY
            "saturday", "sat" -> DayOfWeek.SATURDAY
            "sunday", "sun" -> DayOfWeek.SUNDAY
            else -> null
        }

    private fun splitGridCells(line: String): List<String> {
        val pipeSplit = line.split('|').map { it.trim() }.filter { it.isNotBlank() }
        if (pipeSplit.size >= MIN_GRID_DAY_COLUMNS) return pipeSplit
        val tabSplit = line.split('\t').map { it.trim() }.filter { it.isNotBlank() }
        if (tabSplit.size >= MIN_GRID_DAY_COLUMNS) return tabSplit
        val spaceSplit = line.split(Regex("""\s{2,}""")).map { it.trim() }.filter { it.isNotBlank() }
        if (spaceSplit.size >= MIN_GRID_DAY_COLUMNS) return spaceSplit
        // Normalized OCR/HTML often collapses tabs to single spaces: "Time Mon Tue Wed".
        val tokenSplit = line.split(Regex("""\s+""")).map { it.trim() }.filter { it.isNotBlank() }
        if (tokenSplit.size >= MIN_GRID_DAY_COLUMNS) return tokenSplit
        return listOf(line)
    }

    /**
     * True when [snippet] contains an explicit marker for [targetDate]'s weekday —
     * used to avoid labeling a generic schedule block as "Today's lecture slots".
     */
    fun snippetContainsTargetDay(snippet: String, targetDate: LocalDate): Boolean {
        if (snippet.isBlank()) return false
        val lower = snippet.lowercase()
        return markersFor(targetDate).any { it.containsMatchIn(lower) }
    }

    /**
     * Line-based day section extraction for OCR bodies where day headers are on their
     * own line but may not match [findSectionStart] character offsets.
     */
    fun extractLooseDaySection(
        content: String,
        targetDate: LocalDate,
        maxChars: Int = DEFAULT_DATE_SNIPPET_CHARS,
    ): String? {
        val plain = DocumentContentNormalizer.toPlainText(content)
        if (plain.isBlank()) return null
        val lines = plain.lines().map { it.trim() }.filter { it.isNotBlank() }
        val targetMarkers = dayLineMarkers(targetDate.dayOfWeek)
        val startIdx = lines.indexOfFirst { line ->
            val lower = line.lowercase()
            targetMarkers.any { it.containsMatchIn(lower) } &&
                !looksLikeScheduleDataRow(line)
        }
        if (startIdx < 0) return null
        val otherDayMarkers = DayOfWeek.entries
            .filter { it != targetDate.dayOfWeek }
            .flatMap { dayLineMarkers(it) }
        var endIdx = lines.size
        for (i in (startIdx + 1) until lines.size) {
            val lower = lines[i].lowercase()
            if (otherDayMarkers.any { it.containsMatchIn(lower) } && !looksLikeScheduleDataRow(lines[i])) {
                endIdx = i
                break
            }
        }
        val section = lines.subList(startIdx, endIdx).joinToString("\n")
        return normalize(section).take(maxChars).takeIf { it.isNotBlank() }
    }

    /**
     * OCR timetable rows: course codes and clock ranges without a neat day header block.
     * Collects schedule-shaped lines that mention the target weekday inline.
     */
    fun extractOcrInlineDayLines(
        content: String,
        targetDate: LocalDate,
        maxChars: Int = DEFAULT_DATE_SNIPPET_CHARS,
    ): String? {
        val plain = DocumentContentNormalizer.toPlainText(content)
        if (plain.isBlank()) return null
        val targetTokens = dayLineMarkers(targetDate.dayOfWeek)
        val rows = plain.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line ->
                val lower = line.lowercase()
                targetTokens.any { it.containsMatchIn(lower) } && looksLikeScheduleDataRow(line)
            }
        if (rows.isEmpty()) return null
        return normalize(rows.joinToString("\n")).take(maxChars).takeIf { it.isNotBlank() }
    }

    /**
     * Best contiguous schedule-like paragraph when no day section is found.
     * Caller must not label this as a specific day's slots.
     */
    fun extractBestGroundedScheduleParagraph(
        content: String,
        maxChars: Int = DEFAULT_DATE_SNIPPET_CHARS,
    ): String? {
        val plain = DocumentContentNormalizer.toPlainText(content)
        if (!DocumentContentNormalizer.isGroundedAnswerable(plain)) return null
        val lines = plain.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        var best: String? = null
        var bestScore = 0
        var run = mutableListOf<String>()
        fun flushRun() {
            if (run.isEmpty()) return
            val score = run.sumOf { scheduleLineScore(it) }
            if (score > bestScore) {
                bestScore = score
                best = normalize(run.joinToString("\n")).take(maxChars)
            }
            run = mutableListOf()
        }
        for (line in lines) {
            if (looksLikeScheduleDataRow(line)) {
                run += line
            } else {
                flushRun()
            }
        }
        flushRun()
        return best?.takeIf { bestScore >= MIN_SCHEDULE_PARAGRAPH_SCORE && it.isNotBlank() }
    }

    private fun dayLineMarkers(day: DayOfWeek): List<Regex> {
        val full = day.name.lowercase()
        val short = day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            .lowercase()
            .removeSuffix(".")
        return listOf(
            Regex("""\b$full\b"""),
            Regex("""\b$short\b"""),
        )
    }

    private fun looksLikeScheduleDataRow(line: String): Boolean {
        val times = DocumentContentAnswerExtractor.extractTimesFromLine(line)
        val hasCourse = COURSE_CODE_IN_CELL_REGEX.containsMatchIn(line)
        val hasOcrDotTime = OCR_DOT_TIME_REGEX.containsMatchIn(line)
        return times.isNotEmpty() || hasCourse || hasOcrDotTime
    }

    private fun scheduleLineScore(line: String): Int {
        var score = 0
        if (DocumentContentAnswerExtractor.extractTimesFromLine(line).isNotEmpty()) score += 2
        if (COURSE_CODE_IN_CELL_REGEX.containsMatchIn(line)) score += 3
        if (TIME_IN_CELL_REGEX.containsMatchIn(line)) score += 1
        return score
    }

    private fun isFreeSlot(cell: String): Boolean {
        val lower = cell.lowercase().trim()
        return lower == "free" || lower == "-" || lower == "—" || lower == "n/a"
    }

    private fun collapseSpaces(value: String): String =
        value.replace(Regex("""\s+"""), " ").trim()

    private val TIME_IN_CELL_REGEX = Regex(
        """\b\d{1,2}(?::\d{2})?\s*(?:am|pm)?\b""",
        RegexOption.IGNORE_CASE,
    )

    private val COURSE_CODE_IN_CELL_REGEX = Regex("""\b[A-Z]{2,4}\d{3,4}\b""")

    private val OCR_DOT_TIME_REGEX = Regex("""\b\d{1,2}\.\d{2}\b""")

    private const val MIN_GRID_DAY_COLUMNS = 2
    private const val MIN_SCHEDULE_PARAGRAPH_SCORE = 4

    /** Detects breakfast/lunch/dinner/snacks mentions in a natural-language query. */
    fun detectMealType(query: String): String? {
        val lower = query.lowercase()
        return MEAL_TYPES.firstOrNull { meal ->
            Regex("""\b${Regex.escape(meal)}\b""").containsMatchIn(lower)
        }
    }

    /**
     * Strips date + meal + question + timetable filler words so lexical/semantic matching
     * focuses on the subject
     * ("todays dinner menu" → "mess menu",
     *  "from timetable tell me my todays lec slots between 12pm to 3pm" → "timetable").
     */
    fun stripQueryNoise(query: String): String {
        DocumentContentAnswerExtractor.extractDocumentSubject(query)?.let { subject ->
            if (DocumentContentAnswerExtractor.isTimetableQuery(query)) return subject
        }
        val stripped = stripDateWords(query)
            .let { DocumentContentAnswerExtractor.stripTimetableNoise(it) }
            .let { MEAL_WORD_REGEX.replace(it, " ") }
            .let { QUESTION_WORD_REGEX.replace(it, " ") }
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        val cleaned = stripped.ifBlank { query }
        // Bare "menu" is too weak — prefer the mess-menu subject users mean.
        return if (cleaned.equals("menu", ignoreCase = true)) "mess menu" else cleaned
    }

    /**
     * Formats a user-visible answer line, e.g.
     * "Today's dinner (Saturday): Dal Fry, Rice, Roti…"
     * or "Today's lecture slots (Saturday), 12:00–15:00: 12:00-13:00 DSP; …"
     */
    fun formatAnswer(
        query: String,
        snippet: String,
        today: LocalDate = LocalDate.now(),
        sourceFileName: String? = null,
        sourceModifiedAt: Long? = null,
    ): String {
        val grounded = DocumentContentNormalizer.toPlainText(snippet)
        if (!DocumentContentNormalizer.isGroundedAnswerable(grounded)) {
            return unreadableContentMessage(
                query = query,
                sourceFileName = sourceFileName,
                sourceModifiedAt = sourceModifiedAt,
                reason = UnreadableReason.CONTENT_UNREADABLE,
            )
        }
        val base = if (DocumentContentAnswerExtractor.isTimetableQuery(query)) {
            DocumentContentAnswerExtractor.formatTimetableAnswer(query, grounded, today)
        } else {
            formatMenuAnswer(query, grounded, today)
        }
        return withSourceAttribution(base, sourceFileName, sourceModifiedAt)
    }

    /**
     * User-facing refusal when a file matched but content is missing / unreadable /
     * has no section for the requested day. Never fabricates lecture rows.
     */
    fun unreadableContentMessage(
        query: String,
        sourceFileName: String?,
        sourceModifiedAt: Long? = null,
        reason: UnreadableReason = UnreadableReason.CONTENT_UNREADABLE,
        today: LocalDate = LocalDate.now(),
    ): String {
        val name = sourceFileName?.trim()?.takeIf { it.isNotBlank() }
        val base = when {
            name == null ->
                "Couldn't read document content reliably for \"$query\""
            reason == UnreadableReason.NO_DAY_SECTION -> {
                val day = resolveDateTarget(query, today)?.date?.dayOfWeek
                    ?.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                if (day != null) {
                    "Found $name but it has no $day schedule section I can extract"
                } else {
                    "Found $name but couldn't extract the requested day clearly"
                }
            }
            else ->
                "Found $name but couldn't read its content reliably"
        }
        return withSourceAttribution(base, sourceFileName, sourceModifiedAt)
    }

    enum class UnreadableReason {
        CONTENT_UNREADABLE,
        NO_DAY_SECTION,
    }

    /**
     * Appends " — from {file} (modified YYYY-MM-DD)" so users can verify which
     * document produced an extractive answer.
     */
    fun withSourceAttribution(
        answer: String,
        fileName: String?,
        modifiedAtMillis: Long?,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val name = fileName?.trim()?.takeIf { it.isNotBlank() } ?: return answer
        val datePart = modifiedAtMillis?.takeIf { it > 0L }?.let { millis ->
            val date = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
            " (modified ${SOURCE_DATE_FORMAT.format(date)})"
        }.orEmpty()
        return "$answer — from $name$datePart"
    }

    private fun formatMenuAnswer(
        query: String,
        snippet: String,
        today: LocalDate,
    ): String {
        val target = resolveDateTarget(query, today)
        val meal = detectMealType(query)
        val dayLabel = when {
            target == null -> null
            target.monthScoped -> target.yearMonth.month
                .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            target.date == today -> "Today"
            target.date == today.plusDays(1) -> "Tomorrow"
            target.date == today.minusDays(1) -> "Yesterday"
            else -> target.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        }
        val weekday = target?.date?.dayOfWeek
            ?.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val prefix = buildString {
            when {
                dayLabel == "Today" && meal != null ->
                    append("Today's ${meal.replaceFirstChar { it.titlecase() }}")
                dayLabel != null && meal != null ->
                    append("$dayLabel ${meal.replaceFirstChar { it.titlecase() }}")
                dayLabel == "Today" -> append("Today's menu")
                dayLabel != null -> append("$dayLabel menu")
                meal != null -> append(meal.replaceFirstChar { it.titlecase() })
                else -> append("Menu")
            }
            if (weekday != null && dayLabel == "Today") {
                append(" ($weekday)")
            }
        }
        val body = snippet
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(", ")
            .take(DEFAULT_ANSWER_CHARS)
        return "$prefix: $body"
    }

    /** Leading portion of [content] as normalized plain text (fallback snippet). */
    fun extractLeadSnippet(content: String, maxChars: Int = DEFAULT_LEAD_SNIPPET_CHARS): String? =
        normalize(content.take(maxChars)).takeIf { it.isNotBlank() }

    /**
     * Within a day/month section, extract lines for [meal] through the next meal heading.
     */
    fun extractMealFromSection(
        section: String,
        meal: String,
        maxChars: Int = DEFAULT_MEAL_SNIPPET_CHARS,
    ): String? {
        val lower = section.lowercase()
        val mealRegex = Regex("""\b${Regex.escape(meal.lowercase())}\b[:\-]?\s*""", RegexOption.IGNORE_CASE)
        val startMatch = mealRegex.find(lower) ?: return null
        val start = startMatch.range.first
        val otherMeals = MEAL_TYPES.filter { it != meal.lowercase() }
            .map { Regex("""\b${Regex.escape(it)}\b""") }
        val searchFrom = startMatch.range.last + 1
        val nextMeal = otherMeals
            .mapNotNull { marker -> marker.find(lower, searchFrom)?.range?.first }
            .minOrNull()
        val end = nextMeal ?: section.length
        return normalize(section.substring(start, minOf(end, start + maxChars)))
            .takeIf { it.isNotBlank() }
    }

    private fun findSectionStart(lowerContent: String, targetDate: LocalDate): Int? {
        markersFor(targetDate)
            .mapNotNull { marker -> marker.find(lowerContent)?.range?.first }
            .minOrNull()
            ?.let { return it }
        return null
    }

    private fun findSectionEnd(lowerContent: String, start: Int, targetDate: LocalDate): Int {
        val otherDayMarkers = DayOfWeek.entries
            .filter { it != targetDate.dayOfWeek }
            .map { Regex("""\b${it.name.lowercase()}\b""") }
        val searchFrom = start + 1
        val nextBoundary = otherDayMarkers
            .mapNotNull { marker -> marker.find(lowerContent, searchFrom)?.range?.first }
            .minOrNull()
        return nextBoundary ?: lowerContent.length
    }

    private fun findMonthSectionStart(lowerContent: String, month: Month): Int? {
        monthMarkers(month)
            .mapNotNull { marker -> marker.find(lowerContent)?.range?.first }
            .minOrNull()
            ?.let { return it }
        return null
    }

    private fun findMonthSectionEnd(lowerContent: String, start: Int, month: Month): Int {
        val otherMonthMarkers = Month.entries
            .filter { it != month }
            .flatMap { monthMarkers(it) }
        val searchFrom = start + 1
        val nextBoundary = otherMonthMarkers
            .mapNotNull { marker -> marker.find(lowerContent, searchFrom)?.range?.first }
            .minOrNull()
        return nextBoundary ?: lowerContent.length
    }

    private fun monthMarkers(month: Month): List<Regex> {
        val full = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
        val short = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            .lowercase()
            .removeSuffix(".")
        return listOf(
            Regex("""\b$full\b"""),
            Regex("""\b$short\b"""),
        )
    }

    /** Regexes matching mentions of [targetDate] in document text. */
    private fun markersFor(targetDate: LocalDate): List<Regex> {
        val dayName = targetDate.dayOfWeek.name.lowercase()
        val dayShort = targetDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            .lowercase()
            .removeSuffix(".")
        val monthFull = targetDate.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
        val monthShort = targetDate.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            .lowercase()
            .removeSuffix(".")
        val d = targetDate.dayOfMonth
        val m = targetDate.monthValue
        return listOf(
            // Full weekday ("SATURDAY") plus table abbreviations ("SAT"/"Sat").
            Regex("""\b$dayName\b"""),
            Regex("""\b$dayShort\b"""),
            Regex("""\b$d(?:st|nd|rd|th)?\s+(?:$monthFull|$monthShort)\b"""),
            Regex("""\b(?:$monthFull|$monthShort)\s+$d(?:st|nd|rd|th)?\b"""),
            Regex("""\b0?$d[/\-.]0?$m(?:[/\-.]\d{2,4})?\b"""),
            Regex("""\b${targetDate.year}-0?$m-0?$d\b"""),
        )
    }

    private fun explicitDateRegexes(today: LocalDate): List<Pair<Regex, (MatchResult) -> LocalDate?>> {
        val months = Month.entries.joinToString("|") { month ->
            val full = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
            val short = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase().removeSuffix(".")
            "$full|$short"
        }
        val dayFirst = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+($months)\b""")
        val monthFirst = Regex("""\b($months)\s+(\d{1,2})(?:st|nd|rd|th)?\b""")
        return listOf(
            dayFirst to { match: MatchResult ->
                buildDate(match.groupValues[1].toIntOrNull(), parseMonth(match.groupValues[2]), today)
            },
            monthFirst to { match: MatchResult ->
                buildDate(match.groupValues[2].toIntOrNull(), parseMonth(match.groupValues[1]), today)
            },
        )
    }

    private fun parseMonth(token: String): Month? =
        Month.entries.firstOrNull { month ->
            val full = month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()
            val short = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase().removeSuffix(".")
            token == full || token == short
        }

    private fun buildDate(day: Int?, month: Month?, today: LocalDate): LocalDate? {
        if (day == null || month == null) return null
        return runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
    }

    private fun normalize(raw: String): String =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

    /** "this month", "this month's", "this months", bare "month's". */
    private val THIS_MONTH_REGEX = Regex(
        """\bthis\s+month(?:'?s|s)?\b|\bmonth'?s\b""",
        RegexOption.IGNORE_CASE,
    )

    private val MONTH_PHRASE_REGEX = Regex(
        """\bthis\s+month(?:'?s|s)?\b|\bmonth'?s\b""",
        RegexOption.IGNORE_CASE,
    )

    private val DATE_WORD_REGEX = Regex(
        buildString {
            append("""\b(?:today'?s?|tonight|tomorrow'?s?|yesterday'?s?""")
            DayOfWeek.entries.forEach { append("|${it.name.lowercase()}'?s?") }
            append(""")\b""")
        },
        RegexOption.IGNORE_CASE,
    )

    private val RECENCY_WORD_REGEX = Regex(
        """\b(?:latest|current|recent|newest|new)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val SOURCE_DATE_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val MEAL_TYPES = listOf("breakfast", "lunch", "dinner", "snacks", "snack")

    private val MEAL_WORD_REGEX = Regex(
        """\b(?:breakfast|lunch|dinner|snacks?)\b""",
        RegexOption.IGNORE_CASE,
    )

    private val QUESTION_WORD_REGEX = Regex(
        """\b(?:what|whats|show|tell|me|is|are|the|a|an|please|on)\b""",
        RegexOption.IGNORE_CASE,
    )

    const val DEFAULT_DATE_SNIPPET_CHARS = 700
    const val DEFAULT_LEAD_SNIPPET_CHARS = 300
    const val DEFAULT_MEAL_SNIPPET_CHARS = 280
    const val DEFAULT_ANSWER_CHARS = 400
    const val DEFAULT_MENU_EXTRACT_CHARS = 150
    const val DEFAULT_TIMETABLE_EXTRACT_CHARS = 250
    const val DEFAULT_EXTRACT_CHARS = 4_000
    const val DISPLAY_MODE_SCOPED = "scoped"
    const val DISPLAY_MODE_VERBATIM = "verbatim"
}
