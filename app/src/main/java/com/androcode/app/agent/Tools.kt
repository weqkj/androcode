package com.androcode.app.agent

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.androcode.app.data.Prefs
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class RootStatus { UNKNOWN, CHECKING, GRANTED, DENIED }

/** Owns the (single, persistent) root shell. */
object RootManager {
    var status by mutableStateOf(RootStatus.UNKNOWN); private set

    val active: Boolean get() = Prefs.useRoot && status == RootStatus.GRANTED

    /** Triggers the su prompt (Magisk / KernelSU / APatch). Pass force=true to retry after a denial. */
    suspend fun request(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        status = RootStatus.CHECKING
        try {
            if (force) Shell.getCachedShell()?.close()
            val ok = Shell.getShell().isRoot
            status = if (ok) RootStatus.GRANTED else RootStatus.DENIED
            ok
        } catch (e: Throwable) {
            status = RootStatus.DENIED
            false
        }
    }

    suspend fun warmUp() {
        if (status == RootStatus.UNKNOWN && Prefs.useRoot) request()
    }
}

class ToolOutcome(val text: String, val isError: Boolean = false)

object Tools {
    /** Persistent working directory — `cd` in one bash call carries over to the next. */
    var cwd: String = "/sdcard"

    val specs: List<ToolSpec> = listOf(
        ToolSpec(
            "bash",
            "Run a shell command on the Android device (mksh/toybox). Runs as root (uid 0) when root is granted, " +
                "otherwise as the unprivileged app user. The working directory persists between calls. " +
                "Output is truncated if very long. Default timeout 60s.",
            obj("command" to str("Shell command to run"), "timeout_sec" to int("Timeout in seconds (default 60, max 600)"), required = listOf("command"))
        ),
        ToolSpec(
            "read_file",
            "Read a text file. Returns at most `limit` bytes starting at byte `offset`.",
            obj("path" to str("Absolute or relative path"), "offset" to int("Start byte (default 0)"), "limit" to int("Max bytes (default 20000)"), required = listOf("path"))
        ),
        ToolSpec(
            "write_file",
            "Create or overwrite a file (parent folders are created automatically). Max ~90000 characters per call; " +
                "use append=true to write larger files in chunks.",
            obj("path" to str("Absolute or relative path"), "content" to str("Full file content"), "append" to bool("Append instead of overwrite"), required = listOf("path", "content"))
        ),
        ToolSpec(
            "list_dir",
            "List a directory (ls -la).",
            obj("path" to str("Directory, default current directory"), required = emptyList())
        ),
        ToolSpec(
            "web_fetch",
            "HTTP GET a URL from the phone's own connection and return the text (HTML is reduced to text, max ~15000 chars).",
            obj("url" to str("http(s) URL"), required = listOf("url"))
        ),
    )

    private fun str(d: String) = JSONObject().put("type", "string").put("description", d)
    private fun int(d: String) = JSONObject().put("type", "integer").put("description", d)
    private fun bool(d: String) = JSONObject().put("type", "boolean").put("description", d)
    private fun obj(vararg props: Pair<String, JSONObject>, required: List<String>): JSONObject {
        val p = JSONObject()
        props.forEach { p.put(it.first, it.second) }
        return JSONObject().put("type", "object").put("properties", p).put("required", JSONArray(required))
    }

    fun needsApproval(name: String) = name == "bash" || name == "write_file"

    fun describe(name: String, input: JSONObject): String = when (name) {
        "bash" -> input.optString("command")
        "read_file", "write_file", "list_dir" -> input.optString("path", ".")
        "web_fetch" -> input.optString("url")
        else -> input.toString()
    }

    suspend fun run(name: String, input: JSONObject): ToolOutcome = try {
        when (name) {
            "bash" -> {
                val (out, code) = shell(input.optString("command"), input.optInt("timeout_sec", 60).coerceIn(1, 600))
                ToolOutcome(if (code == 0) out else "$out\n[exit code $code]", code != 0)
            }
            "read_file" -> {
                val off = input.optLong("offset", 0)
                val lim = input.optLong("limit", 20000).coerceIn(1, 100000)
                val p = sq(input.getString("path"))
                val (out, code) = shell("tail -c +${off + 1} $p | head -c $lim", 30)
                ToolOutcome(out, code != 0)
            }
            "write_file" -> {
                val content = input.getString("content")
                if (content.length > 90_000) ToolOutcome("Content too large (${content.length}). Split it with append=true.", true)
                else {
                    val b64 = Base64.encodeToString(content.toByteArray(), Base64.NO_WRAP)
                    val p = sq(input.getString("path"))
                    val redirect = if (input.optBoolean("append", false)) ">>" else ">"
                    val (out, code) = shell("mkdir -p \"\$(dirname $p)\" && printf '%s' '$b64' | base64 -d $redirect $p && echo written", 30)
                    ToolOutcome(if (code == 0) "OK: ${content.length} chars → ${input.getString("path")}" else out, code != 0)
                }
            }
            "list_dir" -> {
                val (out, code) = shell("ls -la ${sq(input.optString("path", "."))}", 30)
                ToolOutcome(out, code != 0)
            }
            "web_fetch" -> webFetch(input.getString("url"))
            else -> ToolOutcome("Unknown tool: $name", true)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        ToolOutcome("Tool failed: ${e.message}", true)
    }

    /** single-quote a string for the shell */
    fun sq(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /**
     * Runs [cmd] with a hard timeout. `cd` is emulated: the final $PWD is captured and reused for the next call,
     * so the persistent working directory works even though every call is a fresh `sh -c`.
     */
    suspend fun shell(cmd: String, timeoutSec: Int): Pair<String, Int> = withContext(Dispatchers.IO) {
        val inner = "$cmd\n__ec=\$?\necho \"__CWD__\$PWD\"\nexit \$__ec"
        val full = "cd ${sq(cwd)} 2>/dev/null; timeout -s KILL $timeoutSec sh -c ${sq(inner)} 2>&1"
        val lines: List<String>
        val code: Int
        if (RootManager.active) {
            val r = Shell.cmd(full).exec()
            lines = r.out
            code = r.code
        } else {
            val p = ProcessBuilder("/system/bin/sh", "-c", full).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            code = p.waitFor()
            lines = text.lines()
        }
        val out = ArrayList<String>(lines.size)
        for (l in lines) {
            if (l.startsWith("__CWD__")) cwd = l.removePrefix("__CWD__").trim().ifEmpty { cwd } else out.add(l)
        }
        var text = out.joinToString("\n").trimEnd()
        if (code == 137) text += "\n[killed: timeout after ${timeoutSec}s]"
        truncate(text) to code
    }

    fun truncate(s: String, max: Int = 14_000): String =
        if (s.length <= max) s else s.take(max / 2) + "\n…[truncated ${s.length - max} chars]…\n" + s.takeLast(max / 2)

    private suspend fun webFetch(url: String): ToolOutcome = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Androcode/1.0 (Android)")
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        var body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if ((conn.contentType ?: "").contains("html", true)) {
            body = body.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
                .replace(Regex("(?s)<[^>]+>"), " ")
                .replace(Regex("&nbsp;"), " ")
                .replace(Regex("\\s+"), " ")
        }
        ToolOutcome("HTTP $code\n" + truncate(body.trim(), 15_000), code !in 200..299)
    }
}
