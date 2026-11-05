package com.abhijith.mindlol.ui.components

import java.text.NumberFormat

/** 134 -> "2h 14m", 45 -> "45m", 120 -> "2h". */
fun formatMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/** 4120 -> "4,120" using the phone's locale. */
fun formatCount(count: Int): String = NumberFormat.getIntegerInstance().format(count)
