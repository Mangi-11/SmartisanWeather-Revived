package com.smartisan.weather.data.city

import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * 已保存城市的 Room 实体。
 */
@Entity(
    tableName = "saved_cities",
    indices = [Index(value = ["locationKey"], unique = true)],
)
data class SavedCityEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val locationKey: String,
    val locationName: String,
    val locationParentName: String,
    val country: String,
    val province: String,
    val sortOrder: Int,
    /** 缓存的天气 JSON */
    val weatherJson: String? = null,
    /** 上次更新时间 */
    val lastUpdate: Long = 0L,
)

@Dao
interface SavedCityDao {
    @Query("SELECT * FROM saved_cities ORDER BY sortOrder ASC")
    fun observeAll(): Flow<List<SavedCityEntity>>

    @Query("SELECT * FROM saved_cities ORDER BY sortOrder ASC")
    suspend fun getAll(): List<SavedCityEntity>

    @Query("SELECT * FROM saved_cities WHERE locationKey = :key LIMIT 1")
    suspend fun findByKey(key: String): SavedCityEntity?

    @Query("SELECT * FROM saved_cities WHERE sortOrder = 1 LIMIT 1")
    suspend fun getLocationCity(): SavedCityEntity?

    @Query("SELECT COUNT(*) FROM saved_cities")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM saved_cities WHERE sortOrder > 1")
    suspend fun maxSortOrder(): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: SavedCityEntity): Long

    /**
     * 原子添加普通城市。
     *
     * 查重、数量上限、排序号分配和写入必须处于同一事务，否则并发搜索结果点击会
     * 同时通过前置检查，并产生重复城市、重复 [SavedCityEntity.sortOrder] 或突破上限。
     */
    @Transaction
    suspend fun insertRegularCity(
        entity: SavedCityEntity,
        maxCities: Int,
        insertAfterKey: String?,
    ): InsertRegularCityResult {
        if (findByKey(entity.locationKey) != null) {
            return InsertRegularCityResult.ALREADY_EXISTS
        }
        if (count() >= maxCities) {
            return InsertRegularCityResult.LIMIT_EXCEEDED
        }
        val insertAfterOrder = insertAfterKey?.let { findByKey(it)?.sortOrder }
        val nextOrder = insertAfterOrder?.plus(1) ?: ((maxSortOrder() ?: 1) + 1)
        shiftSortOrdersAtOrAfter(nextOrder)
        insert(entity.copy(id = 0, sortOrder = nextOrder))
        return InsertRegularCityResult.SUCCESS
    }

    @Update
    suspend fun update(entity: SavedCityEntity)

    @Query("UPDATE saved_cities SET weatherJson = :json, lastUpdate = :time WHERE locationKey = :key")
    suspend fun updateWeather(key: String, json: String, time: Long)

    @Query("DELETE FROM saved_cities WHERE locationKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("UPDATE saved_cities SET sortOrder = :order WHERE locationKey = :key")
    suspend fun updateSortOrder(key: String, order: Int)

    @Query("UPDATE saved_cities SET sortOrder = sortOrder + 1 WHERE sortOrder >= :order")
    suspend fun shiftSortOrdersAtOrAfter(order: Int)

    /** 原子替换定位城市；只有在确实新增一行时才受城市数量上限约束。 */
    @Transaction
    suspend fun replaceLocationCity(entity: SavedCityEntity, maxCities: Int): Boolean {
        val previousLocation = getLocationCity()
        val targetCity = findByKey(entity.locationKey)
        val targetAlreadyExists = targetCity != null
        if (previousLocation == null && !targetAlreadyExists && count() >= maxCities) {
            return false
        }
        previousLocation?.let { deleteByKey(it.locationKey) }
        deleteByKey(entity.locationKey)
        insert(
            entity.copy(
                weatherJson = targetCity?.weatherJson,
                lastUpdate = targetCity?.lastUpdate ?: 0L,
            )
        )
        return true
    }

    /** 一次事务提交完整顺序，避免 Flow 观察到重复 sortOrder 的中间状态。 */
    @Transaction
    suspend fun updateCityOrder(orderedKeys: List<String>, locationCityKey: String?) {
        var nextRegularOrder = 2
        orderedKeys.forEach { key ->
            val order = if (key == locationCityKey) 1 else nextRegularOrder++
            updateSortOrder(key, order)
        }
    }

    @Query("SELECT * FROM saved_cities WHERE locationKey = :key LIMIT 1")
    fun observeByKey(key: String): Flow<SavedCityEntity?>
}

enum class InsertRegularCityResult {
    SUCCESS,
    ALREADY_EXISTS,
    LIMIT_EXCEEDED,
}

@Database(entities = [SavedCityEntity::class], version = 2, exportSchema = true)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun savedCityDao(): SavedCityDao

    companion object {
        @Volatile
        private var instance: WeatherDatabase? = null

        fun getInstance(context: android.content.Context): WeatherDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder<WeatherDatabase>(
                    context.applicationContext,
                    "weather.db",
                )
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    // 新项目不承诺开发期 schema 的历史兼容；遇到旧缓存直接重建。
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
