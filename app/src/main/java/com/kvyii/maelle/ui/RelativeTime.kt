package com.kvyii.maelle.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formats a provider's chapter-release string as a relative time — "7 hours
 * ago", "3 days ago" — for anything within the last 7 days, and an absolute
 * date (e.g. "5 Jan 2024") beyond that. Provider date formats vary wildly, so
 * we try several parsers; if none match we fall back to the raw string (which
 * is often already relative, like "2 days ago").
 */
object RelativeTime {

    // Absolute-date output for anything older than a week.
    private val outputDate = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

    // Common formats seen across providers.
    private val parsers: List<SimpleDateFormat> = listOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
        "MMM d, yyyy",
        "MMMM d, yyyy",
        "d MMM yyyy",
        "dd/MM/yyyy",
        "MM/dd/yyyy",
    ).map { SimpleDateFormat(it, Locale.ENGLISH) }

    fun format(raw: String?, now: Long = System.currentTimeMillis()): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        val time = parse(text) ?: return text // fall back to the raw string
        val diff = now - time
        if (diff < 0) return outputDate.format(Date(time)) // future/clock skew

        val days = TimeUnit.MILLISECONDS.toDays(diff)
        if (days > 7) return outputDate.format(Date(time))

        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
        return when {
            days >= 1 -> "$days ${plural(days, "day")} ago"
            hours >= 1 -> "$hours ${plural(hours, "hour")} ago"
            minutes >= 1 -> "$minutes ${plural(minutes, "minute")} ago"
            else -> "just now"
        }
    }

    private fun parse(text: String): Long? {
        for (parser in parsers) {
            try {
                return parser.parse(text)?.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun plural(n: Long, unit: String) = if (n == 1L) unit else "${unit}s"
}
