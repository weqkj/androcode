package com.androcode.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.androcode.app.agent.AgentSession
import com.androcode.app.agent.AssistantItem
import com.androcode.app.agent.ErrorItem
import com.androcode.app.agent.RootManager
import com.androcode.app.agent.RootStatus
import com.androcode.app.agent.ToolItem
import com.androcode.app.agent.ToolStatus
import com.androcode.app.agent.UiItem
import com.androcode.app.agent.UserItem
import com.androcode.app.data.Prefs
import com.androcode.app.tr
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val items = AgentSession.items
    val busy = AgentSession.busy
    var input by rememberSaveable { mutableStateOf("") }
    val energy = remember { Animatable(0.12f) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val variant = rememberSaveable { (0..99).random() }

    LaunchedEffect(Unit) { RootManager.warmUp() }
    LaunchedEffect(items.size, busy) {
        if (items.isNotEmpty()) list.animateScrollToItem(items.size)
    }

    fun send(text: String) {
        AgentSession.send(text)
        input = ""
        scope.launch {
            energy.snapTo(1f)
            energy.animateTo(0.12f, tween(1500))
        }
    }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        ShapeField(Modifier.fillMaxSize(), energy.asState())
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            TopBar(onSettings)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (items.isEmpty()) {
                    EmptyState(variant) { send(it) }
                } else {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(items.size) { i -> ItemView(items[i]) }
                        item { ThinkingFooter(busy && items.lastOrNull().let { it !is ToolItem || it.status == ToolStatus.RUNNING || it.status == ToolStatus.DONE || it.status == ToolStatus.FAILED }) }
                    }
                }
            }
            InputBar(input, { input = it }, busy, onSend = { send(input) }, onStop = { AgentSession.stop() })
        }
    }
}

// ------------------------------------------------------------------------------------------------ top bar

@Composable
private fun TopBar(onSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BlobMark(30.dp, cs.primary, spinning = AgentSession.busy)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Androcode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            Text(
                Prefs.model.ifBlank { "—" },
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        val st = RootManager.status
        val (label, container, content) = when {
            !Prefs.useRoot -> Triple(tr("песочница", "sandbox"), cs.surfaceContainerHighest, cs.onSurfaceVariant)
            st == RootStatus.GRANTED -> Triple("root", cs.tertiaryContainer, cs.onTertiaryContainer)
            st == RootStatus.DENIED -> Triple(tr("нет root", "no root"), cs.errorContainer, cs.onErrorContainer)
            else -> Triple("root…", cs.surfaceContainerHighest, cs.onSurfaceVariant)
        }
        val bg by animateColorAsState(container, label = "chipbg")
        val fg by animateColorAsState(content, label = "chipfg")
        Surface(
            onClick = { if (st == RootStatus.DENIED || st == RootStatus.UNKNOWN) scope.launch { Prefs.updateUseRoot(true); RootManager.request(force = true) } },
            shape = CircleShape, color = bg, contentColor = fg,
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(fg))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        IconButton(onClick = { AgentSession.newChat() }) { Icon(Icons.Rounded.Refresh, tr("Новый чат", "New chat")) }
        IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, tr("Настройки", "Settings")) }
    }
}

// ------------------------------------------------------------------------------------------------ empty state

@Composable
private fun EmptyState(variant: Int, onPick: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val g = greetingFor(Prefs.nickname, variant)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        MorphHero(88.dp, Modifier.appear(0))
        Spacer(Modifier.size(20.dp))
        Text(
            g.title,
            style = TextStyle(
                fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp,
                brush = Brush.linearGradient(listOf(cs.primary, cs.tertiary)),
            ),
            modifier = Modifier.appear(100),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            g.subtitle,
            style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
            modifier = Modifier.appear(180),
        )
        Spacer(Modifier.size(24.dp))
        suggestions().forEachIndexed { i, s ->
            Surface(
                onClick = { onPick(s) },
                shape = RoundedCornerShape(24.dp),
                color = cs.surfaceContainerHigh.copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).appear(260 + i * 70, dy = 18.dp, blur = 12f),
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BlobMark(14.dp, when (i % 3) { 0 -> cs.primary; 1 -> cs.tertiary; else -> cs.secondary }, blob = if (i % 2 == 0) Blobs.Flower5 else Blobs.Cookie7)
                    Spacer(Modifier.width(14.dp))
                    Text(s, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ messages

@Composable
private fun ItemView(item: UiItem) {
    when (item) {
        is UserItem -> UserBubble(item.text)
        is AssistantItem -> AssistantMessage(item.text)
        is ToolItem -> ToolCard(item)
        is ErrorItem -> Surface(
            shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth().appear(0, 18.dp, 10f),
        ) {
            Text(item.text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().appear(0, 20.dp, 14f), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 26.dp, bottomEnd = 8.dp),
            color = cs.primaryContainer, contentColor = cs.onPrimaryContainer,
            modifier = Modifier.padding(start = 48.dp),
        ) {
            Text(text, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun splitCode(text: String): List<Pair<Boolean, String>> {
    val parts = text.split("```")
    return parts.mapIndexedNotNull { i, p ->
        if (i % 2 == 1) true to p.substringAfter('\n', p).trimEnd() else if (p.isNotBlank()) false to p.trim() else null
    }
}

@Composable
private fun AssistantMessage(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().appear(0, 20.dp, 14f)) {
        BlobMark(26.dp, cs.primary, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            splitCode(text).forEach { (isCode, body) ->
                if (isCode) {
                    Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceContainerHighest) {
                        Text(
                            body,
                            Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                        )
                    }
                } else Text(body, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun ThinkingFooter(show: Boolean) {
    val cs = MaterialTheme.colorScheme
    AnimatedVisibility(show) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            BlobMark(26.dp, cs.tertiary, blob = Blobs.Flower5, spinning = true)
            Spacer(Modifier.width(12.dp))
            Text(tr("Думаю…", "Thinking…"), color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ToolCard(t: ToolItem) {
    val cs = MaterialTheme.colorScheme
    var expanded by rememberSaveable(t.id) { mutableStateOf(false) }
    val icon: ImageVector = when (t.name) {
        "bash" -> Icons.Rounded.Terminal
        "read_file", "write_file" -> Icons.Rounded.Description
        "list_dir" -> Icons.Rounded.FolderOpen
        else -> Icons.Rounded.Language
    }
    val (statusText, sc, sf) = when (t.status) {
        ToolStatus.WAITING -> Triple(tr("ждёт разрешения", "needs approval"), cs.tertiaryContainer, cs.onTertiaryContainer)
        ToolStatus.RUNNING -> Triple(tr("выполняется", "running"), cs.primaryContainer, cs.onPrimaryContainer)
        ToolStatus.DONE -> Triple(tr("готово", "done"), cs.secondaryContainer, cs.onSecondaryContainer)
        ToolStatus.FAILED -> Triple(tr("ошибка", "failed"), cs.errorContainer, cs.onErrorContainer)
        ToolStatus.DENIED -> Triple(tr("отклонено", "denied"), cs.errorContainer, cs.onErrorContainer)
        ToolStatus.STOPPED -> Triple(tr("остановлено", "stopped"), cs.surfaceContainerHighest, cs.onSurfaceVariant)
    }
    val chipBg by animateColorAsState(sc, label = "tcbg")
    val chipFg by animateColorAsState(sf, label = "tcfg")

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = cs.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().appear(0, 22.dp, 16f).animateContentSize(spring(dampingRatio = 0.8f, stiffness = 400f)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(t.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(shape = CircleShape, color = chipBg, contentColor = chipFg) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (t.status == ToolStatus.RUNNING) {
                            CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = chipFg)
                            Spacer(Modifier.width(6.dp))
                        } else if (t.status == ToolStatus.DONE) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(statusText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerLowest) {
                Text(
                    (if (t.name == "bash") "$ " else "") + t.summary,
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp),
                    maxLines = if (expanded) 40 else 4, overflow = TextOverflow.Ellipsis,
                )
            }
            if (t.status == ToolStatus.WAITING) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { AgentSession.decide(t, false) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Отклонить", "Deny"))
                    }
                    Button(onClick = { AgentSession.decide(t, true) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("Разрешить", "Allow"))
                    }
                }
            }
            if (t.output.isNotBlank()) {
                Surface(
                    onClick = { expanded = !expanded },
                    shape = RoundedCornerShape(16.dp), color = cs.surfaceContainerHighest.copy(alpha = 0.6f),
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Вывод", "Output"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = cs.onSurfaceVariant)
                        }
                        Text(
                            t.output,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp),
                            maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
                            modifier = if (expanded) Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ input

@Composable
private fun InputBar(value: String, onChange: (String) -> Unit, busy: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val active = busy || value.isNotBlank()
    val bg by animateColorAsState(if (active) cs.primary else cs.surfaceContainerHighest, label = "sendbg")
    val corner by animateIntAsState(if (busy) 32 else 50, spring(dampingRatio = 0.6f, stiffness = 400f), label = "corner")
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = cs.surfaceContainerHigh,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            TextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(tr("Что сделать на телефоне?", "What should I do on the phone?")) },
                maxLines = 6,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            Box(
                Modifier.padding(6.dp).size(48.dp)
                    .clip(RoundedCornerShape(percent = corner))
                    .background(bg)
                    .clickable(enabled = active) { if (busy) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(busy, label = "sendicon") { b ->
                    Icon(
                        if (b) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward, null,
                        tint = if (active) cs.onPrimary else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
