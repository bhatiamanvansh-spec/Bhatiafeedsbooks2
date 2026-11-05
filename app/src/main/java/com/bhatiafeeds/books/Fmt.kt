package com.bhatiafeeds.books

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatting helpers: Indian digit grouping (1,23,456) and dd MMM yyyy dates. */
object Fmt {
    private val indian: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    fun number(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        val nf = NumberFormat.getNumberInstance(indian)
        nf.maximumFractionDigits = 2
        nf.minimumFractionDigits = if (r % 1.0 == 0.0) 0 else 2
        return nf.format(r)
    }

    fun money(v: Double): String {
        val r = Math.round(v * 100) / 100.0
        return if (r < 0) "-₹" + number(-r) else "₹" + number(r)
    }

    fun date(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFmt)

    fun isoDate(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).toString()

    fun monthTitle(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))

    /** Parses "1234" or "1234.5"; blank or invalid gives 0. */
    fun parse(s: String): Double = s.trim().toDoubleOrNull() ?: 0.0

    /** Plain text for editing in a field: 1500.0 becomes "1500". */
    fun plain(v: Double): String {
        if (v == 0.0) return ""
        val r = Math.round(v * 100) / 100.0
        return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
    }

    fun daysAgo(today: Long, day: Long): String {
        val d = today - day
        return when {
            d <= 0 -> "today"
            d == 1L -> "yesterday"
            else -> "$d days ago"
        }
    }
}
