package uk.railboard.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client for the National Rail "Live Departure Board" (LDBWS) REST API,
 * published on the Rail Data Marketplace (https://raildata.org.uk).
 *
 * Authentication is a per-user consumer key sent in the `x-apikey` header.
 */
class LdbwsClient(
    baseUrl: String = DEFAULT_BASE_URL,
    private val apiKey: String,
) {
    private val baseUrl = baseUrl.trim().trimEnd('/')

    suspend fun departureBoard(
        crs: String,
        callingAtCrs: String? = null,
        numRows: Int = 20,
    ): StationBoard = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw LdbwsException("No API key set. Add your Rail Data Marketplace key in Settings.")
        val conn = URL(departureBoardUrl(baseUrl, crs, callingAtCrs, numRows)).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("x-apikey", apiKey.trim())
            conn.setRequestProperty("Accept", "application/json")

            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(200)
                throw LdbwsException(
                    when (code) {
                        401, 403 -> "API key rejected ($code). Check the key in Settings and that you're subscribed to the Live Departure Board product."
                        404 -> "Not found (404). Check the station code and the API URL in Settings."
                        429 -> "Rate limited (429). Wait a moment and try again."
                        else -> "Server error $code. $detail".trim()
                    }
                )
            }
            parseStationBoard(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: IOException) {
            throw LdbwsException("Network error: ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DEFAULT_BASE_URL =
            "https://api1.raildata.org.uk/1010-live-departure-board-dep1_2/LDBWS/api/20220120"

        fun departureBoardUrl(baseUrl: String, crs: String, callingAtCrs: String?, numRows: Int): String =
            buildString {
                append(baseUrl.trim().trimEnd('/'))
                append("/GetDepartureBoard/")
                append(enc(crs.trim().uppercase()))
                append("?numRows=").append(numRows)
                if (!callingAtCrs.isNullOrBlank()) {
                    append("&filterCrs=").append(enc(callingAtCrs.trim().uppercase()))
                    append("&filterType=to")
                }
            }

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    }
}

class LdbwsException(message: String, cause: Throwable? = null) : Exception(message, cause)

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true // treat JSON nulls as the property default
    isLenient = true
}

fun parseStationBoard(body: String): StationBoard = json.decodeFromString(StationBoard.serializer(), body)

@Serializable
data class StationBoard(
    val locationName: String = "",
    val crs: String = "",
    val generatedAt: String? = null,
    val filterLocationName: String? = null,
    val platformAvailable: Boolean = false,
    val trainServices: List<ServiceItem> = emptyList(),
    val busServices: List<ServiceItem> = emptyList(),
    val nrccMessages: List<JsonElement> = emptyList(),
) {
    /** Trains and replacement buses merged in timetable order, coping with boards that span midnight. */
    val allServices: List<ServiceItem>
        get() {
            if (busServices.isEmpty()) return trainServices
            val all = trainServices + busServices.map { it.copy(serviceType = "bus") }
            val mins = all.map { minutesOf(it.std) }
            val spansMidnight = mins.any { it != null && it >= 20 * 60 } && mins.any { it != null && it < 4 * 60 }
            return all.sortedBy { s ->
                val m = minutesOf(s.std) ?: Int.MAX_VALUE
                if (spansMidnight && m < 12 * 60) m + 24 * 60 else m
            }
        }

    /** Disruption messages as plain text (the API returns HTML fragments). */
    val messages: List<String>
        get() = nrccMessages.mapNotNull { firstString(it)?.let(::stripHtml) }.filter { it.isNotBlank() }
}

@Serializable
data class ServiceItem(
    val std: String? = null,
    val etd: String? = null,
    val platform: String? = null,
    val operator: String? = null,
    val serviceType: String? = null,
    val origin: List<ServiceLocation> = emptyList(),
    val destination: List<ServiceLocation> = emptyList(),
    val isCancelled: Boolean = false,
    val cancelReason: String? = null,
    val delayReason: String? = null,
    val serviceID: String? = null,
    val length: Int? = null,
) {
    val destinationText: String
        get() = destination.joinToString(" & ") { it.locationName }.ifBlank { "Unknown" }

    val viaText: String?
        get() = destination.firstNotNullOfOrNull { it.via?.takeIf(String::isNotBlank) }

    val status: ServiceStatus
        get() {
            val e = etd?.trim().orEmpty()
            return when {
                isCancelled || e.equals("Cancelled", ignoreCase = true) -> ServiceStatus.CANCELLED
                e.equals("On time", ignoreCase = true) -> ServiceStatus.ON_TIME
                e.equals("Delayed", ignoreCase = true) -> ServiceStatus.DELAYED
                TIME_REGEX.matches(e) -> if (e == std) ServiceStatus.ON_TIME else ServiceStatus.LATE
                else -> ServiceStatus.UNKNOWN
            }
        }

    val isBus: Boolean get() = serviceType.equals("bus", ignoreCase = true)

    private companion object {
        val TIME_REGEX = Regex("""\d{2}:\d{2}""")
    }
}

@Serializable
data class ServiceLocation(
    val locationName: String = "",
    val crs: String? = null,
    val via: String? = null,
)

enum class ServiceStatus { ON_TIME, LATE, DELAYED, CANCELLED, UNKNOWN }

private fun minutesOf(hhmm: String?): Int? {
    val parts = hhmm?.split(':') ?: return null
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun firstString(e: JsonElement): String? = when (e) {
    is JsonPrimitive -> if (e.isString) e.content else null
    is JsonObject -> e.values.firstNotNullOfOrNull(::firstString)
    is JsonArray -> e.firstNotNullOfOrNull(::firstString)
}

internal fun stripHtml(s: String): String =
    s.replace(Regex("<[^>]*>"), " ")
        .replace("&amp;", "&").replace("&nbsp;", " ").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
