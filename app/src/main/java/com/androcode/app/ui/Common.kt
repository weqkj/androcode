package com.androcode.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Rounded Material 3 card used by every settings section. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Colour swatch with a springy selection state. */
@Composable
fun Swatch(brush: Brush, selected: Boolean, size: Int = 44, onClick: () -> Unit) {
    val sc by animateFloatAsState(if (selected) 1f else 0.82f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "sw")
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(3.dp, cs.primary) else null,
        modifier = Modifier.size(size.dp).scale(sc),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.padding(if (selected) 5.dp else 0.dp).clip(CircleShape).background(brush),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

data class IconOption(val key: String, val c1: Color, val c2: Color)

val IconOptions = listOf(
    IconOption("default", Color(0xFF8B6CFF), Color(0xFF3A2BA8)),
    IconOption("mint", Color(0xFF0B8F7A), Color(0xFF0B8F7A)),
    IconOption("coral", Color(0xFFE2563B), Color(0xFFE2563B)),
    IconOption("amber", Color(0xFFE8A100), Color(0xFFE8A100)),
    IconOption("sky", Color(0xFF1E7BE0), Color(0xFF1E7BE0)),
    IconOption("rose", Color(0xFFD6336C), Color(0xFFD6336C)),
    IconOption("mono", Color(0xFF1C1B1F), Color(0xFF1C1B1F)),
)

/** Mini launcher-icon preview (colour tile + cookie). */
@Composable
fun IconPreview(opt: IconOption, selected: Boolean, onClick: () -> Unit) {
    val sc by animateFloatAsState(if (selected) 1f else 0.86f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "ip")
    val cs = MaterialTheme.colorScheme
    val path = remember { Path() }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = if (selected) androidx.compose.foundation.BorderStroke(3.dp, cs.primary) else null,
        modifier = Modifier.size(52.dp).scale(sc),
    ) {
        Canvas(Modifier.padding(if (selected) 4.dp else 0.dp).clip(CircleShape).background(Brush.linearGradient(listOf(opt.c1, opt.c2)))) {
            path.blob(center.x, center.y, this.size.minDimension * 0.30f, 0.2f, Blobs.Cookie8)
            drawPath(path, Color(0xFFFFFBFF))
        }
    }
}

/** Switches the launcher icon colour by enabling exactly one `activity-alias`. */
object IconSwitcher {
    fun apply(ctx: Context, key: String) {
        val pm = ctx.packageManager
        IconOptions.forEach { o ->
            val cn = ComponentName(ctx.packageName, "com.androcode.app.Launcher" + o.key.replaceFirstChar { it.uppercase() })
            pm.setComponentEnabledSetting(
                cn,
                if (o.key == key) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
