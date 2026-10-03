package uk.railboard.app.data

import android.content.Context
import uk.railboard.app.BuildConfig

/** Small SharedPreferences wrapper for the API key, API URL and recent stations. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("railboard", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_API_KEY
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_URL, null)?.takeIf { it.isNotBlank() } ?: LdbwsClient.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_URL, value.trim()).apply()

    var recentStations: List<String>
        get() = prefs.getString(KEY_RECENT, "").orEmpty().split(',').filter { it.length == 3 }
        set(value) = prefs.edit().putString(KEY_RECENT, value.joinToString(",")).apply()

    fun addRecent(crs: String) {
        recentStations = (listOf(crs.uppercase()) + recentStations.filterNot { it == crs.uppercase() }).take(MAX_RECENT)
    }

    private companion object {
        const val KEY_API = "api_key"
        const val KEY_URL = "base_url"
        const val KEY_RECENT = "recent"
        const val MAX_RECENT = 6
    }
}
