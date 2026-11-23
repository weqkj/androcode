package com.androcode.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androcode.app.agent.RootManager
import com.androcode.app.agent.RootStatus
import com.androcode.app.data.Prefs
import com.androcode.app.tr
import kotlinx.coroutines.launch

private const val LAST_PAGE = 4

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var page by rememberSaveable { mutableIntStateOf(0) }
    var accepted by rememberSaveable { mutableStateOf(false) }
    val energy = remember { Animatable(0.12f) }
    BackHandler(page > 0) { page-- }
    LaunchedEffect(page) {
        energy.snapTo(1f)
        energy.animateTo(0.12f, tween(1300))
    }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        ShapeField(Modifier.fillMaxSize(), energy)
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Box(Modifier.weight(1f)) {
                BlurSwitch(page, { it }) { p ->
                    when (p) {
                        0 -> WelcomePage(onHeroTap = { /* kicks via recomposition-free bounce */ })
                        1 -> DisclaimerPage(accepted) { accepted = it }
                        2 -> NickPage()
                        3 -> RootPage()
                        else -> ProviderPage()
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(88.dp)) {
                    if (page > 0) TextButton(onClick = { page-- }) { Text(tr("Назад", "Back")) }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    repeat(LAST_PAGE + 1) { i ->
                        val w by animateDpAsState(if (i == page) 30.dp else 9.dp, spring(dampingRatio = 0.55f, stiffness = 380f), label = "dot")
                        val c by animateColorAsState(if (i == page) cs.primary else cs.outlineVariant, label = "dotc")
                        Box(Modifier.padding(horizontal = 3.dp).height(9.dp).width(w).clip(CircleShape).background(c))
                    }
                }
                val enabled = page != 1 || accepted
                Button(
                    onClick = { if (page == LAST_PAGE) onDone() else page++ },
                    enabled = enabled,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.width(120.dp).height(52.dp),
                ) {
                    Text(
                        when (page) {
                            0 -> tr("Поехали", "Start")
                            1 -> tr("Принимаю", "Accept")
                            LAST_PAGE -> tr("Готово", "Done")
                            else -> tr("Далее", "Next")
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
private fun WelcomePage(onHeroTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    PageColumn {
        MorphHero(220.dp, Modifier.appear(0), onTap = onHeroTap)
        VSpace(28)
        Text(
            tr("Добро пожаловать в", "Welcome to"),
            style = MaterialTheme.typography.titleLarge,
            color = cs.onSurfaceVariant,
            modifier = Modifier.appear(120),
        )
        Text(
            "Androcode",
            style = TextStyle(
                fontSize = 52.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp,
                brush = Brush.linearGradient(listOf(cs.primary, cs.tertiary)),
            ),
            modifier = Modifier.appear(200),
        )
        VSpace(10)
        Text(
            tr(
                "ИИ-агент как Claude Code — только в твоём телефоне. С root он создаёт папки, запускает команды и сам выходит в сеть.",
                "An AI agent like Claude Code — right in your phone. With root it creates folders, runs commands and reaches the net on its own."
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = cs.onSurfaceVariant,
            modifier = Modifier.appear(320).padding(horizontal = 8.dp),
        )
    }
}

private data class Risk(val icon: ImageVector, val title: String, val body: String)

@Composable
private fun DisclaimerPage(accepted: Boolean, onAccept: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val risks = listOf(
        Risk(
            Icons.Rounded.Terminal,
            tr("Root — это полный контроль", "Root means full control"),
            tr(
                "Агент выполняет команды от имени суперпользователя: может удалить данные, изменить систему или сломать загрузку.",
                "The agent runs commands as superuser: it can delete data, alter the system or break boot."
            )
        ),
        Risk(
            Icons.Rounded.Warning,
            tr("Ответственность на тебе", "You are responsible"),
            tr(
                "Читай команды перед подтверждением и делай резервные копии. Автоподтверждение включай осознанно.",
                "Read commands before approving and keep backups. Enable auto-approve only on purpose."
            )
        ),
        Risk(
            Icons.Rounded.Key,
            tr("Ключи и данные", "Keys and data"),
            tr(
                "API-ключ хранится зашифрованно (Android Keystore). Диалог и вывод команд отправляются выбранному провайдеру; локальная модель оставляет всё на устройстве.",
                "The API key is stored encrypted (Android Keystore). The chat and command output are sent to your chosen provider; a local model keeps everything on-device."
            )
        ),
        Risk(
            Icons.Rounded.Shield,
            tr("Без гарантий", "No warranty"),
            tr(
                "ПО предоставляется «как есть». Проект независимый и не связан с Anthropic. Ответы ИИ бывают неверными.",
                "Provided “as is”. An independent project, not affiliated with Anthropic. AI answers can be wrong."
            )
        ),
    )
    PageColumn {
        Text(
            tr("Прежде чем начнём", "Before we begin"),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.appear(0),
        )
        VSpace(4)
        Text(
            tr("Короткий, но важный дисклеймер", "A short but important disclaimer"),
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.appear(60),
        )
        VSpace(14)
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = cs.surfaceContainerHigh.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxWidth().appear(120),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                risks.forEachIndexed { i, r ->
                    Row(Modifier.appear(180 + i * 70, dy = 14.dp, blur = 10f), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(
                                if (i < 2) cs.errorContainer else cs.secondaryContainer
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(r.icon, null, tint = if (i < 2) cs.onErrorContainer else cs.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(r.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(r.body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        VSpace(10)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = accepted, onCheckedChange = onAccept)
            Text(
                tr("Я прочитал(а) и понимаю риски", "I have read and understand the risks"),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun NickPage() {
    val cs = MaterialTheme.colorScheme
    var spin by remember { mutableStateOf(0f) }
    val rot by animateFloatAsState(spin, spring(dampingRatio = 0.55f, stiffness = 120f), label = "rot")
    PageColumn {
        BlobMark(96.dp, cs.tertiary, Modifier.appear(0), Blobs.Flower5, spinning = true)
        VSpace(20)
        Text(
            tr("Как тебя называть?", "What should I call you?"),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, modifier = Modifier.appear(80),
        )
        VSpace(6)
        Text(
            tr("Случайный английский ник — или придумай свой.", "A random English nick — or make up your own."),
            color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.appear(140),
        )
        VSpace(20)
        OutlinedTextField(
            value = Prefs.nickname,
            onValueChange = { Prefs.updateNickname(it) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().appear(200),
            trailingIcon = {
                IconButton(onClick = { Prefs.shuffleNickname(); spin += 360f }) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.rotate(rot))
                }
            },
        )
        VSpace(12)
        Text(
            tr("Привет, ${Prefs.nickname}!", "Hi, ${Prefs.nickname}!"),
            style = MaterialTheme.typography.titleLarge, color = cs.primary, fontWeight = FontWeight.Bold,
            modifier = Modifier.appear(260),
        )
    }
}

@Composable
private fun RootPage() {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val st = RootManager.status
    val color by animateColorAsState(
        when (st) {
            RootStatus.GRANTED -> cs.tertiary
            RootStatus.DENIED -> cs.error
            else -> cs.primary
        }, tween(500), label = "rootc"
    )
    PageColumn {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.appear(0)) {
            BlobMark(130.dp, color, blob = if (st == RootStatus.GRANTED) Blobs.Cookie9 else Blobs.Burst12, spinning = st == RootStatus.CHECKING)
            if (st == RootStatus.CHECKING) CircularProgressIndicator(Modifier.size(36.dp), color = cs.surface, strokeWidth = 3.dp)
            else Icon(Icons.Rounded.Terminal, null, tint = cs.surface, modifier = Modifier.size(44.dp))
        }
        VSpace(22)
        Text(
            when (st) {
                RootStatus.GRANTED -> tr("Root выдан", "Root granted")
                RootStatus.DENIED -> tr("Root не получен", "Root not granted")
                RootStatus.CHECKING -> tr("Жду ответа su…", "Waiting for su…")
                RootStatus.UNKNOWN -> tr("Выдай root-доступ", "Grant root access")
            },
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center, modifier = Modifier.appear(80),
        )
        VSpace(8)
        Text(
            when (st) {
                RootStatus.GRANTED -> tr(
                    "Отлично: агент сможет создавать папки, менять систему и пользоваться сетью без ограничений песочницы.",
                    "Great: the agent can create folders, modify the system and use the network without sandbox limits."
                )
                RootStatus.DENIED -> tr(
                    "Magisk / KernelSU / APatch отказали или не установлены. Можно продолжить без root — агент будет работать в песочнице приложения.",
                    "Magisk / KernelSU / APatch denied it or aren't installed. You can continue without root — the agent will work inside the app sandbox."
                )
                else -> tr(
                    "Нажми кнопку — менеджер root покажет запрос. Root нужен, чтобы агент мог создавать папки где угодно, управлять сетью и системой.",
                    "Tap the button — your root manager will show a prompt. Root lets the agent create folders anywhere and manage network and system."
                )
            },
            textAlign = TextAlign.Center, color = cs.onSurfaceVariant,
            modifier = Modifier.appear(140).padding(horizontal = 8.dp),
        )
        VSpace(22)
        FilledTonalButton(
            onClick = { scope.launch { Prefs.updateUseRoot(true); RootManager.request(force = true) } },
            enabled = st != RootStatus.CHECKING,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.height(54.dp).appear(200),
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (st == RootStatus.DENIED) tr("Попробовать снова", "Try again") else tr("Запросить root", "Request root"), fontWeight = FontWeight.Bold)
        }
        if (st != RootStatus.GRANTED) TextButton(onClick = { Prefs.updateUseRoot(false) }) {
            Text(tr("Работать без root", "Work without root"))
        }
    }
}

@Composable
private fun ProviderPage() {
    val cs = MaterialTheme.colorScheme
    PageColumn {
        Icon(Icons.Rounded.Cloud, null, tint = cs.primary, modifier = Modifier.size(56.dp).appear(0))
        VSpace(14)
        Text(
            tr("Подключи модель", "Connect a model"),
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black,
            modifier = Modifier.appear(60),
        )
        VSpace(6)
        Text(
            tr("Вставь ключ провайдера или выбери локальную модель. Всё можно поменять позже.", "Paste a provider key or pick a local model. You can change it all later."),
            color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.appear(120),
        )
        VSpace(16)
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = cs.surfaceContainerHigh.copy(alpha = 0.92f),
            modifier = Modifier.fillMaxWidth().appear(180),
        ) {
            Box(Modifier.padding(18.dp)) { ProviderSection() }
        }
    }
}
