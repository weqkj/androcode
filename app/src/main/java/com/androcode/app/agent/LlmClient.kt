package com.androcode.app.agent

import com.androcode.app.data.ProviderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed class Block {
    data class Text(val text: String) : Block()
    data class ToolUse(val id: String, val name: String, val input: JSONObject) : Block()
    data class ToolResult(val id: String, val content: String, val isError: Boolean) : Block()
}

data class Turn(val role: String, val blocks: List<Block>)
data class LlmReply(val text: String, val toolUses: List<Block.ToolUse>)
class ToolSpec(val name: String, val description: String, val schema: JSONObject)
data class ProviderConfig(val kind: ProviderKind, val baseUrl: String, val apiKey: String, val model: String)

/** Minimal dependency-free client for the two wire formats that cover practically every LLM provider. */
object LlmClient {

    suspend fun chat(cfg: ProviderConfig, system: String, turns: List<Turn>, tools: List<ToolSpec>): LlmReply =
        if (cfg.kind.openAiStyle) openAi(cfg, system, turns, tools) else anthropic(cfg, system, turns, tools)

    // ---------------------------------------------------------------- Anthropic Messages API
    private suspend fun anthropic(cfg: ProviderConfig, system: String, turns: List<Turn>, tools: List<ToolSpec>): LlmReply {
        val msgs = JSONArray()
        for (t in turns) {
            val content = JSONArray()
            for (b in t.blocks) when (b) {
                is Block.Text -> if (b.text.isNotBlank()) content.put(JSONObject().put("type", "text").put("text", b.text))
                is Block.ToolUse -> content.put(
                    JSONObject().put("type", "tool_use").put("id", b.id).put("name", b.name).put("input", b.input)
                )
                is Block.ToolResult -> content.put(
                    JSONObject().put("type", "tool_result").put("tool_use_id", b.id)
                        .put("content", b.content.ifEmpty { "(no output)" }).put("is_error", b.isError)
                )
            }
            if (content.length() > 0) msgs.put(JSONObject().put("role", t.role).put("content", content))
        }
        val toolArr = JSONArray()
        tools.forEach {
            toolArr.put(JSONObject().put("name", it.name).put("description", it.description).put("input_schema", it.schema))
        }
        val body = JSONObject()
            .put("model", cfg.model)
            .put("max_tokens", 4096)
            .put("system", system)
            .put("messages", msgs)
            .put("tools", toolArr)
        val raw = post(
            cfg.baseUrl.trimEnd('/') + "/messages",
            mapOf("x-api-key" to cfg.apiKey, "anthropic-version" to "2023-06-01", "content-type" to "application/json"),
            body.toString()
        )
        val json = JSONObject(raw)
        val sb = StringBuilder()
        val uses = mutableListOf<Block.ToolUse>()
        val arr = json.optJSONArray("content") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            when (o.optString("type")) {
                "text" -> sb.append(o.optString("text"))
                "tool_use" -> uses += Block.ToolUse(o.getString("id"), o.getString("name"), o.optJSONObject("input") ?: JSONObject())
            }
        }
        return LlmReply(sb.toString().trim(), uses)
    }

    // ---------------------------------------------------------------- OpenAI-compatible Chat Completions
    private suspend fun openAi(cfg: ProviderConfig, system: String, turns: List<Turn>, tools: List<ToolSpec>): LlmReply {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (t in turns) {
            if (t.role == "user") {
                val text = t.blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
                t.blocks.filterIsInstance<Block.ToolResult>().forEach {
                    msgs.put(
                        JSONObject().put("role", "tool").put("tool_call_id", it.id)
                            .put("content", it.content.ifEmpty { "(no output)" })
                    )
                }
                if (text.isNotBlank()) msgs.put(JSONObject().put("role", "user").put("content", text))
            } else {
                val text = t.blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
                val uses = t.blocks.filterIsInstance<Block.ToolUse>()
                if (text.isBlank() && uses.isEmpty()) continue
                val m = JSONObject().put("role", "assistant")
                m.put("content", if (text.isBlank()) JSONObject.NULL else text)
                if (uses.isNotEmpty()) {
                    val calls = JSONArray()
                    uses.forEach {
                        calls.put(
                            JSONObject().put("id", it.id).put("type", "function").put(
                                "function", JSONObject().put("name", it.name).put("arguments", it.input.toString())
                            )
                        )
                    }
                    m.put("tool_calls", calls)
                }
                msgs.put(m)
            }
        }
        val toolArr = JSONArray()
        tools.forEach {
            toolArr.put(
                JSONObject().put("type", "function").put(
                    "function", JSONObject().put("name", it.name).put("description", it.description).put("parameters", it.schema)
                )
            )
        }
        val body = JSONObject().put("model", cfg.model).put("messages", msgs)
        if (toolArr.length() > 0) body.put("tools", toolArr).put("tool_choice", "auto")
        val headers = mutableMapOf("content-type" to "application/json")
        if (cfg.apiKey.isNotBlank()) headers["authorization"] = "Bearer ${cfg.apiKey}"
        val raw = post(cfg.baseUrl.trimEnd('/') + "/chat/completions", headers, body.toString())
        val msg = JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
        val text = if (msg.isNull("content")) "" else msg.optString("content", "")
        val uses = mutableListOf<Block.ToolUse>()
        val calls = msg.optJSONArray("tool_calls")
        if (calls != null) for (i in 0 until calls.length()) {
            val c = calls.getJSONObject(i)
            val fn = c.getJSONObject("function")
            val args = runCatching { JSONObject(fn.optString("arguments", "{}").ifBlank { "{}" }) }.getOrDefault(JSONObject())
            uses += Block.ToolUse(c.optString("id", "call_$i"), fn.getString("name"), args)
        }
        return LlmReply(text.trim(), uses)
    }

    // ---------------------------------------------------------------- HTTP
    private suspend fun post(url: String, headers: Map<String, String>, body: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        // cancelling the coroutine (Stop button) tears the socket down immediately
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { if (it != null) conn.disconnect() }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 240_000
            conn.doOutput = true
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code: ${text.take(700)}")
            text
        } finally {
            handle?.dispose()
            conn.disconnect()
        }
    }
}
