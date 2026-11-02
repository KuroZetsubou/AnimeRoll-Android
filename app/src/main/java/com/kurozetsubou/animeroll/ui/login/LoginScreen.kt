package com.kurozetsubou.animeroll.ui.login

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurozetsubou.animeroll.Api
import com.kurozetsubou.animeroll.AuthResponse
import com.kurozetsubou.animeroll.User
import com.kurozetsubou.animeroll.ui.components.SecondaryButton
import com.kurozetsubou.animeroll.ui.components.PrimaryButton
import com.kurozetsubou.animeroll.ui.theme.AR
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(api: Api, onOffline: (() -> Unit)? = null, onLogin: (User) -> Unit) {
    val activity = LocalContext.current as Activity
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(api.baseUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run(block: suspend () -> AuthResponse) = scope.launch {
        error = null; busy = true
        api.baseUrl = server
        runCatching { block() }.onSuccess { onLogin(it.user) }.onFailure { error = it.message ?: "Errore di connessione" }
        busy = false
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AR.accent, unfocusedBorderColor = AR.hairline, focusedLabelColor = AR.accent,
        unfocusedContainerColor = AR.surface, focusedContainerColor = AR.surface, cursorColor = AR.accent)

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0xFF4A1F0C), 0.5f to AR.background, 1f to AR.background))) {
        Column(Modifier.align(Alignment.Center).widthIn(max = 460.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .systemBarsPadding().imePadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                Text("Anime", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, letterSpacing = (-1).sp)
                Text("Roll", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, letterSpacing = (-1).sp, color = AR.accent)
            }
            Text("Il tuo catalogo anime sul NAS", color = AR.textSecondary, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp))
            OutlinedTextField(server, { server = it }, label = { Text("Indirizzo NAS") }, singleLine = true, colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next), shape = AR.cardShape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, colors = fieldColors,
                shape = AR.cardShape, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, colors = fieldColors,
                visualTransformation = PasswordVisualTransformation(),
                shape = AR.cardShape, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            PrimaryButton(if (busy) "Accesso…" else "Accedi", null, Modifier.fillMaxWidth().height(52.dp),
                enabled = !busy && username.isNotBlank() && password.isNotBlank()) { run { api.login(username, password) } }
            SecondaryButton("Accedi con passkey", Icons.Default.Key, Modifier.fillMaxWidth().height(52.dp), ghost = true) {
                if (!busy) run { api.passkeyLogin(activity, username.ifBlank { null }) }
            }
            onOffline?.let { SecondaryButton("Guarda i download offline", Icons.Default.Key, Modifier.fillMaxWidth().height(52.dp), ghost = true) { it() } }
            error?.let { Text(it, color = AR.danger) }
        }
    }
}
