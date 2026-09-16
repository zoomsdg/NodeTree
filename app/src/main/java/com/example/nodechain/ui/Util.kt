package com.example.nodechain.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 今天只显示时分，今年显示月日，再往前带上年份。 */
fun formatTime(millis: Long): String {
    if (millis <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val pattern = when {
        now.get(Calendar.YEAR) != then.get(Calendar.YEAR) -> "yyyy年M月d日"
        now.get(Calendar.DAY_OF_YEAR) != then.get(Calendar.DAY_OF_YEAR) -> "M月d日 HH:mm"
        else -> "今天 HH:mm"
    }
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(millis))
}
