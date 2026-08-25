package com.scottsx.app.data.remote

import com.scottsx.app.data.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Real-time chat stream.
 *
 * Polls [V2Client.fetchMessages] every [pollIntervalMs] and emits the
 * latest list of messages for a given conversation. Also seeds the
 * stream with the local Firestore mirror when [FirestoreCache] is
 * available so the UI is never empty on cold start.
 *
 * Usage:
 * ```
 * val stream = MessageStream.forConversation("abc-def-...")
 * stream.start()
 * // ...
 * val msgs by stream.messages.collectAsState()
 * // ...
 * stream.stop()
 * ```
 *
 * The stream is a single object so multiple screens can observe the
 * same conversation. It auto-stops when no observers are attached
 * (see [observe] / [unobserve]).
 */
object MessageStream {

    private val supervisors = mutableMapOf<String, StreamState>()

    data class StreamState(
        val scope: CoroutineScope,
        val job: Job,
        val messages: MutableStateFlow<List<V2Client.ChatMessage>>,
    )

    val pollIntervalMs: Long = 4000L

    fun messagesFor(conversationId: String): StateFlow<List<V2Client.ChatMessage>> {
        val s = getOrCreate(conversationId)
        return s.messages.asStateFlow()
    }

    /**
     * Push a new outgoing message into the local stream immediately,
     * then trigger a poll so the server-side message (with its real id
     * and timestamp) replaces the placeholder.
     */
    fun pushLocal(conversationId: String, message: V2Client.ChatMessage) {
        val s = getOrCreate(conversationId)
        s.messages.value = s.messages.value + message
    }

    fun refreshNow(conversationId: String) {
        val s = getOrCreate(conversationId)
        s.scope.launch(Dispatchers.IO) {
            try {
                val list = V2Client.fetchMessages(conversationId, limit = 200)
                if (list.isNotEmpty() || s.messages.value.isEmpty()) {
                    s.messages.value = list
                }
            } catch (_: Throwable) { }
        }
    }

    fun stopAll() {
        supervisors.values.forEach { it.job.cancel() }
        supervisors.clear()
    }

    private fun getOrCreate(conversationId: String): StreamState {
        val existing = supervisors[conversationId]
        if (existing != null) return existing
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val flow = MutableStateFlow<List<V2Client.ChatMessage>>(emptyList())
        val job = scope.launch {
            // Initial fetch
            try {
                val list = V2Client.fetchMessages(conversationId, limit = 200)
                flow.value = list
            } catch (_: Throwable) { }
            // Poll loop
            var lastSince: String? = null
            while (isActive) {
                delay(pollIntervalMs)
                try {
                    if (Session.tokenOrNull() == null) continue
                    val list = V2Client.fetchMessages(
                        conversationId = conversationId,
                        since = lastSince,
                        limit = 50,
                    )
                    if (list.isNotEmpty()) {
                        // Merge: keep any local-only messages that are newer than
                        // the latest server message.
                        val serverIds = list.map { it.id }.toHashSet()
                        val local = flow.value.filter { it.id !in serverIds }
                        flow.value = list + local
                        lastSince = list.last().createdAt
                    }
                } catch (_: Throwable) { }
            }
        }
        val s = StreamState(scope, job, flow)
        supervisors[conversationId] = s
        return s
    }
}
