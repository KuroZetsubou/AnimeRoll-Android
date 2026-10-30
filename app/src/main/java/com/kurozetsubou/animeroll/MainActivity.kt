package com.kurozetsubou.animeroll

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val Accent = Color(0xFFFF7333)
val Bg = Color(0xFF0F0F14)

class MainActivity : ComponentActivity() {
    lateinit var api: Api

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        api = Api(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Bg)) {
                Surface(Modifier.fillMaxSize(), color = Bg) { AppRoot(api) }
            }
        }
    }
}

@Composable
fun AppRoot(api: Api) {
    var user by remember { mutableStateOf<User?>(null) }
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (api.token != null) user = runCatching { api.me() }.getOrNull()
        checked = true
    }
    when {
        !checked -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        user == null -> LoginScreen(api) { user = it }
        else -> MainScaffold(api) { api.token = null; user = null }
    }
}

@Composable
fun LoginScreen(api: Api, onLogin: (User) -> Unit) {
    val activity = LocalContext.current as MainActivity
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(api.baseUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> AuthResponse) = scope.launch {
        error = null
        api.baseUrl = server
        runCatching { block() }.onSuccess { onLogin(it.user) }.onFailure { error = it.message }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp).widthIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("▶ AnimeRoll", color = Accent, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text("Il tuo catalogo anime sul NAS", color = Color.Gray)
        OutlinedTextField(server, { server = it }, label = { Text("Indirizzo NAS") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button({ run { api.login(username, password) } }, enabled = username.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Accedi")
        }
        OutlinedButton({ run { api.passkeyLogin(activity, username.ifBlank { null }) } }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Key, null); Spacer(Modifier.width(8.dp)); Text("Accedi con passkey")
        }
        error?.let { Text(it, color = Color.Red) }
    }
}

@Composable
fun MainScaffold(api: Api, onLogout: () -> Unit) {
    val nav = rememberNavController()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(Triple("home", "Home", Icons.Default.Home), Triple("search", "Cerca", Icons.Default.Search),
        Triple("library", "Libreria", Icons.Default.GridView), Triple("downloads", "Download", Icons.Default.Download))
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF16161D)) {
                tabs.forEach { (route, label, icon) ->
                    NavigationBarItem(current == route, { nav.navigate(route) { launchSingleTop = true; popUpTo("home") } },
                        icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        },
    ) { pad ->
        NavHost(nav, "home", Modifier.padding(pad)) {
            composable("home") { HomeScreen(api, nav) }
            composable("search") { SearchScreen(api, nav) }
            composable("library") { LibraryScreen(api, nav) }
            composable("downloads") { DownloadsScreen(api, onLogout) }
            composable("anime/{id}") { TitleScreen(api, aniListId = it.arguments?.getString("id")?.toInt(), seriesId = null) }
            composable("series/{id}") { TitleScreen(api, aniListId = null, seriesId = it.arguments?.getString("id")?.toInt()) }
        }
    }
}

@Composable
fun Poster(title: String, url: String?, badge: String? = null, onClick: () -> Unit) {
    Column(Modifier.width(120.dp).clickable(onClick = onClick)) {
        Box {
            AsyncImage(url, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(120.dp, 180.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .08f)))
            badge?.let {
                Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp)
                    .background(Accent, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        Text(title, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Shelf(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp))
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
fun HomeScreen(api: Api, nav: NavController) {
    var home by remember { mutableStateOf<Home?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { api.home() }.onSuccess { home = it }.onFailure { error = it.message } }
    val h = home ?: return Box(Modifier.fillMaxSize(), Alignment.Center) { error?.let { Text(it) } ?: CircularProgressIndicator() }
    LazyColumn {
        val hero = h.aniListWatching.firstOrNull()?.media ?: h.trending.firstOrNull()
        hero?.let { m ->
            item {
                Box(Modifier.fillMaxWidth().height(380.dp).clickable { nav.navigate("anime/${m.id}") }) {
                    AsyncImage(m.bannerUrl ?: m.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
                    Text(m.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomStart).padding(16.dp))
                }
            }
        }
        if (h.continueWatching.isNotEmpty()) item {
            Shelf("Continua a guardare") { items(h.continueWatching) { c ->
                Poster("${c.series.title} · E${c.episode.label}", c.series.posterUrl) { nav.navigate("series/${c.series.id}") } } }
        }
        if (h.aniListWatching.isNotEmpty()) item {
            Shelf("Stai guardando su AniList") { items(h.aniListWatching) { e ->
                Poster(e.media.title, e.media.coverUrl, "${e.progress}/${e.media.episodes ?: "?"}") { nav.navigate("anime/${e.media.id}") } } }
        }
        if (h.recentlyAdded.isNotEmpty()) item {
            Shelf("Sul tuo NAS") { items(h.recentlyAdded) { s -> Poster(s.title, s.posterUrl, "${s.episodeCount} ep") { nav.navigate("series/${s.id}") } } }
        }
        item { Shelf("Questa stagione") { items(h.seasonal) { m -> Poster(m.title, m.coverUrl) { nav.navigate("anime/${m.id}") } } } }
        item { Shelf("Di tendenza") { items(h.trending) { m -> Poster(m.title, m.coverUrl) { nav.navigate("anime/${m.id}") } } } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun SearchScreen(api: Api, nav: NavController) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Media>()) }
    LaunchedEffect(q) {
        if (q.length < 2) return@LaunchedEffect
        delay(350)
        results = runCatching { api.search(q) }.getOrDefault(results)
    }
    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(q, { q = it }, placeholder = { Text("Cerca un anime") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        LazyVerticalGrid(GridCells.Adaptive(120.dp), Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            gridItems(results) { m -> Poster(m.title, m.coverUrl) { nav.navigate("anime/${m.id}") } }
        }
    }
}

@Composable
fun LibraryScreen(api: Api, nav: NavController) {
    var series by remember { mutableStateOf(emptyList<Series>()) }
    LaunchedEffect(Unit) { series = runCatching { api.library() }.getOrDefault(emptyList()) }
    LazyVerticalGrid(GridCells.Adaptive(120.dp), contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        gridItems(series) { s -> Poster(s.title, s.posterUrl, "${s.episodeCount}") { nav.navigate("series/${s.id}") } }
    }
}

@Composable
fun TitleScreen(api: Api, aniListId: Int?, seriesId: Int?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var media by remember { mutableStateOf<Media?>(null) }
    var series by remember { mutableStateOf<Series?>(null) }
    var entry by remember { mutableStateOf<Entry?>(null) }
    var episodes by remember { mutableStateOf(emptyList<Episode>()) }
    var playing by remember { mutableStateOf<Pair<Episode, StreamInfo>?>(null) }
    var showSources by remember { mutableStateOf(false) }

    LaunchedEffect(aniListId, seriesId) {
        var alId = aniListId
        if (seriesId != null) series = runCatching { api.series(seriesId) }.getOrNull().also { alId = it?.aniListId }
        alId?.let { id ->
            media = runCatching { api.media(id) }.getOrNull()
            entry = api.entry(id)
            if (series == null) series = api.seriesByAniList(id)
        }
        series?.let { episodes = runCatching { api.episodes(it.id) }.getOrDefault(emptyList()) }
    }

    playing?.let { (ep, info) ->
        VideoPlayer(info.url, info.resumeAt, onProgress = { pos, dur -> scope.launch { runCatching { api.progress(ep.id, pos, dur) } } },
            onClose = { playing = null })
        return
    }

    val title = media?.title ?: series?.title ?: ""
    LazyColumn {
        item {
            Box(Modifier.fillMaxWidth().height(240.dp)) {
                AsyncImage(media?.bannerUrl ?: series?.bannerUrl ?: media?.coverUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Bg))))
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(listOfNotNull(media?.year?.toString(), media?.format, media?.episodes?.let { "$it episodi" }).joinToString(" · "), color = Color.Gray)
                entry?.let { Text("AniList: ${it.progress}/${media?.episodes ?: "?"} visti", color = Accent, fontWeight = FontWeight.Bold) }
                Text((media?.description ?: series?.description).stripHtml(), maxLines = 6, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Button({ showSources = true }) { Icon(Icons.Default.CloudDownload, null); Spacer(Modifier.width(8.dp)); Text("Scarica sul NAS") }
            }
        }
        items(episodes) { ep ->
            ListItem(
                headlineContent = { Text("E${ep.label}  ${ep.title ?: ""}") },
                supportingContent = { if (ep.completed || (entry?.progress ?: 0) >= ep.number) Text("✓ visto", color = Color(0xFF4CAF50)) },
                trailingContent = {
                    IconButton({ scope.launch { runCatching { downloadToDevice(context, api.stream(ep.id).downloadUrl, "$title E${ep.label}") } } }) {
                        Icon(Icons.Default.Download, "Scarica sul dispositivo")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Bg),
                modifier = Modifier.clickable { scope.launch { runCatching { api.stream(ep.id) }.onSuccess { playing = ep to it } } },
            )
        }
    }
    if (showSources) SourcesDialog(api, title, media?.id ?: series?.aniListId) { showSources = false }
}

/** Saves the episode in the app's Movies folder with the system DownloadManager (continues in background). */
fun downloadToDevice(context: Context, url: String, name: String) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(DownloadManager.Request(Uri.parse(url))
        .setTitle(name)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, "${name.replace(Regex("[^\\w\\- ]"), "_")}.mp4"))
}

@Composable
fun SourcesDialog(api: Api, title: String, aniListId: Int?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(emptyList<Provider>()) }
    var provider by remember { mutableStateOf<Provider?>(null) }
    var results by remember { mutableStateOf(emptyList<SourceResult>()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { providers = runCatching { api.providers() }.getOrDefault(emptyList()).filter { it.enabled && it.canSearch }; provider = providers.firstOrNull() }
    LaunchedEffect(provider) { provider?.let { p -> results = runCatching { api.sourceSearch(p.id, title) }.getOrDefault(emptyList()) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("Chiudi") } },
        title = { Text("Scarica sul NAS") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { p -> FilterChip(p == provider, { provider = p }, label = { Text(p.name) }) }
                }
                message?.let { Text(it, color = Accent) }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(results) { r ->
                        ListItem(
                            headlineContent = { Text(r.title, maxLines = 2) },
                            supportingContent = { r.subtitle?.let { Text(it, maxLines = 1) } },
                            modifier = Modifier.clickable {
                                val p = provider ?: return@clickable
                                scope.launch {
                                    runCatching {
                                        val eps = if (p.canListEpisodes) api.sourceEpisodes(p.id, r.ref) else emptyList()
                                        val items = if (eps.isEmpty()) listOf(DownloadItem(r.ref)) else eps.map { DownloadItem(it.ref, it.number) }
                                        val alId = aniListId ?: eps.firstOrNull()?.extra?.get("anilistId")?.toIntOrNull()
                                        api.sendToNas(DownloadRequest(p.id, title, alId, items))
                                        items.size
                                    }.onSuccess { message = "Inviati $it download al NAS" }.onFailure { message = it.message }
                                }
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun DownloadsScreen(api: Api, onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    var jobs by remember { mutableStateOf(emptyList<Job>()) }
    LaunchedEffect(Unit) {
        while (true) { jobs = runCatching { api.jobs() }.getOrDefault(jobs); delay(3000) }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Download sul NAS", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onLogout) { Text("Esci") }
            }
        }
        items(jobs) { j ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .06f))) {
                Column(Modifier.padding(12.dp).fillMaxWidth()) {
                    Text("${j.seriesTitle}${j.episodeNumber?.let { " · E${it.toInt()}" } ?: ""}  (${j.source})", fontWeight = FontWeight.Bold)
                    val active = j.status in setOf("Queued", "Running", "PostProcessing")
                    if (active) LinearProgressIndicator({ j.progress.toFloat() }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Text("${j.status}${j.message?.let { " · $it" } ?: ""}", color = Color.Gray, fontSize = 12.sp)
                    if (active) TextButton({ scope.launch { runCatching { api.cancelJob(j.id) } } }) { Text("Annulla") }
                }
            }
        }
    }
}
