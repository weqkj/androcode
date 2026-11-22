package com.androcode.app

import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.superuser.Shell
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                darkColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AndroCodeRootApp()
                }
            }
        }
    }
}

@Composable
fun AndroCodeRootApp() {
    val adjectives = listOf("Velvet", "Obsidian", "Lunar", "Silent", "Cobalt", "Solar", "Cipher")
    val nouns = listOf("Drift", "Echo", "Pulse", "Forge", "Kernel", "Vector", "Orbit")
    var nickname by remember { mutableStateOf(adjectives.random() + nouns.random()) }
    var isOnboarded by remember { mutableStateOf(false) }
    var inCall by remember { mutableStateOf(false) }
    var rootEnabled by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf("https://api.proxy-claude.example/v1") }
    var apiKey by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }

    when {
        inCall -> LiveVoiceCallScreen(onHangUp = { inCall = false })
        !isOnboarded -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Добро пожаловать", fontSize = 28.sp, fontWeight = FontWeight.Black)
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Твой никнейм") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(onClick = {
                    nickname = adjectives.random() + nouns.random()
                }) {
                    Text("Случайный английский ник")
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Base URL (Домен API)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Ключ домена") },
                    modifier = Modifier.fillMaxWidth()
                )
                FilledTonalButton(onClick = {
                    rootEnabled = Shell.getShell().isRoot
                }) {
                    Text(if (rootEnabled) "Root выдан (SU:ON)" else "Запросить Root (libsu)")
                }
            }
            Button(
                onClick = { isOnboarded = true },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Запустить AndroCode", fontWeight = FontWeight.Bold)
            }
        }
        else -> {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val greeting = when (hour) {
                in 5..11 -> "Доброе утро"
                in 12..17 -> "Добрый день"
                in 18..23 -> "Добрый вечер"
                else -> "Доброй ночи"
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AndroCode", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    FilledTonalIconButton(onClick = { inCall = true }) {
                        Icon(Icons.Rounded.Call, contentDescription = "Голосовой звонок")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$greeting, $nickname!", fontSize = 26.sp, fontWeight = FontWeight.Black)
                    if (output.isNotBlank()) {
                        Text(output, fontSize = 13.sp)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        placeholder = { Text("Команда или промпт...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(999.dp)
                    )
                    FilledIconButton(onClick = {
                        if (prompt.startsWith("su ")) {
                            val res = Shell.cmd(prompt.removePrefix("su ")).exec()
                            output = res.out.joinToString("\n")
                        } else {
                            output = "Запрос отправлен: $prompt"
                        }
                        prompt = ""
                    }) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "Отправить")
                    }
                }
            }
        }
    }
}

@Composable
fun LiveVoiceCallScreen(onHangUp: () -> Unit) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var availableRuVoices by remember { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    var selectedVoiceIdx by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                val ruVoices = tts?.voices
                    ?.filter { it.locale.language == "ru" && !it.isNetworkConnectionRequired }
                    ?.sortedBy { if (it.name.contains("google", ignoreCase = true)) 1 else 0 }
                    .orEmpty()
                availableRuVoices = ruVoices
                ruVoices.firstOrNull()?.let { tts?.voice = it }
                tts?.setSpeechRate(1.05f)
                tts?.setPitch(1.0f)
                tts?.speak("На связи. Говорите.", TextToSpeech.QUEUE_FLUSH, null, "init")
            }
        }
        tts = engine
        onDispose { engine.shutdown() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ГОЛОСОВОЙ ЗВОНОК", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("AndroCode Voice", fontSize = 28.sp, fontWeight = FontWeight.Black)
            if (availableRuVoices.size > 1) {
                FilledTonalButton(onClick = {
                    selectedVoiceIdx = (selectedVoiceIdx + 1) % availableRuVoices.size
                    val nextVoice = availableRuVoices[selectedVoiceIdx]
                    tts?.voice = nextVoice
                    tts?.speak("Голос переключён.", TextToSpeech.QUEUE_FLUSH, null, "switch")
                }) {
                    Text("Голос: ${availableRuVoices[selectedVoiceIdx].name.take(22)}")
                }
            }
        }
        Surface(
            modifier = Modifier.size(140.dp),
            shape = RoundedCornerShape(42.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GraphicEq, null, modifier = Modifier.size(64.dp))
            }
        }
        FilledIconButton(
            onClick = onHangUp,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Rounded.CallEnd, contentDescription = "Завершить", modifier = Modifier.size(32.dp))
        }
    }
}

fun callCustomEndpoint(baseUrl: String, apiKey: String, prompt: String): String {
    val conn = URL("${baseUrl.trimEnd('/')}/messages").openConnection() as HttpURLConnection
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("x-api-key", apiKey)
    conn.doOutput = true
    val body = """{"model":"claude-3-5-sonnet-latest","max_tokens":1024,"messages":[{"role":"user","content":"$prompt"}]}"""
    conn.outputStream.use { it.write(body.toByteArray()) }
    return conn.inputStream.bufferedReader().readText()
}
