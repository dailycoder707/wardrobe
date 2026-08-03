package com.wardrobe.app.feature.stats.insights

import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Currency

internal fun relativeDateMetric(
    date: LocalDate,
    today: LocalDate,
): String {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0 -> "Today"
        days == 1L -> "Yesterday"
        else -> "$days days ago"
    }
}

internal fun wearCountMetric(count: Int): String = if (count == 1) "Worn once" else "Worn $count times"

internal fun dormantMetric(
    lastWornDate: LocalDate?,
    today: LocalDate,
): String {
    if (lastWornDate == null) return "Never worn"
    val days = ChronoUnit.DAYS.between(lastWornDate, today)
    return when {
        days <= 0 -> "Worn today"
        days == 1L -> "Worn yesterday"
        else -> "$days days since last worn"
    }
}

internal fun costPerWearMetric(
    costPerWear: Double?,
    currencyCode: String?,
): String {
    if (costPerWear == null) return "Not enough wears yet"
    return "${formatCurrency(costPerWear, currencyCode)} / wear"
}

private fun formatCurrency(
    amount: Double,
    currencyCode: String?,
): String {
    val currencyFormat =
        currencyCode?.let { code ->
            runCatching {
                NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(code) }
            }.getOrNull()
        }
    return (currencyFormat ?: NumberFormat.getNumberInstance()).format(amount)
}
