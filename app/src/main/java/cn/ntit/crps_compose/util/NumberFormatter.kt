package cn.ntit.crps_compose.util

import java.util.Locale

/** 数值格式化工具：与旧版 CRPS Monitor 输出格式保持一致 */
object NumberFormatter {

    fun voltage(v: Float): String = String.format(Locale.US, "%.1f", v)

    fun current(a: Float): String = String.format(Locale.US, "%.2f", a)

    fun power(w: Float): String = String.format(Locale.US, "%.1f", w)

    fun efficiency(eff: Int): String = eff.toString()

    fun temperature(t: Float): String = String.format(Locale.US, "%.1f", t)

    fun fanSpeed(rpm: Int): String = rpm.toString()

    fun runtime(seconds: Long): String {
        if (seconds <= 0) return "0秒"
        val days = seconds / 86400
        val hours = seconds % 86400 / 3600
        val minutes = seconds % 3600 / 60
        val secs = seconds % 60
        return buildString {
            if (days > 0) append(days).append("天")
            if (hours > 0) append(hours).append("时")
            if (minutes > 0) append(minutes).append("分")
            if (secs > 0 || isEmpty()) append(secs).append("秒")
        }
    }

    fun hex(value: Int): String = String.format(Locale.US, "0x%04X", value and 0xFFFF)
}