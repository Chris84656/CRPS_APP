package cn.ntit.crps_compose.data

import org.json.JSONObject

/**
 * 动态遥测数据：ESP32 每 250ms 通过 BLE Notify 上报的 JSON。
 */
data class DynamicData(
    val vin: Float?,
    val iin: Float?,
    val vout: Float?,
    val iout: Float?,
    val pout: Float?,
    val eff: Int?,
    val fan: Int?,
    val t1: Float?,
    val t2: Float?,
    val rt: Long,
    val pwr: Int,
    val si: Int?,
    val sn: Int?,
    val st: Int?,
    val sf: Int?,
    val valid: Boolean?,
    val sequence: Long?,
    val acknowledgement: Long?,
) {
    /** 输入视在功率，不能代替有功功率。 */
    val apparentPower: Float? get() = if (vin != null && iin != null) vin * iin else null

    val isPowerOn: Boolean get() = pwr == 1

    companion object {
        fun parse(json: JSONObject): DynamicData {
            val packet = TelemetryJson(json)
            return with(packet) { DynamicData(
                vin = number("vin"), iin = number("iin"), vout = number("vout"),
                iout = number("iout"), pout = number("pout"),
                eff = integer("eff", 0, 100, true)?.toInt(),
                fan = integer("fan", 0, 100000, true)?.toInt(),
                t1 = number("t1"), t2 = number("t2"),
                rt = requireNotNull(integer("rt", 0, 9007199254740991L)),
                pwr = requireNotNull(integer("pwr", 0, 1)).toInt(),
                si = integer("si", 0, 65535, true)?.toInt(),
                sn = integer("sn", 0, 65535, true)?.toInt(),
                st = integer("st", 0, 65535, true)?.toInt(),
                sf = integer("sf", 0, 65535, true)?.toInt(),
                valid = valid, sequence = sequence,
                acknowledgement = if (json.has("ack")) integer("ack", 0, 0xFFFFFFFFL) else null,
            ) }
        }
    }
}
