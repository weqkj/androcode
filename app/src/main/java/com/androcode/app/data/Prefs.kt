package com.androcode.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class ProviderKind(
    val title: String,
    val openAiStyle: Boolean,
    val baseUrl: String,
    val model: String,
    val needsKey: Boolean,
) {
    ANTHROPIC("Anthropic", false, "https://api.anthropic.com/v1", "claude-sonnet-5-5", true),
    OPENAI("OpenAI", true, "https://api.openai.com/v1", "gpt-4o", true),
    OPENROUTER("OpenRouter", true, "https://openrouter.ai/api/v1", "openai/gpt-4o", true),
    GEMINI("Gemini", true, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", true),
    LOCAL("Local (llama.cpp / Ollama)", true, "http://127.0.0.1:8080/v1", "local", false),
    CUSTOM_OPENAI("Custom · OpenAI API", true, "", "", false),
    CUSTOM_ANTHROPIC("Custom · Anthropic API", false, "", "", true),
}

/** Encrypts secrets with an AES-GCM key that lives in the Android Keystore (never leaves the secure hardware). */
object Vault {
    private const val ALIAS = "androcode_vault"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun enc(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, key())
            val ct = c.doFinal(plain.toByteArray())
            Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
        } catch (e: Exception) {
            ""
        }
    }

    fun dec(blob: String): String {
        if (blob.isEmpty()) return ""
        return try {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
            String(c.doFinal(raw, 12, raw.size - 12))
        } catch (e: Exception) {
            ""
        }
    }
}

const val DEFAULT_SERVER_CMD =
    "PATH=\$PATH:/data/data/com.termux/files/usr/bin LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib " +
        "llama-server -m {model} --host 127.0.0.1 --port 8080 -c 4096 --jinja"

/** App-wide settings. Every property is Compose state, so UI reacts instantly. */
object Prefs {
    private lateinit var sp: SharedPreferences

    var onboarded by mutableStateOf(false); private set
    var nickname by mutableStateOf("Velvet Echo"); private set

    var themeMode by mutableStateOf(ThemeMode.SYSTEM); private set
    var dynamicColor by mutableStateOf(true); private set
    var seed by mutableIntStateOf(0xFF6750A4.toInt()); private set
    var amoled by mutableStateOf(false); private set
    var motionBlur by mutableStateOf(true); private set
    var blurStrength by mutableFloatStateOf(1f); private set
    var iconKey by mutableStateOf("default"); private set

    var useRoot by mutableStateOf(true); private set
    var autoApprove by mutableStateOf(false); private set
    var workDir by mutableStateOf("/sdcard/Androcode"); private set

    var provider by mutableStateOf(ProviderKind.ANTHROPIC); private set
    var baseUrl by mutableStateOf(""); private set
    var model by mutableStateOf(""); private set
    var apiKey by mutableStateOf(""); private set

    var localModelPath by mutableStateOf(""); private set
    var serverCmd by mutableStateOf(DEFAULT_SERVER_CMD); private set

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("androcode", Context.MODE_PRIVATE)
        onboarded = sp.getBoolean("onboarded", false)
        nickname = sp.getString("nick", null) ?: randomNick()
        themeMode = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM)
        dynamicColor = sp.getBoolean("dynamic", true)
        seed = sp.getInt("seed", seed)
        amoled = sp.getBoolean("amoled", false)
        motionBlur = sp.getBoolean("blur", true)
        blurStrength = sp.getFloat("blurStrength", 1f)
        iconKey = sp.getString("icon", "default") ?: "default"
        useRoot = sp.getBoolean("useRoot", true)
        autoApprove = sp.getBoolean("autoApprove", false)
        workDir = sp.getString("workDir", workDir) ?: workDir
        localModelPath = sp.getString("localModel", "") ?: ""
        serverCmd = sp.getString("serverCmd", DEFAULT_SERVER_CMD) ?: DEFAULT_SERVER_CMD
        val kind = runCatching { ProviderKind.valueOf(sp.getString("provider", "ANTHROPIC")!!) }
            .getOrDefault(ProviderKind.ANTHROPIC)
        loadProvider(kind)
    }

    private fun loadProvider(k: ProviderKind) {
        provider = k
        baseUrl = sp.getString("url_${k.name}", null) ?: k.baseUrl
        model = sp.getString("model_${k.name}", null) ?: k.model
        apiKey = Vault.dec(sp.getString("key_${k.name}", "") ?: "")
    }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) = sp.edit().apply(block).apply()

    fun finishOnboarding() { onboarded = true; edit { putBoolean("onboarded", true) } }
    fun resetOnboarding() { onboarded = false; edit { putBoolean("onboarded", false) } }
    fun updateNickname(v: String) { nickname = v; edit { putString("nick", v) } }
    fun shuffleNickname() = updateNickname(randomNick())

    fun updateThemeMode(v: ThemeMode) { themeMode = v; edit { putString("theme", v.name) } }
    fun updateDynamic(v: Boolean) { dynamicColor = v; edit { putBoolean("dynamic", v) } }
    fun updateSeed(v: Int) { seed = v; edit { putInt("seed", v) } }
    fun updateAmoled(v: Boolean) { amoled = v; edit { putBoolean("amoled", v) } }
    fun updateMotionBlur(v: Boolean) { motionBlur = v; edit { putBoolean("blur", v) } }
    fun updateBlurStrength(v: Float) { blurStrength = v; edit { putFloat("blurStrength", v) } }
    fun updateIconKey(v: String) { iconKey = v; edit { putString("icon", v) } }

    fun updateUseRoot(v: Boolean) { useRoot = v; edit { putBoolean("useRoot", v) } }
    fun updateAutoApprove(v: Boolean) { autoApprove = v; edit { putBoolean("autoApprove", v) } }
    fun updateWorkDir(v: String) { workDir = v; edit { putString("workDir", v) } }

    fun selectProvider(k: ProviderKind) {
        edit { putString("provider", k.name) }
        loadProvider(k)
    }
    fun updateBaseUrl(v: String) { baseUrl = v; edit { putString("url_${provider.name}", v) } }
    fun updateModel(v: String) { model = v; edit { putString("model_${provider.name}", v) } }
    fun updateApiKey(v: String) { apiKey = v; edit { putString("key_${provider.name}", Vault.enc(v.trim())) } }

    fun updateLocalModel(v: String) { localModelPath = v; edit { putString("localModel", v) } }
    fun updateServerCmd(v: String) { serverCmd = v; edit { putString("serverCmd", v) } }

    fun randomNick(): String {
        val adj = listOf("Velvet", "Obsidian", "Lunar", "Silent", "Cobalt", "Solar", "Cipher", "Amber", "Neon", "Quartz")
        val noun = listOf("Drift", "Echo", "Pulse", "Forge", "Kernel", "Vector", "Orbit", "Comet", "Prism", "Rune")
        return adj.random() + " " + noun.random()
    }
}
