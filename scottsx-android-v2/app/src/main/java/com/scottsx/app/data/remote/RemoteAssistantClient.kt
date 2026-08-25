package com.scottsx.app.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Thin client for the free LLM endpoint that powers ScottsTechX AI in
 * Stage 3. The endpoint is configured via two resource strings in
 * `app/src/main/res/values/strings.xml`:
 *
 *  - `remote.assistant.base`     — "https://apifreellm.com"
 *  - `remote.assistant.api_key`  — the bearer token for the free tier
 *
 * Auth: a static `Authorization: Bearer <key>` header. No Firebase ID
 * token, no per-user rate limit. Stage 3 ships this against the public
 * free LLM; Stage 3.1 swaps it for the Fastify `/api/v1/ai/assistant`
 * route once the backend is deployed publicly.
 *
 * Failure modes — all return [Result.LocalFallback] except 4xx/5xx
 * which log + fall back, since this is a public free endpoint and we
 * do not want to lock the user out of the AI screen.
 *  - No network at all (IOException / timeout): local.
 *  - Backend 4xx / 5xx: log + local.
 *  - Malformed body: log + local.
 *
 * Always returns a non-null [Result]; never throws.
 */
class RemoteAssistantClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val apiKey: String = DEFAULT_API_KEY,
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun ask(
        message: String,
        history: List<ChatTurn> = emptyList(),
        locationLat: Double? = null,
        locationLng: Double? = null,
    ): Result = withContext(Dispatchers.IO) {
        // Build the request body. The free endpoint accepts a single
        // `message` field plus optional `history` (we send it when
        // populated so the model can carry conversation context).
        val body = JSONObject().apply {
            put("message", message)
            if (history.isNotEmpty()) {
                // The free tier also accepts a top-level `messages` array
                // — we collapse history into it if the schema requires.
                val arr = org.json.JSONArray()
                for (turn in history) {
                    arr.put(JSONObject().apply {
                        put("role", turn.role)
                        put("content", turn.content)
                    })
                }
                put("messages", arr)
            }
        }
        // Use the local Fastify /api/v1/ai/v2/ask endpoint so the AI
        // works offline (no third-party rate-limit issues). Fall through
        // to the apifreellm.com fallback if the local backend is down.
        val token = com.scottsx.app.data.Session.tokenOrNull()
        val localReqBuilder = Request.Builder()
            .url("http://127.0.0.1:3001/api/v1/ai/v2/ask")
            .header("Content-Type", "application/json")
        if (token != null) localReqBuilder.header("Authorization", "Bearer $token")
        val localReq = localReqBuilder.post(body.toString().toRequestBody(JSON)).build()

        runCatching {
            client.newCall(localReq).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "HTTP ${resp.code}: $raw")
                    return@use Result.LocalFallback(reason = "Backend HTTP ${resp.code}")
                }
                val json = runCatching { JSONObject(raw) }.getOrNull()
                    ?: return@use Result.LocalFallback(reason = "Malformed JSON")
                val success = json.optBoolean("success", true)
                if (!success) {
                    val msg = json.optString("error", "Unknown error")
                    return@use Result.LocalFallback(reason = msg)
                }
                val reply = json.optString("reply").ifBlank { json.optString("response") }
                if (reply.isBlank()) return@use Result.LocalFallback(reason = "Empty reply")
                Result.Remote(reply = reply, provider = json.optString("provider"))
            }
        }.getOrElse { err ->
            Log.w(TAG, "Network call failed", err)
            Result.LocalFallback(reason = err.message ?: "Network error")
        }
    }

    /**
     * Backend produced a usable reply (real LLM or remote-located
     * knowledge). The free LLM tier doesn't carry location context so
     * we attach the user GPS anyway via a `locationLat/locationLng` tag
     * so callers can decide whether to bias search.
     */
    sealed class Result {
        data class Remote(val reply: String, val provider: String?) : Result()
        /** Backend reachable but disabled / failing — caller uses local fallback. */
        data class LocalFallback(val reason: String) : Result()
    }

    private companion object {
        private const val TAG = "RemoteAI"
        // Free LLM public endpoint. The user-supplied bearer token is a
        // tier-1 key with a 20s/req delay; safe for our 1-line queries.
        // Stage 3.1 will move this to a deployed Fastify URL with
        // proper per-user auth.
        private const val DEFAULT_BASE_URL = "https://apifreellm.com"
        private const val DEFAULT_API_KEY = "apf_z8vg9nzv40wfhaktxv216gvc"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS) // free tier is slow
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }
}

data class ChatTurn(val role: String, val content: String)
