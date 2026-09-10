package cn.ntit.crps_compose.data

import org.json.JSONObject

/**
 * 动态遥测数据：ESP32 每 250ms 通过 BLE Notify 上报的 JSON。
 */
data class DynamicData(
    val vin: Float,
    val iin: Float,
    val vout: Float,
    val iout: Float,
    val pout: Float,
    val eff: Int,
    val fan: Int,
    val t1: Float,
    val t2: Float,
    val rt: Long,
    val pwr: Int,
    val si: Int,
    val sn: Int,
    val st: Int,
    val sf: Int,
) {
    /** 输入功率（计算值） */
    val pin: Float get() = vin * iin

    val isPowerOn: Boolean get() = pwr == 1

    companion object {
        fun parse(json: JSONObject): DynamicData = DynamicData(
            vin = json.optDouble("vin").toFloat(),
            iin = json.optDouble("iin").toFloat(),
            vout = json.optDouble("vout").toFloat(),
            iout = json.optDouble("iout").toFloat(),
            pout = json.optDouble("pout").toFloat(),
            eff = json.optInt("eff"),
            fan = json.optInt("fan"),
            t1 = json.optDouble("t1").toFloat(),
            t2 = json.optDouble("t2").toFloat(),
            rt = json.optLong("rt"),
            pwr = json.optInt("pwr"),
            si = json.optInt("si"),
            sn = json.optInt("sn"),
            st = json.optInt("st"),
            sf = json.optInt("sf"),
        )
    }
}
