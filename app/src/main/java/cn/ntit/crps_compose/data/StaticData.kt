package cn.ntit.crps_compose.data

import org.json.JSONObject

/**
 * 静态电源信息：连接后周期性上报的 JSON。
 */
data class StaticData(
    val mid: String?,
    val mm: String?,
    val msn: String?,
    val mloc: String?,
    val mdate: String?,
    val mrev: String?,
    val prev: Int?,
    val rimax: Float?,
    val rpmax: Float?,
    val rt1: Float?,
    val rt2: Float?,
    val valid: Boolean?,
    val sequence: Long?,
) {
    val midDisplay: String get() = clean(mid)
    val mmDisplay: String get() = clean(mm)
    val msnDisplay: String get() = clean(msn)
    val mlocDisplay: String get() = clean(mloc)
    val mdateDisplay: String get() = clean(mdate)
    val mrevDisplay: String get() = clean(mrev)

    val prevDisplay: String get() = when (prev) {
        0 -> "V1.0"
        17 -> "V1.1"
        34 -> "V1.2"
        51 -> "V1.3"
        null, 255 -> "未知"
        else -> String.format("(0x%02X)", prev)
    }

    /** 去掉 # 和 0xFF 填充，空则返回"未知" */
    private fun clean(s: String?): String {
        if (s == null) return "未知"
        val cleaned = s.replace("#", "").replace("\u00FF", "").trim()
        return cleaned.ifEmpty { "未知" }
    }

    companion object {
        fun parse(json: JSONObject): StaticData {
            val packet = TelemetryJson(json)
            return with(packet) { StaticData(
                mid = text("mid"), mm = text("mm"), msn = text("msn"),
                mloc = text("mloc"), mdate = text("mdate"), mrev = text("mrev"),
                prev = integer("prev", 0, 255, true)?.toInt(),
                rimax = number("rimax"), rpmax = number("rpmax"),
                rt1 = number("rt1"), rt2 = number("rt2"),
                valid = valid, sequence = sequence,
            ) }
        }
    }
}
