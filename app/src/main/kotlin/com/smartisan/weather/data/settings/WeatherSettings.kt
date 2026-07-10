package com.smartisan.weather.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.weatherDataStore by preferencesDataStore(name = "weather_settings")

/**
 * 应用级设置仓库。
 *
 * DataStore 是唯一持久化来源；[tempUnit] 同时提供同步可读的内存快照，供原版 View
 * 在绘制和动画期间读取，避免再维护一份 SharedPreferences 状态。
 */
class WeatherSettings private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences = appContext.weatherDataStore.data.catch { error ->
        if (error is IOException) {
            emit(emptyPreferences())
        } else {
            throw error
        }
    }

    val tempUnit: StateFlow<Int> = preferences
        .map { it[KEY_TEMP_UNIT] ?: UNIT_CELSIUS }
        .stateIn(scope, SharingStarted.Eagerly, UNIT_CELSIUS)

    val isCelsius: Flow<Boolean> = tempUnit.map { it == UNIT_CELSIUS }

    val privacyAccepted: Flow<Boolean> = preferences
        .map { it[KEY_PRIVACY_ACCEPTED] ?: false }

    suspend fun setTempUnit(unit: Int) {
        require(unit == UNIT_CELSIUS || unit == UNIT_FAHRENHEIT)
        appContext.weatherDataStore.edit { it[KEY_TEMP_UNIT] = unit }
    }

    fun setTempUnitAsync(unit: Int) {
        scope.launch { setTempUnit(unit) }
    }

    suspend fun setPrivacyAccepted(accepted: Boolean) {
        appContext.weatherDataStore.edit { it[KEY_PRIVACY_ACCEPTED] = accepted }
    }

    companion object {
        val KEY_TEMP_UNIT = intPreferencesKey("temp_unit")
        private val KEY_PRIVACY_ACCEPTED = booleanPreferencesKey("privacy_accepted")
        const val UNIT_CELSIUS = 1
        const val UNIT_FAHRENHEIT = 2

        @Volatile
        private var instance: WeatherSettings? = null

        fun getInstance(context: Context): WeatherSettings =
            instance ?: synchronized(this) {
                instance ?: WeatherSettings(context).also { instance = it }
            }
    }
}
