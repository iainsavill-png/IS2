package uk.railboard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import uk.railboard.app.data.ServiceItem
import uk.railboard.app.data.ServiceStatus
import uk.railboard.app.data.Station
import uk.railboard.app.data.Stations
import uk.railboard.app.ui.theme.StatusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val AUTO_REFRESH_MS = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeparturesScreen(vm: DeparturesViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(state.apiKey.isBlank()) }

    // Refresh immediately and then every minute, but only while the app is in the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                vm.refresh()
                delay(AUTO_REFRESH_MS)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.board?.locationName?.takeIf { it.isNotBlank() }
                                ?: state.crs?.let { Stations.nameFor(it) ?: it }
                                ?: "RailBoard",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub = state.board?.filterLocationName ?: state.callingAt?.let { Stations.nameFor(it) ?: it }
                        if (sub != null) Text("Calling at $sub", style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh, enabled = state.crs != null) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchPanel(
                initialFrom = state.crs,
                initialTo = state.callingAt,
                recent = state.recent,
                onSearch = vm::show,
            )
            PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                BoardContent(state)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            apiKey = state.apiKey,
            baseUrl = state.baseUrl,
            onDismiss = { showSettings = false },
            onSave = { key, url ->
                vm.saveSettings(key, url)
                showSettings = false
            },
        )
    }
}

@Composable
private fun SearchPanel(
    initialFrom: String?,
    initialTo: String?,
    recent: List<String>,
    onSearch: (String, String?) -> Unit,
) {
    var from by rememberSaveable(initialFrom) { mutableStateOf(initialFrom?.let(::labelFor).orEmpty()) }
    var to by rememberSaveable(initialTo) { mutableStateOf(initialTo?.let(::labelFor).orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    fun submit() {
        val fromCrs = resolveCrs(from)
        val toCrs = if (to.isBlank()) null else resolveCrs(to)
        error = when {
            fromCrs == null -> "Unknown station \"$from\". Try its 3-letter code."
            to.isNotBlank() && toCrs == null -> "Unknown station \"$to\". Try its 3-letter code."
            else -> null
        }
        if (error == null && fromCrs != null) {
            focus.clearFocus()
            onSearch(fromCrs, toCrs)
        }
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        StationField(
            label = "Departures from",
            value = from,
            onValueChange = { from = it; error = null },
            onPick = { from = labelFor(it.crs); error = null },
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StationField(
                label = "Calling at (optional)",
                value = to,
                onValueChange = { to = it; error = null },
                onPick = { to = labelFor(it.crs); error = null },
                onDone = ::submit,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = ::submit) { Text("Go") }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (recent.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(recent) { crs ->
                    AssistChip(
                        onClick = {
                            from = labelFor(crs)
                            to = ""
                            error = null
                            focus.clearFocus()
                            onSearch(crs, null)
                        },
                        label = { Text(Stations.nameFor(crs) ?: crs) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPick: (Station) -> Unit,
    modifier: Modifier = Modifier,
    onDone: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val suggestions = remember(value) {
        // A picked station is shown as "Name (CRS)"; don't offer suggestions for it again.
        if (PICKED_LABEL.containsMatchIn(value)) emptyList() else Stations.search(value)
    }
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = if (value.isNotEmpty()) {
                { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Default.Clear, "Clear") } }
            } else null,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = if (onDone != null) ImeAction.Go else ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(onGo = { onDone?.invoke() }),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )
        if (focused && suggestions.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                suggestions.forEach { st ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onPick(st)
                                if (onDone == null) focus.clearFocus()
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(st.name, Modifier.weight(1f))
                        Text(st.crs, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardContent(state: UiState) {
    val board = state.board
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.error?.let { msg ->
            item {
                Banner(msg, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        when {
            state.crs == null -> item { CenteredHint("Search for a station to see live departures.") }
            board == null -> if (!state.loading && state.error == null) item { CenteredHint("Pull down to load departures.") }
            else -> {
                items(board.messages) { msg ->
                    Banner(msg, MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                }
                state.lastUpdatedMillis?.let {
                    item {
                        Text(
                            "Updated ${formatClock(it)} · refreshes every minute",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
                val services = board.allServices
                if (services.isEmpty()) {
                    item { CenteredHint("No departures in the next couple of hours.") }
                } else {
                    items(services) { svc ->
                        ServiceRow(svc, showPlatforms = board.platformAvailable)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceRow(svc: ServiceItem, showPlatforms: Boolean) {
    val status = svc.status
    val statusColor = when (status) {
        ServiceStatus.ON_TIME -> StatusColors.onTime
        ServiceStatus.LATE, ServiceStatus.DELAYED -> StatusColors.late
        ServiceStatus.CANCELLED -> StatusColors.cancelled
        ServiceStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = when (status) {
        ServiceStatus.ON_TIME -> "On time"
        ServiceStatus.LATE -> "Exp ${svc.etd}"
        ServiceStatus.DELAYED -> "Delayed"
        ServiceStatus.CANCELLED -> "Cancelled"
        ServiceStatus.UNKNOWN -> svc.etd ?: "—"
    }

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 72.dp)) {
            Text(
                svc.std ?: "--:--",
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(statusText, color = statusColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(svc.destinationText, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            svc.viaText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            val details = listOfNotNull(
                svc.operator,
                svc.length?.takeIf { it > 0 }?.let { "$it coaches" },
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            (svc.cancelReason ?: svc.delayReason)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = statusColor)
            }
        }
        Spacer(Modifier.width(12.dp))
        PlatformBadge(
            when {
                svc.isBus -> "BUS"
                !showPlatforms -> null
                else -> svc.platform ?: "–"
            }
        )
    }
}

@Composable
private fun PlatformBadge(text: String?) {
    if (text == null) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            Modifier.widthIn(min = 52.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Plat", style = MaterialTheme.typography.labelSmall)
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Banner(text: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { expanded = !expanded },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun CenteredHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsDialog(
    apiKey: String,
    baseUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var key by rememberSaveable { mutableStateOf(apiKey) }
    var url by rememberSaveable { mutableStateOf(baseUrl) }
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Live data comes from National Rail's Live Departure Board API. Register free at " +
                        "raildata.org.uk, subscribe to \"Live Departure Board\" and paste the Consumer key here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uri.openUri("https://raildata.org.uk/") }) { Text("Open raildata.org.uk") }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key (Consumer key)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("API base URL") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { url = uk.railboard.app.data.LdbwsClient.DEFAULT_BASE_URL }) {
                    Text("Reset URL to default")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key, url) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun labelFor(crs: String): String = Stations.nameFor(crs)?.let { "$it ($crs)" } ?: crs.uppercase()

private val PICKED_LABEL = Regex("""\(([A-Za-z]{3})\)$""")

/** Accepts "Name (CRS)", a bare CRS code, or a station name/prefix from the built-in list. */
private fun resolveCrs(input: String): String? {
    val s = input.trim()
    PICKED_LABEL.find(s)?.let { return it.groupValues[1].uppercase() }
    if (Stations.isCrs(s)) return s.uppercase()
    return Stations.search(s, limit = 1).firstOrNull()?.crs
}

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")
private fun formatClock(millis: Long): String =
    clockFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
