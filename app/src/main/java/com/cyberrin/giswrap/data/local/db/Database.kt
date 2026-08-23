package com.cyberrin.giswrap.data.local.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.model.CurrentWeather
import com.cyberrin.giswrap.domain.model.DailyForecast
import com.cyberrin.giswrap.domain.model.Forecast
import com.cyberrin.giswrap.domain.model.ForecastOrigin
import com.cyberrin.giswrap.domain.model.HourlyForecast
import com.cyberrin.giswrap.domain.model.Period
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Entity(tableName = "saved_city")
data class SavedCityEntity(
    @PrimaryKey val urlPath: String,
    val cityId: Int?,
    val name: String,
    val slug: String,
    val country: String,
    val district: String,
    val latitude: Double?,
    val longitude: Double?,
    val position: Int,
    val primary: Boolean = false,
)

@Entity(tableName = "forecast_cache")
data class ForecastCacheEntity(
    @PrimaryKey val key: String,
    // ponytail: opaque blob; normalise if a query ever needs to reach inside a day.
    val payload: String,
    val fetchedAtMillis: Long,
    val fetchedAtLocal: String,
)

@Serializable
data class CachedForecast(
    val city: String,
    val cityId: String,
    val period: String,
    val origin: String,
    val days: List<CachedDay>,
)

@Serializable
data class CachedDay(
    val date: String,
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    val pressureMin: Int? = null,
    val pressureMax: Int? = null,
    val humidity: Int? = null,
    val windSpeedMax: Double? = null,
    val windGust: Double? = null,
    val precipitationMm: Double? = null,
    val description: String? = null,
    val icon: String? = null,
    val hours: List<CachedHour> = emptyList(),
)

@Serializable
data class CachedHour(
    val valid: String,
    val temperature: Double? = null,
    val feelsLike: Double? = null,
    val pressure: Int? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val windGust: Double? = null,
    val windDirection: Int? = null,
    val cloudiness: Int? = null,
    val description: String? = null,
    val icon: String? = null,
)

@Serializable
data class CachedCurrent(
    val city: String,
    val cityId: String,
    val temperature: Double? = null,
    val feelsLike: Double? = null,
    val description: String? = null,
    val humidity: Int? = null,
    val pressure: Int? = null,
    val windSpeed: Double? = null,
    val icon: String? = null,
)

@Dao
interface SavedCityDao {
    @Query("SELECT * FROM saved_city ORDER BY position ASC")
    fun observeAll(): Flow<List<SavedCityEntity>>

    @Query("SELECT * FROM saved_city ORDER BY position ASC")
    suspend fun all(): List<SavedCityEntity>

    @Query("SELECT * FROM saved_city ORDER BY `primary` DESC, position ASC LIMIT 1")
    fun observePrimary(): Flow<SavedCityEntity?>

    @Query("SELECT `primary` FROM saved_city WHERE `primary` = 1 LIMIT 1")
    fun observePrimaryFlag(): Flow<Boolean?>

    @Query("SELECT urlPath FROM saved_city WHERE `primary` = 1 LIMIT 1")
    fun observePrimaryPath(): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(city: SavedCityEntity)

    @Query("DELETE FROM saved_city WHERE urlPath = :urlPath")
    suspend fun delete(urlPath: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM saved_city")
    suspend fun nextPosition(): Int

    @Query("SELECT COUNT(*) FROM saved_city")
    suspend fun count(): Int

    @Query("UPDATE saved_city SET `primary` = (urlPath = :urlPath)")
    suspend fun markPrimary(urlPath: String)

    @Transaction
    suspend fun add(city: SavedCityEntity) {
        val existing = all().any { it.urlPath == city.urlPath }
        if (!existing) upsert(city.copy(position = nextPosition()))
        if (count() == 1 || all().none { it.primary }) markPrimary(city.urlPath)
    }

    @Transaction
    suspend fun remove(urlPath: String) {
        val wasPrimary = all().firstOrNull { it.urlPath == urlPath }?.primary == true
        delete(urlPath)
        if (wasPrimary) all().firstOrNull()?.let { markPrimary(it.urlPath) }
    }
}

@Dao
interface ForecastCacheDao {
    @Query("SELECT * FROM forecast_cache WHERE `key` = :key AND fetchedAtMillis > :notBefore")
    suspend fun fresh(key: String, notBefore: Long): ForecastCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: ForecastCacheEntity)

    @Query("DELETE FROM forecast_cache WHERE `key` = :key")
    suspend fun evict(key: String)

    @Query("DELETE FROM forecast_cache WHERE fetchedAtMillis <= :notBefore")
    suspend fun sweep(notBefore: Long)
}

@Database(
    entities = [SavedCityEntity::class, ForecastCacheEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GisWrapDatabase : RoomDatabase() {
    abstract fun savedCities(): SavedCityDao
    abstract fun forecastCache(): ForecastCacheDao

    companion object {
        const val NAME = "giswrap.db"
    }
}

fun SavedCityEntity.toDomain(): City = City(
    id = cityId,
    name = name,
    slug = slug,
    country = country,
    district = district,
    urlPath = urlPath,
    latitude = latitude,
    longitude = longitude,
)

fun City.toEntity(position: Int = 0, primary: Boolean = false): SavedCityEntity = SavedCityEntity(
    urlPath = urlPath,
    cityId = id,
    name = name,
    slug = slug,
    country = country,
    district = district,
    latitude = latitude,
    longitude = longitude,
    position = position,
    primary = primary,
)

fun Forecast.toCached(): CachedForecast = CachedForecast(
    city = city,
    cityId = cityId,
    period = period.slug,
    origin = origin.label,
    days = days.map { day ->
        CachedDay(
            date = day.date.toString(),
            tempMin = day.tempMin,
            tempMax = day.tempMax,
            pressureMin = day.pressureMin,
            pressureMax = day.pressureMax,
            humidity = day.humidity,
            windSpeedMax = day.windSpeedMax,
            windGust = day.windGust,
            precipitationMm = day.precipitationMm,
            description = day.description,
            icon = day.icon,
            hours = day.hours.map { hour ->
                CachedHour(
                    valid = hour.valid.toString(),
                    temperature = hour.temperature,
                    feelsLike = hour.feelsLike,
                    pressure = hour.pressure,
                    humidity = hour.humidity,
                    windSpeed = hour.windSpeed,
                    windGust = hour.windGust,
                    windDirection = hour.windDirection,
                    cloudiness = hour.cloudiness,
                    description = hour.description,
                    icon = hour.icon,
                )
            },
        )
    },
)

fun CachedForecast.toDomain(): Forecast = Forecast(
    city = city,
    cityId = cityId,
    period = Period.entries.first { it.slug == period },
    origin = ForecastOrigin.entries.first { it.label == origin },
    days = days.map { day ->
        DailyForecast(
            date = LocalDate.parse(day.date),
            tempMin = day.tempMin,
            tempMax = day.tempMax,
            pressureMin = day.pressureMin,
            pressureMax = day.pressureMax,
            humidity = day.humidity,
            windSpeedMax = day.windSpeedMax,
            windGust = day.windGust,
            precipitationMm = day.precipitationMm,
            description = day.description,
            icon = day.icon,
            hours = day.hours.map { hour ->
                HourlyForecast(
                    valid = LocalDateTime.parse(hour.valid),
                    temperature = hour.temperature,
                    feelsLike = hour.feelsLike,
                    pressure = hour.pressure,
                    humidity = hour.humidity,
                    windSpeed = hour.windSpeed,
                    windGust = hour.windGust,
                    windDirection = hour.windDirection,
                    cloudiness = hour.cloudiness,
                    description = hour.description,
                    icon = hour.icon,
                )
            },
        )
    },
)

fun CurrentWeather.toCached(): CachedCurrent = CachedCurrent(
    city = city,
    cityId = cityId,
    temperature = temperature,
    feelsLike = feelsLike,
    description = description,
    humidity = humidity,
    pressure = pressure,
    windSpeed = windSpeed,
    icon = icon,
)

fun CachedCurrent.toDomain(): CurrentWeather = CurrentWeather(
    city = city,
    cityId = cityId,
    temperature = temperature,
    feelsLike = feelsLike,
    description = description,
    humidity = humidity,
    pressure = pressure,
    windSpeed = windSpeed,
    icon = icon,
)
