package com.scottsx.app.ui.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Format a price in UGX with the "UGX" prefix and thousands
 * separators (e.g. "UGX 1,150,000").
 */
fun formatUgx(amount: Long): String {
    val nf = NumberFormat.getInstance(Locale("en", "US"))
    nf.maximumFractionDigits = 0
    nf.minimumFractionDigits = 0
    return "UGX " + nf.format(amount)
}

/**
 * Parse a "1:23:45" style duration into total seconds.
 * Used by the Flash Deals countdown timer.
 */
fun parseHmsToSeconds(text: String): Int? {
    val parts = text.split(":")
    if (parts.size != 3) return null
    return runCatching {
        parts[0].trim().toInt() * 3600 +
                parts[1].trim().toInt() * 60 +
                parts[2].trim().toInt()
    }.getOrNull()
}

/** Format a seconds value as "HH:MM:SS". */
fun secondsToHms(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return "%02d : %02d : %02d".format(h, m, ss)
}