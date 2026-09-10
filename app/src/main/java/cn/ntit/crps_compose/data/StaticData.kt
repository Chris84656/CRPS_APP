package cn.ntit.crps_compose.data

import org.json.JSONObject

/**
 * 静态电源信息：连接后周期性上报的 JSON。
 */
data class StaticData(
    val mid: String,
    val mm: String,
    val msn: String,
    val mloc: String,
    val mdate: String,
    val mrev: String,
    val prev: Int,
    val rimax: Float,
    val rpmax: Float,
    val rt1: Float,
    val rt2: Float,
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
        255 -> "未知"
        else -> String.format("(0x%02X)", prev)
    }

    /** 去掉 # 和 0xFF 填充，空则返回"未知" */
    private fun clean(s: String): String {
        val cleaned = s.replace("#", "").replace("\u00FF", "").trim()
        return cleaned.ifEmpty { "未知" }
    }

    companion object {
        fun parse(json: JSONObject): StaticData = StaticData(
            mid = json.optString("mid"),
            mm = json.optString("mm"),
            msn = json.optString("msn"),
            mloc = json.optString("mloc"),
            mdate = json.optString("mdate"),
            mrev = json.optString("mrev"),
            prev = json.optInt("prev"),
            rimax = json.optDouble("rimax").toFloat(),
            rpmax = json.optDouble("rpmax").toFloat(),
            rt1 = json.optDouble("rt1").toFloat(),
            rt2 = json.optDouble("rt2").toFloat(),
        )
    }
}
