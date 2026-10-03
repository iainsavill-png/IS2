package uk.railboard.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uk.railboard.app.data.LdbwsClient
import uk.railboard.app.data.Settings
import uk.railboard.app.data.StationBoard

data class UiState(
    val crs: String? = null,
    val callingAt: String? = null,
    val board: StationBoard? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val lastUpdatedMillis: Long? = null,
    val recent: List<String> = emptyList(),
    val apiKey: String = "",
    val baseUrl: String = LdbwsClient.DEFAULT_BASE_URL,
)

class DeparturesViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)

    private val _state = MutableStateFlow(
        UiState(
            crs = settings.recentStations.firstOrNull(),
            recent = settings.recentStations,
            apiKey = settings.apiKey,
            baseUrl = settings.baseUrl,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Show departures from [crs], optionally only trains calling at [callingAt]. */
    fun show(crs: String, callingAt: String?) {
        val from = crs.trim().uppercase()
        val to = callingAt?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        settings.addRecent(from)
        _state.update {
            it.copy(
                crs = from,
                callingAt = to,
                board = if (it.crs == from && it.callingAt == to) it.board else null,
                recent = settings.recentStations,
                error = null,
            )
        }
        refresh()
    }

    fun refresh() {
        val s = _state.value
        val crs = s.crs ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            try {
                val board = LdbwsClient(s.baseUrl, s.apiKey).departureBoard(crs, s.callingAt)
                _state.update {
                    it.copy(board = board, loading = false, error = null, lastUpdatedMillis = System.currentTimeMillis())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Something went wrong") }
            }
        }
    }

    fun saveSettings(apiKey: String, baseUrl: String) {
        settings.apiKey = apiKey
        settings.baseUrl = baseUrl
        _state.update { it.copy(apiKey = settings.apiKey, baseUrl = settings.baseUrl) }
        refresh()
    }
}
