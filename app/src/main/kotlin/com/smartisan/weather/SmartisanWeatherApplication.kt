package com.smartisan.weather

import android.app.Application

/**
 * 应用入口。
 *
 * 天气应用不需要复杂的全局初始化，Room 和 DataStore 都在各自的单例中懒加载。
 */
class SmartisanWeatherApplication : Application()
