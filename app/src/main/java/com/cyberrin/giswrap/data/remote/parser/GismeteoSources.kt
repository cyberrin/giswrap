package com.cyberrin.giswrap.data.remote.parser

import com.cyberrin.giswrap.domain.model.DailyForecast
import com.cyberrin.giswrap.domain.model.ForecastOrigin
import com.cyberrin.giswrap.domain.model.HourlyForecast
import com.cyberrin.giswrap.domain.model.Period
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.w3c.dom.Element as XmlElement
import org.xml.sax.InputSource

object Sources {
    const val LEGACY_FORECAST_URL =
        "https://services.gismeteo.ru/inform-service/inf_chrome/forecast/"
    const val SITE_BASE = "https://www.gismeteo.ru"

    val ORIGIN_FOR_PERIOD: Map<Period, ForecastOrigin> = mapOf(
        Period.DAYS_3 to ForecastOrigin.LEGACY_XML,
        Period.WEEKS_2 to ForecastOrigin.HTML_WIDGET,
        Period.MONTH to ForecastOrigin.HTML_MONTH,
    )

    val DAY_LIMIT: Map<Period, Int?> = mapOf(
        Period.DAYS_3 to 3,
        Period.WEEKS_2 to 14,
        Period.MONTH to null,
    )

    fun legacyForecastUrl(cityId: String): String = "$LEGACY_FORECAST_URL?city=$cityId&lang=ru"

    fun pageUrl(cityPath: String, period: Period): String =
        "$SITE_BASE/weather-$cityPath/${period.slug}/"

    fun cityUrl(cityPath: String): String = "$SITE_BASE/weather-$cityPath/"

    fun cityToday(tzName: String?, tzOffsetMinutes: Int?): LocalDate {
        if (tzName != null) {
            runCatching { return ZonedDateTime.now(ZoneId.of(tzName)).toLocalDate() }
        }
        if (tzOffsetMinutes != null) {
            runCatching {
                return ZonedDateTime.now(ZoneOffset.ofTotalSeconds(tzOffsetMinutes * 60))
                    .toLocalDate()
            }
        }
        return ZonedDateTime.now(ZoneOffset.UTC).toLocalDate()
    }

    private fun num(raw: String?): Double? =
        if (raw.isNullOrEmpty()) null else raw.toDoubleOrNull()

    private fun int(raw: String?): Int? = num(raw)?.toInt()

    private fun XmlElement.attrOrNull(name: String): String? =
        if (hasAttribute(name)) getAttribute(name).takeIf { it.isNotEmpty() } else null

    private fun Element.attrOrNull(name: String): String? =
        attr(name).takeIf { it.isNotEmpty() }

    private val xmlFactory: DocumentBuilderFactory by lazy {
        DocumentBuilderFactory.newInstance().apply {
            // Per-feature: disallow-doctype-decl is a Xerces name and Android's Expat parser throws on it.
            harden(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            harden("http://apache.org/xml/features/disallow-doctype-decl", true)
            harden("http://xml.org/sax/features/external-general-entities", false)
            harden("http://xml.org/sax/features/external-parameter-entities", false)
            runCatching { isXIncludeAware = false }
            runCatching { isExpandEntityReferences = false }
        }
    }

    internal fun DocumentBuilderFactory.harden(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun newXmlBuilder() = synchronized(xmlFactory) { xmlFactory.newDocumentBuilder() }

    fun parseLegacyForecast(xmlText: String): Pair<String?, List<DailyForecast>> {
        val document = newXmlBuilder().parse(InputSource(StringReader(xmlText)))
        val root = document.documentElement

        val cityName = root.childElements("location").firstOrNull()?.attrOrNull("name")

        val days = mutableListOf<DailyForecast>()

        val dayNodes = document.getElementsByTagName("day")
        for (index in 0 until dayNodes.length) {
            val day = dayNodes.item(index) as? XmlElement ?: continue
            val rawDate = day.attrOrNull("date") ?: continue

            val hours = day.childElements("forecast").mapNotNull { entry ->
                val valid = entry.attrOrNull("valid") ?: return@mapNotNull null
                val values = entry.childElements("values").firstOrNull() ?: return@mapNotNull null
                HourlyForecast(
                    valid = LocalDateTime.parse(valid),
                    temperature = num(values.attrOrNull("t")),
                    feelsLike = num(values.attrOrNull("hi")),
                    pressure = int(values.attrOrNull("p")),
                    humidity = int(values.attrOrNull("hum")),
                    windSpeed = num(values.attrOrNull("ws")),
                    windGust = num(values.attrOrNull("gust_speed")),
                    windDirection = int(values.attrOrNull("wd")),
                    cloudiness = int(values.attrOrNull("cl")),
                    description = values.attrOrNull("descr"),
                    icon = values.attrOrNull("icon"),
                )
            }

            days += DailyForecast(
                date = LocalDate.parse(rawDate),
                tempMin = num(day.attrOrNull("tmin")),
                tempMax = num(day.attrOrNull("tmax")),
                pressureMin = int(day.attrOrNull("pmin")),
                pressureMax = int(day.attrOrNull("pmax")),
                humidity = int(day.attrOrNull("hum")),
                windSpeedMax = num(day.attrOrNull("wsmax")),
                windGust = num(day.attrOrNull("gust_speed")),
                precipitationMm = num(day.attrOrNull("prflt")),
                description = day.attrOrNull("descr"),
                icon = day.attrOrNull("icon"),
                hours = hours,
            )
        }

        return cityName to days
    }

    private fun XmlElement.childElements(name: String): List<XmlElement> {
        val result = mutableListOf<XmlElement>()
        val nodes = childNodes
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is XmlElement && node.tagName == name) result += node
        }
        return result
    }

    private val DAY_OFFSET = Regex("""/(\d+)-day/?$""")
    private val WEATHER_ROOT = Regex("""/weather-[^/]+/?$""")

    internal fun cellDate(href: String?, today: LocalDate): LocalDate? {
        if (href.isNullOrEmpty()) return null
        if (WEATHER_ROOT.containsMatchIn(href)) return today
        if (href.trimEnd('/').endsWith("/tomorrow")) return today.plusDays(1)
        val match = DAY_OFFSET.find(href) ?: return null
        return today.plusDays(match.groupValues[1].toLong() - 1)
    }

    internal fun anchorDates(cells: List<Element>, today: LocalDate): List<LocalDate> {
        cells.forEachIndexed { index, cell ->
            val anchored = cellDate(cell.attrOrNull("href"), today)
            if (anchored != null) {
                return cells.indices.map { anchored.plusDays((it - index).toLong()) }
            }
        }

        return cells.indices.map { today.plusDays(it.toLong()) }
    }

    private fun values(row: Element?, selector: String): List<Double?> =
        row?.select(selector)?.map { num(it.attrOrNull("value")) } ?: emptyList()

    private fun List<Double?>.at(index: Int): Double? = getOrNull(index)

    private fun iconName(cell: Element): String? {
        val use = cell.selectFirst("use") ?: return null
        val href = use.attrOrNull("href") ?: use.attrOrNull("xlink:href") ?: return null
        return href.trimStart('#')
    }

    fun parseWidgetForecast(html: String, today: LocalDate): List<DailyForecast> {
        val soup = Jsoup.parse(html)

        val dateCells = soup.select(".widget-row-date .row-item")
        if (dateCells.isEmpty()) return emptyList()
        val dates = anchorDates(dateCells, today)

        val tempRow = soup.selectFirst(".widget-row-chart-temperature-air")
        val pressureRow = soup.selectFirst(".widget-row-chart-pressure")
        val tempMax = values(tempRow, ".maxt temperature-value")
        val tempMin = values(tempRow, ".mint temperature-value")
        val pressureMax = values(pressureRow, ".maxt pressure-value")
        val pressureMin = values(pressureRow, ".mint pressure-value")

        val windCells = soup.select(".widget-row-wind .row-item")
        val humidityCells = soup.select(".widget-row-humidity .row-item")
        val precipCells = soup.select(".widget-row-precipitation-bars .row-item")
        val iconCells = soup.select(".widget-row-icon .row-item")

        return dates.mapIndexed { i, dayDate ->
            val wind = windCells.getOrNull(i)?.selectFirst("speed-value")
            val precip = precipCells.getOrNull(i)?.selectFirst("precipitation-value")
            val iconCell = iconCells.getOrNull(i)

            DailyForecast(
                date = dayDate,
                tempMin = tempMin.at(i),
                tempMax = tempMax.at(i),
                pressureMin = pressureMin.at(i)?.toInt(),
                pressureMax = pressureMax.at(i)?.toInt(),
                humidity = int(humidityCells.getOrNull(i)?.text()?.trim()),
                windSpeedMax = num(wind?.attrOrNull("value")),
                precipitationMm = num(precip?.attrOrNull("value")),
                description = iconCell?.attrOrNull("data-tooltip"),
                icon = iconCell?.let { iconName(it) },
            )
        }
    }

    fun parseMonthForecast(html: String, today: LocalDate): List<DailyForecast> {
        val soup = Jsoup.parse(html)
        val cells = soup.select(".widget-month .row-item-month-date")
        if (cells.isEmpty()) return emptyList()

        val dates = anchorDates(cells, today)

        check(dates.size == cells.size) {
            "dated ${dates.size} cells but the grid has ${cells.size}"
        }

        return dates.zip(cells)
            .filterNot { (dayDate, _) -> dayDate < today }
            .map { (dayDate, cell) ->
                DailyForecast(
                    date = dayDate,
                    tempMin = num(cell.selectFirst(".mint temperature-value")?.attrOrNull("value")),
                    tempMax = num(cell.selectFirst(".maxt temperature-value")?.attrOrNull("value")),
                    description = cell.attrOrNull("data-tooltip"),
                    icon = iconName(cell),
                )
            }
    }
}
