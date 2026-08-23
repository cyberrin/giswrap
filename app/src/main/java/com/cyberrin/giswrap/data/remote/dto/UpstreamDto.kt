package com.cyberrin.giswrap.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull

@OptIn(ExperimentalSerializationApi::class)
open class UnwrappedSerializer<T : Any>(private val inner: KSerializer<T>) : KSerializer<T?> {
    override val descriptor: SerialDescriptor = inner.descriptor.nullable

    override fun deserialize(decoder: Decoder): T? {
        val json = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(inner)
        val element = json.decodeJsonElement()
        val scalar = if (element is JsonArray) element.firstOrNull() else element
        if (scalar == null || scalar is JsonNull) return null
        return json.json.decodeFromJsonElement(inner, scalar)
    }

    override fun serialize(encoder: Encoder, value: T?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(inner, value)
    }
}

object UnwrappedDouble : UnwrappedSerializer<Double>(Double.serializer())
object UnwrappedInt : UnwrappedSerializer<Int>(Int.serializer())
object UnwrappedString : UnwrappedSerializer<String>(String.serializer())

@Serializable
data class Named(val name: String? = null)

@Serializable
data class Translation(
    val city: Named? = null,
    val country: Named? = null,
    val district: Named? = null,
)

@Serializable
data class Coordinates(
    val latitude: Double? = null,
    val longitude: Double? = null,
)

@Serializable
data class UpstreamCity(
    val id: Int? = null,
    val slug: String = "unknown",
    val coordinates: Coordinates? = null,
    val timeZone: Int? = null,
    val timeZoneName: String? = null,
    // Every readable name lives here. The top-level name/country.name/district.name that
    // client.py reads do not exist upstream, which is why the TUI shows bare slugs.
    val translations: Map<String, Translation> = emptyMap(),
) {
    fun cityName(lang: String = "ru"): String? = localised(lang) { it.city }

    fun countryName(lang: String = "ru"): String? = localised(lang) { it.country }

    fun districtName(lang: String = "ru"): String? = localised(lang) { it.district }

    private fun localised(lang: String, pick: (Translation) -> Named?): String? {
        val candidates = listOfNotNull(translations[lang]) + translations.values
        return candidates.firstNotNullOfOrNull { translation ->
            pick(translation)?.name?.takeIf { it.isNotBlank() }
        }
    }
}

@Serializable
data class UpstreamSearchResponse(val data: List<UpstreamCity> = emptyList())

@Serializable
data class UpstreamCityResponse(val data: UpstreamCity? = null)

@Serializable
data class UpstreamCurrentWeather(
    @Serializable(with = UnwrappedString::class) val description: String? = null,
    @Serializable(with = UnwrappedString::class) val iconWeather: String? = null,
    @Serializable(with = UnwrappedDouble::class) val temperatureAir: Double? = null,
    @Serializable(with = UnwrappedDouble::class) val temperatureFeelsLike: Double? = null,
    @Serializable(with = UnwrappedInt::class) val humidity: Int? = null,
    @Serializable(with = UnwrappedInt::class) val pressure: Int? = null,
    @Serializable(with = UnwrappedDouble::class) val windSpeed: Double? = null,
    @Serializable(with = UnwrappedDouble::class) val windGust: Double? = null,
    @Serializable(with = UnwrappedInt::class) val cloudiness: Int? = null,
)

@Serializable
data class UpstreamWeatherItem(
    val city: UpstreamCity? = null,
    val weather: UpstreamCurrentWeather = UpstreamCurrentWeather(),
)

@Serializable
data class UpstreamWeatherResponse(val data: List<UpstreamWeatherItem> = emptyList())
