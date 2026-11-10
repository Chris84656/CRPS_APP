package cn.ntit.crps_compose.data

import org.json.JSONObject

internal class TelemetryJson(private val json: JSONObject) {
    val valid: Boolean? = if (json.has("valid")) (json.get("valid") as? Boolean
        ?: error("Invalid quality flag")) else null
    val sequence: Long? = if (json.has("seq")) integer("seq", 0, 0xFFFFFFFFL) else null

    init {
        if (json.has("proto")) require(integer("proto", 1, 1) == 1L)
    }

    fun number(key: String): Float? {
        val value = json.get(key)
        if (valid == false && value === JSONObject.NULL) return null
        require(value is Number && value.toDouble().isFinite() && value.toFloat().isFinite()) { "Invalid $key" }
        return value.toFloat()
    }

    fun integer(key: String, min: Long, max: Long, nullable: Boolean = false): Long? {
        val value = json.get(key)
        if (nullable && valid == false && value === JSONObject.NULL) return null
        require(value is Number) { "Invalid $key" }
        val number = value.toDouble()
        require(number.isFinite() && number >= min && number <= max && number == value.toLong().toDouble()) { "Invalid $key" }
        return value.toLong()
    }

    fun text(key: String): String? {
        val value = json.get(key)
        if (valid == false && value === JSONObject.NULL) return null
        require(value is String && value.length <= 128) { "Invalid $key" }
        return value
    }
}
