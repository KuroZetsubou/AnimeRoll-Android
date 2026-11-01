package com.kurozetsubou.animeroll.ui.mine

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurozetsubou.animeroll.PasskeyInfo
import com.kurozetsubou.animeroll.QueueSettings
import com.kurozetsubou.animeroll.TelegramChannel
import com.kurozetsubou.animeroll.TelegramStatus
import com.kurozetsubou.animeroll.ui.AppState
import com.kurozetsubou.animeroll.ui.components.*
import com.kurozetsubou.animeroll.ui.theme.AR
import kotlinx.coroutines.launch

/** Profilo: account, AniList, passkeys, NAS queue, Telegram sources, logout. */
@Composable
fun ProfileScreen(state: AppState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = state.api
    var passkeys by remember { mutableStateOf(emptyList<PasskeyInfo>()) }
    var queue by remember { mutableStateOf<QueueSettings?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var tg by remember { mutableStateOf<TelegramStatus?>(null) }
    var tgNext by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var channels by remember { mutableStateOf(emptyList<TelegramChannel>()) }
    val user = state.user

    suspend fun loadTelegram() {
        tg = runCatching { api.telegramStatus() }.getOrNull()
        if (tg?.loggedIn == true) channels = runCatching { api.telegramChannels() }.getOrDefault(emptyList())
    }
    LaunchedEffect(Unit) {
        state.refreshUser()
        passkeys = runCatching { api.passkeys() }.getOrDefault(emptyList())
        queue = runCatching { api.queueSettings() }.getOrNull()
        loadTelegram()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = AR.side, end = AR.side, bottom = 128.dp)) {
        item { ScreenTitle("Profilo", Modifier.statusBarsPadding().padding(top = 16.dp)) }
        group("Account") {
            Line(Icons.Default.Person, "Utente", user?.username ?: "")
            Line(Icons.Default.Dns, "Server", runCatching { Uri.parse(api.baseUrl).host }.getOrNull() ?: api.baseUrl)
        }
        group("AniList", "Oltre l'85% di un episodio il server aggiorna AniList. Il progresso non torna mai indietro e l'ultimo episodio imposta Completato.") {
            Line(Icons.Default.Link, if (user?.aniListConnected == true) "Collegato come ${user.aniListUserName ?: "AniList"}" else "Non collegato", null)
            Box(Modifier.padding(start = 16.dp, bottom = 14.dp)) {
                if (user?.aniListConnected == true) SecondaryButton("Scollega", null, small = true) {
                    scope.launch { runCatching { api.aniListDisconnect() }; state.refreshUser(); state.inList.clear(); state.planning = emptyList() }
                } else PrimaryButton("Collega AniList", null, small = true) {
                    // Sign-in happens in the browser; the server redirects back to animeroll:// and the app refreshes the user.
                    scope.launch {
                        runCatching { api.aniListConnectUrl() }
                            .onSuccess { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                            .onFailure { message = it.message }
                    }
                }
            }
        }
        group("Passkey") {
            passkeys.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, null, tint = AR.textSecondary, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(p.lastUsedAt?.take(10)?.let { "Ultimo uso: $it" } ?: "Mai usata", fontSize = 12.sp, color = AR.textTertiary)
                    }
                    IconButton({ scope.launch { runCatching { api.deletePasskey(p.id) }; passkeys = runCatching { api.passkeys() }.getOrDefault(passkeys) } }) {
                        Icon(Icons.Default.Delete, "Elimina", tint = AR.textSecondary)
                    }
                }
            }
            Action("Aggiungi passkey", Icons.Default.Add) {
                scope.launch {
                    runCatching { api.passkeyRegister(context as Activity, Build.MODEL) }
                        .onSuccess { message = "Passkey creata"; passkeys = runCatching { api.passkeys() }.getOrDefault(passkeys) }
                        .onFailure { message = it.message }
                }
            }
        }
        group("Download sul NAS", "Quanti lavori il NAS esegue insieme. Gli altri aspettano in coda in ordine di priorità.") {
            val q = queue
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Download contemporanei", fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                if (q == null) CircularProgressIndicator(Modifier.size(20.dp), color = AR.accent, strokeWidth = 2.dp)
                else {
                    fun set(v: Int) = scope.launch { queue = runCatching { api.setQueueSettings(QueueSettings(v)) }.getOrDefault(q) }
                    IconButton({ set(q.maxConcurrentDownloads - 1) }, enabled = q.maxConcurrentDownloads > 1) { Icon(Icons.Default.Remove, "Meno") }
                    Text("${q.maxConcurrentDownloads}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton({ set(q.maxConcurrentDownloads + 1) }, enabled = q.maxConcurrentDownloads < 10) { Icon(Icons.Default.Add, "Più") }
                }
            }
        }
        group("Fonti · Telegram") {
            val s = tg
            when {
                s == null -> Box(Modifier.padding(16.dp)) { CircularProgressIndicator(Modifier.size(22.dp), color = AR.accent, strokeWidth = 2.dp) }
                !s.configured -> Text("Configura ApiId, ApiHash e numero nel file .env del server.", color = AR.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                !s.loggedIn -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (tgNext != null) {
                        OutlinedTextField(code, { code = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = AR.cardShape,
                            label = { Text(if (tgNext == "password") "Password 2FA" else "Codice ricevuto su Telegram") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = if (tgNext == "password") KeyboardType.Password else KeyboardType.Number))
                        PrimaryButton("Verifica", null, small = true) {
                            scope.launch { runCatching { api.telegramLogin(code) }.onSuccess { tgNext = it.next; code = ""; loadTelegram() }.onFailure { message = it.message } }
                        }
                    } else {
                        Text("Collega il tuo account per cercare episodi nei canali.", fontSize = 14.sp, color = AR.textSecondary)
                        PrimaryButton("Invia codice", null, small = true) {
                            scope.launch { runCatching { api.telegramLogin(null) }.onSuccess { tgNext = it.next ?: "code" }.onFailure { message = it.message } }
                        }
                    }
                }
                else -> {
                    channels.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Switch(c.enabled, { on -> channels = channels.map { if (it.id == c.id) it.copy(enabled = on) else it } },
                                colors = SwitchDefaults.colors(checkedTrackColor = AR.accent, checkedThumbColor = Color.White))
                        }
                    }
                    if (channels.isEmpty()) Text("Nessun canale trovato.", color = AR.textSecondary, modifier = Modifier.padding(16.dp))
                    Box(Modifier.padding(start = 16.dp, bottom = 14.dp, top = 4.dp)) {
                        PrimaryButton("Salva", null, small = true) {
                            scope.launch { runCatching { api.saveTelegramChannels(channels) }.onSuccess { state.toast("Canali salvati") }.onFailure { message = it.message } }
                        }
                    }
                }
            }
        }
        message?.let { m -> item { Text(m, fontSize = 13.sp, color = AR.accent, modifier = Modifier.padding(top = 16.dp)) } }
        group(null) { Action("Esci", Icons.AutoMirrored.Filled.Logout, AR.danger) { state.logout() } }
    }
}

private fun LazyListScope.group(title: String?, note: String? = null, content: @Composable ColumnScope.() -> Unit) {
    item {
        Column {
            if (title != null) GroupTitle(title) else Spacer(Modifier.height(24.dp))
            Column(Modifier.fillMaxWidth().clip(AR.cardShape).background(AR.surface).padding(vertical = 4.dp), content = content)
            note?.let { Text(it, fontSize = 12.sp, color = AR.textTertiary, lineHeight = 17.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
        }
    }
}

@Composable
private fun Line(icon: ImageVector, label: String, value: String?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AR.textSecondary, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 14.dp))
        value?.let { Text(it, fontSize = 14.sp, color = AR.textTertiary) }
    }
}

@Composable
private fun Action(label: String, icon: ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (tint == Color.White) AR.textSecondary else tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint, modifier = Modifier.padding(start = 14.dp).weight(1f))
        if (tint == Color.White) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AR.textTertiary)
    }
}
