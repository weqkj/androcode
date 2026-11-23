package com.androcode.app.agent

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.androcode.app.data.Prefs
import com.androcode.app.tr
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class UiItem
class UserItem(val text: String) : UiItem()
class AssistantItem(val text: String) : UiItem()
class ErrorItem(val text: String) : UiItem()

enum class ToolStatus { WAITING, RUNNING, DONE, FAILED, DENIED, STOPPED }

class ToolItem(val id: String, val name: String, val args: JSONObject) : UiItem() {
    var status by mutableStateOf(ToolStatus.RUNNING)
    var output by mutableStateOf("")
    internal var decision: CompletableDeferred<Boolean>? = null
    val summary: String get() = Tools.describe(name, args)
}

/** The agent loop: model → tool calls → results → model … until the model answers without a tool call. */
object AgentSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val turns = mutableListOf<Turn>()
    private var job: Job? = null

    val items = mutableStateListOf<UiItem>()
    var busy by mutableStateOf(false); private set

    private const val MAX_STEPS = 30

    private fun systemPrompt(): String {
        val root = if (RootManager.active) "ROOT GRANTED (commands run as uid 0 via su)" else "NO ROOT (commands run as the unprivileged app user)"
        return """
You are Androcode, an autonomous agent living on the user's Android phone (${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}).
You act through tools: bash, read_file, write_file, list_dir, web_fetch. Root status: $root. Working directory: ${Tools.cwd}.

Guidelines:
- Reply in the language the user writes in. Be concise and practical.
- Before using tools, say in one short sentence what you are about to do.
- The shell is Android's mksh + toybox: no bash-isms, a reduced coreutils. /sdcard is shared storage, /data/local/tmp is writable.
- With root you can create folders anywhere, inspect the system, manage the network and packages (pm, am, settings, cmd, ip, ping, curl if present).
- Prefer small, reversible steps. Verify results (ls, cat) after changing things.
- NEVER run destructive or irreversible commands (rm -rf on system or user data, dd/mkfs on block devices, wipe, flashing, factory reset, disabling security) without first asking the user for explicit confirmation in plain text.
- Never reveal, upload or exfiltrate secrets, keys, tokens or private files.
- If a command fails, read the error, adapt, and try a different approach instead of repeating the same call.
""".trim()
    }

    fun newChat() {
        stop()
        turns.clear()
        items.clear()
    }

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        items += UserItem(t)
        turns += Turn("user", listOf(Block.Text(t)))
        busy = true
        job = scope.launch {
            try {
                RootManager.warmUp()
                if (Tools.cwd == "/sdcard") Tools.cwd = Prefs.workDir
                Tools.shell("mkdir -p ${Tools.sq(Prefs.workDir)}", 15)
                loop()
            } catch (e: kotlinx.coroutines.CancellationException) {
                items += ErrorItem(tr("Остановлено.", "Stopped."))
                throw e
            } catch (e: Exception) {
                items += ErrorItem(e.message ?: e.toString())
            } finally {
                busy = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        items.filterIsInstance<ToolItem>().forEach {
            it.decision?.complete(false)
            if (it.status == ToolStatus.WAITING || it.status == ToolStatus.RUNNING) it.status = ToolStatus.STOPPED
        }
    }

    fun decide(item: ToolItem, allow: Boolean) {
        item.decision?.complete(allow)
    }

    private suspend fun loop() {
        val cfg = ProviderConfig(Prefs.provider, Prefs.baseUrl, Prefs.apiKey, Prefs.model)
        if (cfg.baseUrl.isBlank() || cfg.model.isBlank()) {
            items += ErrorItem(tr("Не настроен провайдер: открой Настройки → Провайдер.", "Provider is not configured: open Settings → Provider."))
            return
        }
        if (cfg.kind.needsKey && cfg.apiKey.isBlank()) {
            items += ErrorItem(tr("Не указан API-ключ: Настройки → Провайдер.", "API key is missing: Settings → Provider."))
            return
        }
        repeat(MAX_STEPS) {
            val reply = LlmClient.chat(cfg, systemPrompt(), turns, Tools.specs)
            val blocks = mutableListOf<Block>()
            if (reply.text.isNotBlank()) {
                blocks += Block.Text(reply.text)
                items += AssistantItem(reply.text)
            }
            blocks += reply.toolUses
            if (blocks.isEmpty()) {
                items += ErrorItem(tr("Модель вернула пустой ответ.", "The model returned an empty reply."))
                return
            }
            turns += Turn("assistant", blocks)
            if (reply.toolUses.isEmpty()) return

            val results = mutableListOf<Block>()
            for (use in reply.toolUses) {
                val item = ToolItem(use.id, use.name, use.input)
                items += item
                val allowed = if (Tools.needsApproval(use.name) && !Prefs.autoApprove) {
                    item.status = ToolStatus.WAITING
                    val d = CompletableDeferred<Boolean>()
                    item.decision = d
                    d.await()
                } else true
                if (!allowed) {
                    item.status = ToolStatus.DENIED
                    item.output = tr("Отклонено пользователем", "Denied by the user")
                    results += Block.ToolResult(use.id, "The user denied this action. Do not retry it; ask what to do instead.", true)
                    continue
                }
                item.status = ToolStatus.RUNNING
                val out = Tools.run(use.name, use.input)
                item.output = out.text
                item.status = if (out.isError) ToolStatus.FAILED else ToolStatus.DONE
                results += Block.ToolResult(use.id, out.text, out.isError)
            }
            turns += Turn("user", results)
        }
        items += ErrorItem(tr("Достигнут лимит шагов ($MAX_STEPS). Напиши «продолжай», если нужно.", "Step limit ($MAX_STEPS) reached. Say \"continue\" to go on."))
    }
}
