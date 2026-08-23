package com.cyberrin.giswrap.widget

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cyberrin.giswrap.domain.model.Outcome
import com.cyberrin.giswrap.domain.repository.CityRepository
import com.cyberrin.giswrap.domain.repository.WeatherRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class WidgetRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val cities: CityRepository,
    private val weather: WeatherRepository,
    private val widget: WeatherWidget.Updater,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val city = cities.primary() ?: return Result.success()
        return when (weather.currentWeather(city.urlPath, refresh = true)) {
            is Outcome.Ok -> {
                widget.refresh()
                Result.success()
            }

            is Outcome.Failed -> Result.retry()
        }
    }

    companion object {
        private const val NAME = "widget-refresh"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
