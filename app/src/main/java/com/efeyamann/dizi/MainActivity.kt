package com.efeyamann.dizi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "check", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CheckWorker>(1, TimeUnit.DAYS).setConstraints(net).build(),
        )
        val store = Store(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App(store) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(store: Store) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var shows by remember { mutableStateOf(store.shows) }
    var dialog by remember { mutableStateOf("") } // "", "search", "settings"
    fun save(list: List<Show>) { shows = list; store.shows = list }
    fun checkNow() {
        val wm = WorkManager.getInstance(ctx)
        val req = OneTimeWorkRequestBuilder<CheckWorker>().build()
        wm.enqueue(req)
        scope.launch { wm.getWorkInfoByIdFlow(req.id).first { it?.state?.isFinished == true }; shows = store.shows }
    }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // The daily worker may have changed the list while the app was in the background.
    LifecycleResumeEffect(Unit) { shows = store.shows; onPauseOrDispose {} }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Diziler") }, actions = {
                IconButton(onClick = { checkNow() }) { Icon(Icons.Default.Refresh, "Şimdi kontrol et") }
                IconButton(onClick = { dialog = "settings" }) { Icon(Icons.Default.Settings, "Ayarlar") }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = "search" }) { Icon(Icons.Default.Add, "Dizi ekle") }
        },
    ) { pad ->
        if (shows.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Liste boş. + ile dizi ekle ya da Ayarlar'dan Serializd'i içe aktar.", Modifier.padding(32.dp))
            }
        }
        LazyColumn(contentPadding = pad) {
            items(shows, key = { it.id }) { show ->
                val swipe = rememberSwipeToDismissBoxState(confirmValueChange = {
                    if (it != SwipeToDismissBoxValue.Settled) save(shows.filter { s -> s.id != show.id })
                    true
                })
                SwipeToDismissBox(swipe, backgroundContent = {}) { ShowRow(show) }
            }
        }
    }

    when (dialog) {
        "search" -> SearchDialog(store, onAdd = { s -> save(listOf(s) + shows.filter { it.id != s.id }) }, onClose = { dialog = "" })
        "settings" -> SettingsDialog(
            store,
            onImported = { new -> save(shows + new.filter { n -> shows.none { it.id == n.id } }) },
            onTest = {
                // Pretend we knew one season less, so the next check announces a "new" season.
                val i = shows.indexOfFirst { it.seasons > 1 }
                if (i >= 0) {
                    save(shows.toMutableList().also { it[i] = it[i].copy(seasons = it[i].seasons - 1) })
                    checkNow()
                }
            },
            onClose = { dialog = "" },
        )
    }
}

@Composable
fun ShowRow(show: Show) = Surface {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            show.poster?.let { "https://image.tmdb.org/t/p/w154$it" }, null,
            Modifier.size(48.dp, 72.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(show.name, style = MaterialTheme.typography.titleMedium)
            val info = listOfNotNull(
                if (show.seasons > 0) "${show.seasons} sezon" else null,
                show.nextAir?.let { "Sıradaki: S${show.nextSeason}E${show.nextEpisode} · $it" },
            )
            Text(info.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Runs a network job, showing progress and errors in [setStatus]. */
private fun kotlinx.coroutines.CoroutineScope.job(setStatus: (String?) -> Unit, busy: String, block: suspend () -> Unit) = launch {
    setStatus(busy)
    try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        setStatus("Hata: ${e.message}")
    }
}

@Composable
fun SearchDialog(store: Store, onAdd: (Show) -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Show>()) }
    var status by remember { mutableStateOf(if (store.token.isBlank()) "Önce Ayarlar'a TMDB anahtarını gir." else null) }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text("Kapat") } },
        title = { Text("Dizi ekle") },
        text = {
            Column {
                OutlinedTextField(
                    q, { q = it }, singleLine = true, label = { Text("Dizi adı") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        scope.job({ status = it }, "Aranıyor…") {
                            results = withContext(Dispatchers.IO) { Tmdb.search(store.token, q) }
                            status = if (results.isEmpty()) "Sonuç yok." else null
                        }
                    }),
                )
                status?.let { Text(it, Modifier.padding(top = 8.dp)) }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(results, key = { it.id }) { r ->
                        Text(r.name, Modifier.fillMaxWidth().clickable {
                            scope.job({ status = it }, "Ekleniyor…") {
                                onAdd(withContext(Dispatchers.IO) { Tmdb.details(store.token, r.id) })
                                onClose()
                            }
                        }.padding(vertical = 12.dp))
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
                OutlinedTextField(
                    token, { token = it; store.token = it }, singleLine = true,
                    label = { Text("TMDB Read Access Token") },
                    supportingText = { Text("themoviedb.org → Ayarlar → API") },
                )
                OutlinedTextField(user, { user = it; store.user = it }, singleLine = true, label = { Text("Serializd kullanıcı adı") })
                Button(
                    enabled = token.isNotBlank() && user.isNotBlank(),
                    onClick = {
                        scope.job({ status = it }, "İçe aktarılıyor… (sunucu uyanırken 1 dk sürebilir)") {
                            val imported = withContext(Dispatchers.IO) {
                                // Prefer fresh TMDB data; keep the Serializd entry if TMDB fails for one show.
                                Serializd.watched(store.user).map { s -> runCatching { Tmdb.details(store.token, s.id) }.getOrDefault(s) }
                            }
                            onImported(imported)
                            status = "${imported.size} dizi bulundu, yeni olanlar eklendi."
                        }
                    },
                ) { Text("Serializd'den içe aktar") }
                OutlinedButton(onClick = { onTest(); status = "Kontrol başladı, birazdan bildirim gelmeli." }) { Text("Bildirimi test et") }
                status?.let { Text(it) }
            }
        },
    )
}
