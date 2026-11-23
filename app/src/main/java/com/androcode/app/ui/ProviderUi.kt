package com.androcode.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import com.androcode.app.data.Prefs
import com.androcode.app.data.ProviderKind
import com.androcode.app.tr

/** Provider picker + Base URL + API key + model. Shared by onboarding and settings. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderSection() {
    var showKey by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProviderKind.entries.forEach { k ->
                FilterChip(
                    selected = Prefs.provider == k,
                    onClick = { Prefs.selectProvider(k) },
                    label = { Text(k.title) },
                )
            }
        }
        OutlinedTextField(
            value = Prefs.baseUrl,
            onValueChange = { Prefs.updateBaseUrl(it) },
            label = { Text("Base URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        )
        AnimatedVisibility(Prefs.provider != ProviderKind.LOCAL) {
            OutlinedTextField(
                value = Prefs.apiKey,
                onValueChange = { Prefs.updateApiKey(it) },
                label = { Text(tr("API-ключ", "API key")) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Key, null) },
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null)
                    }
                },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
            )
        }
        OutlinedTextField(
            value = Prefs.model,
            onValueChange = { Prefs.updateModel(it) },
            label = { Text(tr("Модель", "Model")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
        )
        Text(
            when (Prefs.provider) {
                ProviderKind.LOCAL -> tr(
                    "Локальный сервер (llama.cpp, Ollama, LM Studio по сети). Скачать модель и запустить сервер можно в Настройки → Локальная модель.",
                    "A local server (llama.cpp, Ollama, LM Studio over LAN). Download a model and start the server in Settings → Local model."
                )
                ProviderKind.CUSTOM_ANTHROPIC, ProviderKind.CUSTOM_OPENAI -> tr(
                    "Свой домен / прокси: укажи Base URL (обычно оканчивается на /v1) и ключ.",
                    "Your own domain / proxy: set the Base URL (usually ending in /v1) and the key."
                )
                else -> tr(
                    "Ключ хранится зашифрованным (Android Keystore) и отправляется только этому провайдеру.",
                    "The key is stored encrypted (Android Keystore) and only ever sent to this provider."
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
