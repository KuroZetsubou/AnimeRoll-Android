package com.kurozetsubou.animeroll

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import com.kurozetsubou.animeroll.ui.AppState
import com.kurozetsubou.animeroll.ui.MainScaffold
import com.kurozetsubou.animeroll.ui.login.LoginScreen
import com.kurozetsubou.animeroll.ui.theme.AR
import com.kurozetsubou.animeroll.ui.theme.AnimeRollTheme
import com.kurozetsubou.animeroll.User
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException

class MainActivity : ComponentActivity() {
    lateinit var api: Api
    lateinit var offline: OfflineStore
    /** Bumped when the app comes back (e.g. from the AniList sign-in in the browser), to refresh the user. */
    val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(AR.background.toArgb()), navigationBarStyle = SystemBarStyle.dark(AR.background.toArgb()))
        api = Api(this)
        offline = OfflineStore(applicationContext)
        setContent {
            AnimeRollTheme {
                Box(Modifier.fillMaxSize().background(AR.background)) { AppRoot(api, offline, resumeTick.intValue) }
            }
        }
    }

    override fun onResume() { super.onResume(); resumeTick.intValue++ }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); resumeTick.intValue++ }
}

@Composable
fun AppRoot(api: Api, offline: OfflineStore, resumeTick: Int) {
    var checked by remember { mutableStateOf(false) }
    var loggedIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state = remember { AppState(api, offline, scope) { api.token = null; api.cachedUser = null; loggedIn = false } }

    LaunchedEffect(Unit) {
        if (api.token != null) {
            // Away from home the NAS can hang instead of refusing: give up after a few seconds and open offline.
            runCatching { withTimeout(8_000) { api.me() } }
                .onSuccess { state.user = it; api.cachedUser = it; loggedIn = true }
                .onFailure { e ->
                    // NAS unreachable: open with the last user, device downloads still play.
                    if ((e is IOException || e is TimeoutCancellationException) && api.cachedUser != null) { state.user = api.cachedUser; state.isOffline = true; loggedIn = true }
                }
        }
        checked = true
    }
    LaunchedEffect(resumeTick, loggedIn) { if (loggedIn && resumeTick > 1) state.refreshUser() }

    when {
        !checked -> LaunchScreen()
        !loggedIn -> LoginScreen(api, onOffline = if (offline.items.isEmpty()) null else ({
            val guest = User("00000000-0000-0000-0000-000000000000", "Offline", false)
            state.user = guest; state.isOffline = true; loggedIn = true
        })) { user -> state.user = user; api.cachedUser = user; loggedIn = true }
        else -> MainScaffold(state)
    }
}

/** Shown at launch while the login is checked with the NAS. */
@Composable
fun LaunchScreen() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.dp)) {
            Row {
                Text("Anime", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, letterSpacing = (-1).sp)
                Text("Roll", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, letterSpacing = (-1).sp, color = AR.accent)
            }
            CircularProgressIndicator(color = AR.accent, modifier = Modifier.size(36.dp))
            Text("Caricamento…", color = AR.textSecondary, fontSize = 16.sp)
        }
    }
}
