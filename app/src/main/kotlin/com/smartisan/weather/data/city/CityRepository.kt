package com.smartisan.weather.data.city

import android.content.Context
import com.smartisan.weather.data.model.HotCity
import com.smartisan.weather.data.model.SavedCity
import com.smartisan.weather.data.model.SearchResultCity
import com.smartisan.weather.data.weather.WeatherApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 城市仓库——管理已保存城市的增删改查 + 城市搜索。
 */
class CityRepository(context: Context) {

    private val dao = WeatherDatabase.getInstance(context).savedCityDao()
    private val apiClient = WeatherApiClient.getInstance()

    /** 观察所有已保存城市 */
    val savedCities: Flow<List<SavedCity>> = dao.observeAll().map { entities ->
        entities.map { it.toModel() }
    }

    /** 获取全部已保存城市 Key。 */
    suspend fun getAllCityKeys(): Set<String> = withContext(Dispatchers.IO) {
        dao.getAllKeys().toSet()
    }

    /** 添加城市（检查是否已存在） */
    suspend fun addCity(
        city: SearchResultCity,
        insertAfterKey: String? = null,
    ): AddResult = withContext(Dispatchers.IO) {
        when (
            dao.insertRegularCity(
                entity = SavedCityEntity(
                    locationKey = city.cityId,
                    locationName = city.county.ifBlank { city.city },
                    locationParentName = city.city,
                    country = city.country,
                    province = city.province,
                    sortOrder = 0,
                ),
                maxCities = MAX_CITIES,
                insertAfterKey = insertAfterKey,
            )
        ) {
            InsertRegularCityResult.SUCCESS -> AddResult.SUCCESS
            InsertRegularCityResult.ALREADY_EXISTS -> AddResult.ALREADY_EXISTS
            InsertRegularCityResult.LIMIT_EXCEEDED -> AddResult.LIMIT_EXCEEDED
        }
    }

    /** 添加定位城市 */
    suspend fun setLocationCity(
        cityId: String,
        name: String,
        parentName: String,
        province: String,
        country: String,
    ): AddResult = withContext(Dispatchers.IO) {
        val replaced = dao.replaceLocationCity(
            entity =
                SavedCityEntity(
                    locationKey = cityId,
                    locationName = name,
                    locationParentName = parentName,
                    country = country,
                    province = province,
                    sortOrder = 1,
                ),
            maxCities = MAX_CITIES,
        )
        if (replaced) AddResult.SUCCESS else AddResult.LIMIT_EXCEEDED
    }

    /** 删除城市 */
    suspend fun deleteCity(key: String) = withContext(Dispatchers.IO) {
        dao.deleteByKey(key)
    }

    /** 更新排序 */
    suspend fun updateSortOrder(key: String, order: Int) = withContext(Dispatchers.IO) {
        dao.updateSortOrder(key, order)
    }

    /** 按城市管理页的最终拖拽顺序原子更新 Room。 */
    suspend fun updateCityOrder(cities: List<SavedCity>) = withContext(Dispatchers.IO) {
        dao.updateCityOrder(
            orderedKeys = cities.map(SavedCity::locationKey),
            locationCityKey = cities.firstOrNull(SavedCity::isLocationCity)?.locationKey,
        )
    }

    /** 交换两个城市的排序 */
    suspend fun swapSortOrder(city1: SavedCity, city2: SavedCity) = withContext(Dispatchers.IO) {
        dao.updateSortOrder(city1.locationKey, city2.sortOrder)
        dao.updateSortOrder(city2.locationKey, city1.sortOrder)
    }

    /** 缓存天气数据 */
    suspend fun cacheWeather(key: String, json: String) = withContext(Dispatchers.IO) {
        dao.updateWeather(key, json, System.currentTimeMillis())
    }

    /** 获取缓存的天气 JSON */
    suspend fun getCachedWeather(key: String): String? = withContext(Dispatchers.IO) {
        dao.getWeatherJsonByKey(key)
    }

    /** 搜索城市。小米中国区接口一次返回完整结果，不提供分页。 */
    suspend fun searchCities(query: String): List<SearchResultCity> = withContext(Dispatchers.IO) {
        apiClient.searchCities(query)
    }

    /** 搜索城市，保留请求失败信息供搜索页区分错误态与空结果。 */
    suspend fun searchCitiesResult(query: String): Result<List<SearchResultCity>> =
        withContext(Dispatchers.IO) {
            apiClient.searchCitiesResult(query)
        }

    /** 用天气服务的坐标反查接口直接取得 canonical `weathercn` 城市。 */
    suspend fun resolveCityByCoordinates(
        latitude: Double,
        longitude: Double,
    ): Result<SearchResultCity?> = withContext(Dispatchers.IO) {
        apiClient.resolveCityByCoordinatesResult(latitude, longitude)
    }

    /** 城市数量 */
    suspend fun cityCount(): Int = withContext(Dispatchers.IO) { dao.count() }

    /** 检查城市是否已添加 */
    suspend fun isCityAdded(key: String): Boolean = withContext(Dispatchers.IO) {
        dao.existsByKey(key)
    }

    private fun SavedCitySummary.toModel(): SavedCity = SavedCity(
        id = id,
        locationKey = locationKey,
        locationName = locationName,
        locationParentName = locationParentName,
        country = country,
        province = province,
        sortOrder = sortOrder,
    )

    enum class AddResult { SUCCESS, ALREADY_EXISTS, LIMIT_EXCEEDED }

    companion object {
        const val MAX_CITIES = 36

        /** 内置热门城市列表（来自原版 hot_city_json） */
        val defaultHotCities = listOf(
            HotCity("101010100", "北京", "北京", "北京", "中国"),
            HotCity("101030100", "天津", "天津", "天津", "中国"),
            HotCity("101020100", "上海", "上海", "上海", "中国"),
            HotCity("101280101", "广州", "广州", "广东", "中国"),
            HotCity("101040100", "重庆", "重庆", "重庆", "中国"),
            HotCity("101190101", "南京", "南京", "江苏", "中国"),
            HotCity("101210101", "杭州", "杭州", "浙江", "中国"),
            HotCity("101230101", "福州", "福州", "福建", "中国"),
            HotCity("101250101", "长沙", "长沙", "湖南", "中国"),
            HotCity("101200101", "武汉", "武汉", "湖北", "中国"),
            HotCity("101310101", "海口", "海口", "海南", "中国"),
            HotCity("101270101", "成都", "成都", "四川", "中国"),
            HotCity("101110101", "西安", "西安", "陕西", "中国"),
            HotCity("101230201", "厦门", "厦门", "福建", "中国"),
            HotCity("101190401", "苏州", "苏州", "江苏", "中国"),
        )
    }
}
