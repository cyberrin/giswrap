package com.cyberrin.giswrap.domain.model

// A type, never a sentence: domain/ compiles without Android on its classpath, so
// it cannot reach a string resource, and prose built here could only be one
// language. Mapped to text at the UI edge, in ui/common/ErrorText.kt.
sealed interface WeatherError {
    data object Unreachable : WeatherError

    data class UpstreamStatus(val status: Int) : WeatherError

    data object CityNotFound : WeatherError

    data object NoWeatherHere : WeatherError

    data object NoForecastForRange : WeatherError

    data object BadPayload : WeatherError

    data class NoCitiesNear(val place: String) : WeatherError

    data object LocationTimeout : WeatherError

    data object LocationUnavailable : WeatherError

    data object LocationDenied : WeatherError

    data object LocationOff : WeatherError

    data object LocationNoProvider : WeatherError

    data object LocationNoName : WeatherError
}

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Failed(val error: WeatherError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Failed -> this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Ok)?.value
