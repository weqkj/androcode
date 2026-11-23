package com.androcode.app.agent

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import com.androcode.app.data.Prefs
import com.androcode.app.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.topjohnwu.superuser.Shell
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads GGUF models and starts / stops a local llama.cpp-compatible server through the root shell. */
object LocalModels {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dl: Job? = null

    var downloading by mutableStateOf(false); private set
    var progress by mutableFloatStateOf(0f); private set
    var status by mutableStateOf(""); private set
    val files = mutableStateListOf<File>()

    private const val LOG = "/data/local/tmp/androcode-server.log"

    fun dir(ctx: Context): File = (ctx.getExternalFilesDir("models") ?: File(ctx.filesDir, "models")).apply { mkdirs() }

    fun refresh(ctx: Context) {
        files.clear()
        files.addAll(dir(ctx).listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList())
    }

    fun delete(ctx: Context, f: File) {
        f.delete()
        if (Prefs.localModelPath == f.absolutePath) Prefs.updateLocalModel("")
        refresh(ctx)
    }

    fun download(ctx: Context, url: String, nameOverride: String) {
        if (downloading) return
        val name = nameOverride.ifBlank { url.substringBefore('?').substringAfterLast('/') }.ifBlank { "model.gguf" }
        val target = File(dir(ctx), name)
        val part = File(target.absolutePath + ".part")
        downloading = true
        progress = 0f
        status = tr("Скачиваю…", "Downloading…")
        dl = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 60_000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "Androcode/1.0")
                    if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(256 * 1024)
                            var read = 0L
                            var lastUi = 0L
                            while (isActive) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                read += n
                                val now = System.currentTimeMillis()
                                if (total > 0 && now - lastUi > 150) {
                                    lastUi = now
                                    val p = read.toFloat() / total
                                    withContext(Dispatchers.Main) {
                                        progress = p
                                        status = "${read / 1_048_576} / ${total / 1_048_576} MB"
                                    }
                                }
                            }
                        }
                    }
                    if (!isActive) { part.delete(); return@withContext }
                    part.renameTo(target)
                    Unit
                }
                if (target.exists()) {
                    status = tr("Готово: ${target.name}", "Done: ${target.name}")
                    Prefs.updateLocalModel(target.absolutePath)
                    progress = 1f
                } else status = tr("Отменено", "Cancelled")
            } catch (e: kotlinx.coroutines.CancellationException) {
                part.delete()
                status = tr("Отменено", "Cancelled")
            } catch (e: Exception) {
                part.delete()
                status = tr("Ошибка: ", "Error: ") + (e.message ?: e.toString())
            } finally {
                downloading = false
                refresh(ctx)
            }
        }
    }

    fun cancelDownload() { dl?.cancel() }

    suspend fun startServer(): String = withContext(Dispatchers.IO) {
        if (Prefs.localModelPath.isBlank()) return@withContext tr("Сначала выбери модель.", "Pick a model first.")
        val cmd = Prefs.serverCmd.replace("{model}", Tools.sq(Prefs.localModelPath))
        val script = "pkill -f llama-server 2>/dev/null; setsid nohup sh -c ${Tools.sq(cmd)} > $LOG 2>&1 &"
        Shell.cmd(script).exec()
        tr("Команда запуска отправлена. Подожди 10–60 с и нажми «Проверить».", "Start command sent. Wait 10–60 s, then press “Check”.")
    }

    suspend fun stopServer(): String = withContext(Dispatchers.IO) {
        Shell.cmd("pkill -f llama-server || killall llama-server").exec()
        tr("Сервер остановлен.", "Server stopped.")
    }

    suspend fun check(): String = withContext(Dispatchers.IO) {
        val base = Prefs.baseUrl.trimEnd('/').removeSuffix("/v1")
        try {
            val c = URL("$base/health").openConnection() as HttpURLConnection
            c.connectTimeout = 3000
            c.readTimeout = 3000
            val code = c.responseCode
            if (code in 200..299) tr("✓ Сервер отвечает", "✓ Server is responding") else "HTTP $code"
        } catch (e: Exception) {
            val log = Shell.cmd("tail -n 6 $LOG 2>/dev/null").exec().out.joinToString("\n")
            tr("Сервер не отвечает.", "Server is not responding.") + if (log.isNotBlank()) "\n$log" else ""
        }
    }
}
