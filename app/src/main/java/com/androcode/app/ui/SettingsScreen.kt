package com.androcode.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.androcode.app.agent.LocalModels
import com.androcode.app.agent.RootManager
import com.androcode.app.agent.RootStatus
import com.androcode.app.data.Prefs
import com.androcode.app.data.ThemeMode
import com.androcode.app.tr
import kotlinx.coroutines.launch

private val SeedColors = listOf(
    0xFF6750A4, 0xFF1E6FD9, 0xFF00897B, 0xFF43A047, 0xFFF9A825, 0xFFF4511E, 0xFFD81B60, 0xFF546E7A,
).map { it.toInt() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onReplayWelcome: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(cs.background)) {
        val energy = remember { Animatable(0.06f) }
        ShapeField(Modifier.fillMaxSize(), energy.asState())
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Назад", "Back")) }
                Text(tr("Настройки", "Settings"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .navigationBarsPadding().padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---------------------------------------------------------------- appearance
                SectionCard(tr("Внешний вид", "Appearance"), Modifier.appear(0, 24.dp, 14f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { m ->
                            FilterChip(
                                selected = Prefs.themeMode == m, onClick = { Prefs.updateThemeMode(m) },
                                label = {
                                    Text(
                                        when (m) {
                                            ThemeMode.SYSTEM -> tr("Системная", "System")
                                            ThemeMode.LIGHT -> tr("Светлая", "Light")
                                            ThemeMode.DARK -> tr("Тёмная", "Dark")
                                        }
                                    )
                                },
                            )
                        }
                    }
                    SwitchRow(
                        tr("Цвета из обоев (Material You)", "Wallpaper colours (Material You)"),
                        tr("Динамическая палитра Android 12+", "Android 12+ dynamic palette"),
                        Prefs.dynamicColor,
                    ) { Prefs.updateDynamic(it) }
                    AnimatedVisibility(!Prefs.dynamicColor) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(tr("Акцентный цвет", "Accent colour"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                SeedColors.forEach { c ->
                                    Swatch(SolidColor(Color(c)), Prefs.seed == c) { Prefs.updateSeed(c) }
                                }
                            }
                            var hue by remember { mutableStateOf(ColorEngine.hueChroma(Prefs.seed).first.toFloat()) }
                            Text(tr("Свой оттенок", "Custom hue"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                            Slider(
                                value = hue,
                                onValueChange = { hue = it; Prefs.updateSeed(ColorEngine.fromHue(it.toDouble())) },
                                valueRange = 0f..360f,
                            )
                        }
                    }
                    SwitchRow(tr("AMOLED-чёрный", "AMOLED black"), tr("Чистый чёрный фон в тёмной теме", "True black background in dark theme"), Prefs.amoled) { Prefs.updateAmoled(it) }
                    SwitchRow(
                        tr("Motion blur", "Motion blur"),
                        tr("Размытие в движении, как в After Effects", "After Effects-style motion blur"),
                        Prefs.motionBlur,
                    ) { Prefs.updateMotionBlur(it) }
                    AnimatedVisibility(Prefs.motionBlur) {
                        Column {
                            Text(tr("Сила размытия", "Blur strength"), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                            Slider(value = Prefs.blurStrength, onValueChange = { Prefs.updateBlurStrength(it) }, valueRange = 0.3f..2f)
                        }
                    }
                }

                // ---------------------------------------------------------------- app icon
                SectionCard(tr("Иконка приложения", "App icon"), Modifier.appear(80, 24.dp, 14f)) {
                    Text(
                        tr(
                            "Выбери цвет иконки. В Android 13+ ещё можно включить «Тематические значки» в настройках обоев — иконка перекрасится под обои.",
                            "Pick the icon colour. On Android 13+ you can also enable “Themed icons” in wallpaper settings — the icon then follows your wallpaper."
                        ),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconOptions.forEach { o ->
                            IconPreview(o, Prefs.iconKey == o.key) {
                                Prefs.updateIconKey(o.key)
                                IconSwitcher.apply(ctx, o.key)
                            }
                        }
                    }
                }

                // ---------------------------------------------------------------- agent & root
                SectionCard(tr("Агент и root", "Agent & root"), Modifier.appear(160, 24.dp, 14f)) {
                    val st = RootManager.status
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (st) {
                                    RootStatus.GRANTED -> tr("Root выдан", "Root granted")
                                    RootStatus.DENIED -> tr("Root не выдан", "Root not granted")
                                    RootStatus.CHECKING -> tr("Проверяю…", "Checking…")
                                    RootStatus.UNKNOWN -> tr("Root не проверялся", "Root not checked yet")
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                tr("Magisk · KernelSU · APatch", "Magisk · KernelSU · APatch"),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                            )
                        }
                        FilledTonalButton(
                            onClick = { scope.launch { Prefs.updateUseRoot(true); RootManager.request(force = true) } },
                            enabled = st != RootStatus.CHECKING,
                        ) { Text(tr("Запросить", "Request")) }
                    }
                    SwitchRow(
                        tr("Использовать root", "Use root"),
                        tr("Выключи, чтобы агент работал только в песочнице приложения", "Turn off to keep the agent inside the app sandbox"),
                        Prefs.useRoot,
                    ) { Prefs.updateUseRoot(it) }
                    SwitchRow(
                        tr("Выполнять без подтверждения", "Auto-approve actions"),
                        tr("Опасно: bash и запись файлов будут выполняться сразу", "Dangerous: bash and file writes run immediately"),
                        Prefs.autoApprove,
                    ) { Prefs.updateAutoApprove(it) }
                    OutlinedTextField(
                        value = Prefs.workDir, onValueChange = { Prefs.updateWorkDir(it) },
                        label = { Text(tr("Рабочая папка", "Working directory")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    )
                    OutlinedTextField(
                        value = Prefs.nickname, onValueChange = { Prefs.updateNickname(it) },
                        label = { Text(tr("Ник", "Nickname")) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                    )
                }

                // ---------------------------------------------------------------- provider
                SectionCard(tr("Провайдер", "Provider"), Modifier.appear(240, 24.dp, 14f)) { ProviderSection() }

                // ---------------------------------------------------------------- local model
                SectionCard(tr("Локальная модель", "Local model"), Modifier.appear(320, 24.dp, 14f)) { LocalModelSection() }

                // ---------------------------------------------------------------- about
                SectionCard(tr("О приложении", "About"), Modifier.appear(400, 24.dp, 14f)) {
                    Text("Androcode 1.0.0", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        tr(
                            "Независимый open-source проект. Не связан с Anthropic. Используй на свой риск.",
                            "An independent open-source project. Not affiliated with Anthropic. Use at your own risk."
                        ),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    )
                    TextButton(onClick = onReplayWelcome) { Text(tr("Показать приветствие и дисклеймер", "Replay welcome & disclaimer")) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalModelSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var url by rememberSaveable { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { LocalModels.refresh(ctx) }

    Text(
        tr(
            "Скачай модель .gguf (например, с Hugging Face) и запусти локальный сервер. Для работы с инструментами нужна модель с поддержкой tool calling.",
            "Download a .gguf model (e.g. from Hugging Face) and start a local server. Tool use needs a model with tool-calling support."
        ),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
    )
    OutlinedTextField(
        value = url, onValueChange = { url = it },
        label = { Text(tr("Ссылка на .gguf", ".gguf URL")) },
        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { LocalModels.download(ctx, url.trim(), "") }, enabled = !LocalModels.downloading && url.isNotBlank()) {
            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("Скачать", "Download"))
        }
        if (LocalModels.downloading) OutlinedButton(onClick = { LocalModels.cancelDownload() }) { Text(tr("Отмена", "Cancel")) }
    }
    AnimatedVisibility(LocalModels.downloading || LocalModels.status.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (LocalModels.downloading) LinearProgressIndicator(progress = { LocalModels.progress }, modifier = Modifier.fillMaxWidth())
            Text(LocalModels.status, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
    LocalModels.files.forEach { f ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = Prefs.localModelPath == f.absolutePath, onClick = { Prefs.updateLocalModel(f.absolutePath) })
            Column(Modifier.weight(1f)) {
                Text(f.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${f.length() / 1_048_576} MB", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = { LocalModels.delete(ctx, f) }) { Icon(Icons.Rounded.Delete, tr("Удалить", "Delete")) }
        }
    }
    OutlinedTextField(
        value = Prefs.serverCmd, onValueChange = { Prefs.updateServerCmd(it) },
        label = { Text(tr("Команда сервера ({model} — путь к модели)", "Server command ({model} = model path)")) },
        minLines = 2, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = { scope.launch { msg = LocalModels.startServer() } }) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Запустить", "Start"))
        }
        OutlinedButton(onClick = { scope.launch { msg = LocalModels.stopServer() } }) {
            Icon(Icons.Rounded.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Стоп", "Stop"))
        }
        TextButton(onClick = { scope.launch { msg = LocalModels.check() } }) { Text(tr("Проверить", "Check")) }
    }
    if (msg.isNotBlank()) Text(msg, style = MaterialTheme.typography.bodySmall, color = cs.primary)
    Text(
        tr(
            "Нужен бинарник llama-server (в Termux: pkg install llama-cpp) и root. Потом выбери провайдер «Local».",
            "Requires a llama-server binary (in Termux: pkg install llama-cpp) and root. Then pick the “Local” provider."
        ),
        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
    )
}
