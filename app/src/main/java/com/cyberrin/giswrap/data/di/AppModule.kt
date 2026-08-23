package com.cyberrin.giswrap.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.cyberrin.giswrap.data.local.db.ForecastCacheDao
import com.cyberrin.giswrap.data.local.db.GisWrapDatabase
import com.cyberrin.giswrap.data.local.db.SavedCityDao
import com.cyberrin.giswrap.data.repository.CityRepositoryImpl
import com.cyberrin.giswrap.data.repository.LocationRepositoryImpl
import com.cyberrin.giswrap.data.repository.SettingsRepositoryImpl
import com.cyberrin.giswrap.data.repository.WeatherRepositoryImpl
import com.cyberrin.giswrap.domain.repository.CityRepository
import com.cyberrin.giswrap.domain.repository.LocationRepository
import com.cyberrin.giswrap.domain.repository.SettingsRepository
import com.cyberrin.giswrap.domain.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Dispatcher(val value: GisDispatcher)

enum class GisDispatcher {
    Io,
    Parsing,
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @Dispatcher(GisDispatcher.Io)
    fun io(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(GisDispatcher.Parsing)
    fun parsing(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(
        @Dispatcher(GisDispatcher.Io) dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        defaultRequest {
            header(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0",
            )
        }
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 20_000
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GisWrapDatabase =
        Room.databaseBuilder(context, GisWrapDatabase::class.java, GisWrapDatabase.NAME)

            .build()

    @Provides
    fun savedCityDao(db: GisWrapDatabase): SavedCityDao = db.savedCities()

    @Provides
    fun forecastCacheDao(db: GisWrapDatabase): ForecastCacheDao = db.forecastCache()

    @Provides
    @Singleton
    fun preferences(
        @ApplicationContext context: Context,
        @Dispatcher(GisDispatcher.Io) dispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(dispatcher + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("appearance") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun weather(impl: WeatherRepositoryImpl): WeatherRepository

    @Binds
    @Singleton
    abstract fun cities(impl: CityRepositoryImpl): CityRepository

    @Binds
    @Singleton
    abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun location(impl: LocationRepositoryImpl): LocationRepository
}
