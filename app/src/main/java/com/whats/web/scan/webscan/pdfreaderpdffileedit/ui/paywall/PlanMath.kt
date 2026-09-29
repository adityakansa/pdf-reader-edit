package com.whats.web.scan.webscan.pdfreaderpdffileedit.ui.paywall

import java.text.NumberFormat
import java.util.Currency

/** The sums the premium screen shows beside each plan. Pure, so it is unit-tested. */
object PlanMath {

    /** Days in an ISO-8601 billing period ("P1Y" 365, "P1M" 30, "P1W" 7, "P3D" 3); 0 when unknown. */
    fun days(iso: String): Int {
        val amount = iso.filter { it.isDigit() }.toIntOrNull() ?: return 0
        return when (iso.lastOrNull()) {
            'Y' -> amount * 365
            'M' -> amount * 30
            'W' -> amount * 7
            'D' -> amount
            else -> 0
        }
    }

    /** Months in an ISO-8601 period, the unit stores compare plans in (a year is exactly 12 months). */
    fun months(iso: String): Double {
        val amount = iso.filter { it.isDigit() }.toIntOrNull() ?: return 0.0
        return when (iso.lastOrNull()) {
            'Y' -> amount * 12.0
            'M' -> amount.toDouble()
            'W' -> amount * 7 / DAYS_PER_MONTH
            'D' -> amount / DAYS_PER_MONTH
            else -> 0.0
        }
    }

    /** How much cheaper [longer] is than paying [shorter] for as long, in whole percent; 0 when it is not cheaper. */
    fun savingPercent(longerMicros: Long, longerIso: String, shorterMicros: Long, shorterIso: String): Int {
        val longMonths = months(longerIso)
        val shortMonths = months(shorterIso)
        if (longMonths <= 0 || shortMonths <= 0 || shorterMicros <= 0) return 0
        val perMonthLong = longerMicros / longMonths
        val perMonthShort = shorterMicros / shortMonths
        return ((1 - perMonthLong / perMonthShort) * 100).toInt().coerceAtLeast(0)
    }

    private const val DAYS_PER_MONTH = 365.0 / 12

    /** "₹11.37" — the price of one day of the plan, in its own currency. */
    fun perDay(micros: Long, days: Int, currencyCode: String): String? {
        if (micros <= 0 || days <= 0 || currencyCode.isEmpty()) return null
        val format = NumberFormat.getCurrencyInstance()
        runCatching { format.currency = Currency.getInstance(currencyCode) }.onFailure { return null }
        format.maximumFractionDigits = 2
        return format.format(micros / 1_000_000.0 / days)
    }
}
