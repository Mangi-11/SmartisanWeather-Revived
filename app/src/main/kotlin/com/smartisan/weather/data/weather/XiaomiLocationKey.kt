package com.smartisan.weather.data.weather

/**
 * 小米天气城市标识。
 *
 * 中国城市在本地继续使用历史上稳定的九位城市码，发起请求时补成
 * `weathercn:{cityId}`；全球城市保留服务端返回的完整 `accu:` 标识。
 */
internal class XiaomiLocationKey private constructor(
    val storageKey: String,
    val requestKey: String,
    val isGlobal: Boolean,
) {
    companion object {
        fun parse(rawKey: String?): XiaomiLocationKey? {
            val key = rawKey?.trim().orEmpty()
            return when {
                CHINA_CITY_ID.matches(key) -> china(key)
                key.startsWith(WEATHERCN_PREFIX) -> {
                    val cityId = key.removePrefix(WEATHERCN_PREFIX)
                    cityId.takeIf(CHINA_CITY_ID::matches)?.let(::china)
                }

                key.startsWith(ACCU_PREFIX) -> {
                    val providerId = key.removePrefix(ACCU_PREFIX)
                    providerId.takeIf(ACCU_LOCATION_ID::matches)?.let {
                        XiaomiLocationKey(
                            storageKey = "$ACCU_PREFIX$it",
                            requestKey = "$ACCU_PREFIX$it",
                            isGlobal = true,
                        )
                    }
                }

                else -> null
            }
        }

        private fun china(cityId: String): XiaomiLocationKey = XiaomiLocationKey(
            storageKey = cityId,
            requestKey = "$WEATHERCN_PREFIX$cityId",
            isGlobal = false,
        )
    }
}

private const val WEATHERCN_PREFIX = "weathercn:"
private const val ACCU_PREFIX = "accu:"
private val CHINA_CITY_ID = Regex("\\d{9}")
private val ACCU_LOCATION_ID = Regex("[A-Za-z0-9_-]{1,128}")
