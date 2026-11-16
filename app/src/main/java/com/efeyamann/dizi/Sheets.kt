package com.efeyamann.dizi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShowSheet(
    show: Show, today: LocalDate,
    onStatus: (Status) -> Unit, onToggleSeason: (Int) -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Poster(show, Modifier.size(100.dp, 150.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(show.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(
                            if (show.seasons > 0) "${show.seasons} sezon" else null,
                            if (show.ended) "Final yaptı" else "Devam ediyor",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (show.nextAir != null && show.nextAir >= today.toString()) {
                        Text(
                            "Sıradaki: ${show.nextSeason}. sezon ${show.nextEpisodeLabel()}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${longDate(show.nextAir, today)} · ${relativeDay(show.nextAir, today)}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TABS.forEach { (st, label) ->
                    FilterChip(selected = show.status == st, onClick = { onStatus(st) }, label = { Text(label) })
                }
            }
            if (show.seasons > 0) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val resume = show.resumeAt
                Text(
                    when {
                        resume == null -> "İzlediğin sezonlar · hepsi izlendi"
                        show.watched.isEmpty() -> "İzlediğin sezonlar"
                        else -> "İzlediğin sezonlar · kaldığın yer: $resume. sezon"
                    },
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..show.seasons).forEach { n ->
                        FilterChip(
                            selected = n in show.watched, onClick = { onToggleSeason(n) },
                            label = { Text("$n. sezon") },
                        )
                    }
                }
            }
            TextButton(onClick = onDelete) { Text("Listeden sil", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Runs a network job, showing progress and errors in [setStatus]. */
private fun CoroutineScope.job(setStatus: (String?) -> Unit, busy: String, block: suspend () -> Unit) = launch {
    setStatus(busy)
    try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        setStatus("Hata: ${e.message}")
    }
}

@Composable
fun SearchDialog(store: Store, tabName: String, onAdd: (Show) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Show>()) }
    var status by remember { mutableStateOf(if (store.token.isBlank()) "Önce Ayarlar'a TMDB anahtarını gir." else null) }
    // Search as you type, once typing pauses.
    LaunchedEffect(q) {
        if (q.trim().length < 2 || store.token.isBlank()) return@LaunchedEffect
        delay(400)
        try {
            results = withContext(Dispatchers.IO) { Tmdb.search(store.token, q.trim()) }
            status = if (results.isEmpty()) "Sonuç yok." else null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            status = "Hata: ${e.message}"
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } },
        title = { Text("$tabName listesine ekle") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Dizi adı") })
                status?.let { Text(it, Modifier.padding(top = 8.dp)) }
                LazyColumn(Modifier.heightIn(max = 400.dp).padding(top = 8.dp)) {
                    items(results, key = { it.id }) { r ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                scope.job({ status = it }, "Ekleniyor…") {
                                    onAdd(withContext(Dispatchers.IO) { Tmdb.details(store.token, r.id) })
                                    onClose()
                                }
                            }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Poster(r, Modifier.size(40.dp, 60.dp), "w92", label = false)
                            Text(r.name)
                        }
                    }
                }
            }
        },
    )
}

@Composable
fun SettingsDialog(store: Store, onImported: (List<Show>) -> Unit, onTest: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var token by remember { mutableStateOf(store.token) }
    var user by remember { mutableStateOf(store.user) }
    var status by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } },
        title = { Text("Ayarlar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user, { user = it; store.user = it }, singleLine = true, label = { Text("Serializd kullanıcı adı") })
                Button(
                    enabled = token.isNotBlank() && user.isNotBlank(),
                    onClick = {
                        scope.job({ status = it }, "İçe aktarılıyor… (sunucu uyanırken 1 dk sürebilir)") {
                            val imported = withContext(Dispatchers.IO) {
                                // Prefer fresh TMDB data; keep the Serializd entry if TMDB fails for one show.
                                Serializd.fetch(store.user).map { (s, seasonIds) ->
                                    runCatching {
                                        val o = Tmdb.detailsJson(store.token, s.id)
                                        val numbers = Tmdb.seasonNumbers(o)
                                        val d = Tmdb.parseDetails(o)
                                        val watched = seasonIds.mapNotNull { numbers[it] }.toSet()
                                        d.copy(status = s.status, watched = if (s.status == Status.DONE) watched + (1..d.seasons) else watched)
                                    }.getOrDefault(s)
                                }
                            }
                            onImported(imported)
                            status = "${imported.size} dizi içe aktarıldı ve sekmelere yerleştirildi."
                        }
                    },
                ) { Text("Serializd'den içe aktar") }
                OutlinedButton(onClick = { onTest(); status = "Kontrol başladı, birazdan bildirim gelmeli." }) { Text("Bildirimi test et") }
                OutlinedTextField(
                    token, { token = it; store.token = it }, singleLine = true,
                    label = { Text("TMDB Read Access Token") },
                )
                status?.let { Text(it) }
            }
        },
    )
}
