package com.efeyamann.dizi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

val TABS = listOf(Status.WATCHING to "Yarıda", Status.WATCHLIST to "Watchlist", Status.DONE to "Bitti")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(store: Store, openId: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var shows by remember { mutableStateOf(store.shows) }
    var selected by rememberSaveable { mutableIntStateOf(openId) } // show whose sheet is open, 0 = none
    var tab by rememberSaveable {
        val opened = shows.find { it.id == openId }?.status
        mutableIntStateOf(TABS.indexOfFirst { it.first == opened }.coerceAtLeast(0))
    }
    var dialog by remember { mutableStateOf("") } // "", "search", "settings"
    var refreshing by remember { mutableStateOf(false) }
    fun save(list: List<Show>) { shows = list; store.shows = list }
    fun update(id: Int, change: (Show) -> Show) = save(shows.map { if (it.id == id) change(it) else it })
    fun checkNow() {
        val wm = WorkManager.getInstance(ctx)
        val req = OneTimeWorkRequestBuilder<CheckWorker>().build()
        wm.enqueue(req)
        scope.launch {
            refreshing = true
            // Timeout: a failed check is rescheduled as a retry and never reaches a finished state.
            withTimeoutOrNull(60_000) { wm.getWorkInfoByIdFlow(req.id).first { it?.state?.isFinished == true } }
            shows = store.shows
            refreshing = false
        }
    }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    // The daily worker may have changed the list while the app was in the background.
    LifecycleResumeEffect(Unit) { shows = store.shows; onPauseOrDispose {} }

    val status = TABS[tab].first
    val list = shows.filter { it.status == status }
    val today = LocalDate.now()
    // From every tab: a finished show with an announced season must still show up here.
    val upcoming = shows.filter { (it.nextAir ?: "") >= today.toString() }.sortedBy { it.nextAir }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dizi", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = { dialog = "settings" }) { Icon(Icons.Default.Settings, "Ayarlar") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = "search" }) { Icon(Icons.Default.Add, "Dizi ekle") }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                TABS.forEachIndexed { i, (st, label) ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text("$label  ${shows.count { it.status == st }}") })
                }
            }
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { checkNow() }, modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp), // bottom: room for the FAB
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (upcoming.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Upcoming(upcoming, today) { selected = it.id }
                    }
                    if (list.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (shows.isEmpty()) "Liste boş. + ile dizi ekle ya da Ayarlar'dan Serializd'i içe aktar."
                            else "Bu sekmede dizi yok.",
                            Modifier.fillMaxWidth().padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        )
                    }
                    items(list, key = { it.id }) { show -> PosterCard(show) { selected = show.id } }
                }
            }
        }
    }

    shows.find { it.id == selected }?.let { show ->
        LaunchedEffect(show.id) { if (show.newSeason) update(show.id) { it.copy(newSeason = false) } }
        ShowSheet(
            show, today,
            onStatus = { st -> update(show.id) { if (st == Status.DONE) it.markDone() else it.copy(status = st) } },
            onToggleSeason = { n -> update(show.id) { it.toggleSeason(n) } },
            onDelete = {
                selected = 0
                save(shows.filter { it.id != show.id })
                scope.launch {
                    val r = snackbar.showSnackbar("${show.name} silindi", "Geri al", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) save(listOf(show) + shows)
                }
            },
            onClose = { selected = 0 },
        )
    }

    when (dialog) {
        "search" -> SearchDialog(
            store, TABS[tab].second,
            onAdd = { s -> save(listOf(s.copy(status = status)) + shows.filter { it.id != s.id }) },
            onClose = { dialog = "" },
        )
        "settings" -> SettingsDialog(
            store,
            onImported = { new ->
                // Shows already in the list take their Serializd status and watched seasons; the rest are added.
                val byId = new.associateBy { it.id }
                save(
                    shows.map { s -> byId[s.id]?.let { s.copy(status = it.status, watched = it.watched.ifEmpty { s.watched }) } ?: s } +
                        new.filter { n -> shows.none { it.id == n.id } },
                )
            },
            onTest = {
                // Pretend we knew one season less, so the next check announces a "new" season.
                shows.find { it.seasons > 1 }?.let { s ->
                    update(s.id) { it.copy(seasons = it.seasons - 1) }
                    checkNow()
                }
            },
            onClose = { dialog = "" },
        )
    }
}

/** Shows of the current tab with a dated next episode, soonest first. */
@Composable
fun Upcoming(shows: List<Show>, today: LocalDate, onClick: (Show) -> Unit) {
    Column {
        Text("Yaklaşanlar", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shows, key = { it.id }) { s ->
                val date = s.nextAir!!
                val relative = relativeDay(date, today)
                Column(Modifier.width(112.dp).clip(RoundedCornerShape(10.dp)).clickable { onClick(s) }) {
                    Box {
                        Poster(s, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
                        // "Bugün", "Yarın", "3 gün sonra" for this week; later dates speak for themselves.
                        if (withinWeek(date, today)) Text(
                            relative,
                            Modifier.padding(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Text(
                        longDate(date, today), Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                    )
                    Text(
                        s.nextEpisodeLabel(), maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        "${s.nextSeason}. sezon",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun PosterCard(show: Show, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Box {
            Poster(show, Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            if (show.newSeason) Text(
                "Yeni sezon",
                Modifier.padding(6.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            show.name, Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(progress(show), if (show.ended) "Final" else null).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "2/4 sezon" while seasons are partly watched, otherwise the season count. */
fun progress(show: Show): String? = when {
    show.seasons <= 0 -> null
    show.watched.isNotEmpty() && show.resumeAt != null -> "${show.watched.count { it <= show.seasons }}/${show.seasons} sezon"
    else -> "${show.seasons} sezon"
}

/** Poster image; while it loads or when there is none, the name on a plain tile. */
@Composable
fun Poster(show: Show, modifier: Modifier, size: String = "w342", label: Boolean = true) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (label) Text(
            show.name, Modifier.padding(8.dp),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        if (show.poster != null) AsyncImage(
            model = "https://image.tmdb.org/t/p/$size${show.poster}", contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
        )
    }
}
