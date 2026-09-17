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

/**
 * 只到天的日期，用在空间紧张的地方（首页卡片的标题行）。
 * 跨年才带上年份，否则"9月17日"这种就够了。
 */
fun formatDate(millis: Long): String {
    if (millis <= 0L) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val pattern = if (now.get(Calendar.YEAR) != then.get(Calendar.YEAR)) "yyyy年M月d日" else "M月d日"
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(millis))
}

/** 完整日期，年月日都带。 */
fun formatFullDate(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(millis))
}
