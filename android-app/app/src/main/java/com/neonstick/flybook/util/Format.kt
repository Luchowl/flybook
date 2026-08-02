package com.neonstick.flybook.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Format {

    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun date(millis: Long): String = dateFmt.format(Date(millis))

    fun iso(millis: Long): String = isoFmt.format(Date(millis))

    fun parseIso(dateStr: String): Long? = try {
        isoFmt.parse(dateStr)?.time
    } catch (e: Exception) {
        null
    }

    fun year(millis: Long): Int =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
            .get(Calendar.YEAR)

    fun month(millis: Long): Int =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
            .get(Calendar.MONTH)

    fun distance(km: Double): String =
        if (km <= 0) "—"
        else "%,d km".format(Locale.getDefault(), Math.round(km))

    fun duration(minutes: Int): String {
        if (minutes <= 0) return "—"
        val h = minutes / 60
        val m = minutes % 60
        return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    fun time(hour: Int, minute: Int): String =
        if (hour < 0) "—" else "%02d:%02d".format(Locale.US, hour, minute)

    fun code(iata: String, icao: String): String =
        if (iata.isNotEmpty()) iata else if (icao.isNotEmpty()) icao else "?"
}
