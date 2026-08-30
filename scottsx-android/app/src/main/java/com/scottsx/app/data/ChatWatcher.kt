package com.scottsx.app.data

import android.content.Context
import com.scottsx.app.data.push.ScottsMessagingService
import com.scottsx.app.data.remote.V2Client
import kotlinx.coroutines.*

/**
 * Foreground chat watcher — the "someone messaged me while the app is
 * open" notifier.
 *
 * FCM pushes cover the background/screen-off case; this covers the
 * in-app case: while the user is browsing the shop, a lightweight
 * poller re-reads the conversation list every [POLL_MS] and compares
 * each thread's unread counter + last-message timestamp against the
 * previous snapshot. Any NEW inbound message from the other party
 * immediately posts a standard Android notification ("name: preview"),
 * so the user NEVER misses a chat — the requirement "when new
 * messages come in I am notified".
 *
 * Cheap: one small GET per interval, skipped entirely when signed
 * out or when nothing changed since the previous poll.
 */
object ChatWatcher {
    private const val POLL_MS = 15_000L
    private var job: Job? = null
    private var lastSnapshot: Map<String, Pair<Int, String>> = emptyMap()
    private var primed = false

    fun start(context: Context) {
        if (job != null) return
        job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            while (isActive) {
                try {
                    if (Session.tokenOrNull() != null) {
                        poll(context)
                    } else {
                        lastSnapshot = emptyMap()
                        primed = false
                    }
                } catch (_: Throwable) { /* network blips are retried on the next tick */ }
                delay(POLL_MS)
            }
        }
    }

    private suspend fun poll(context: Context) {
        val list = try {
            V2Client.fetchConversations()
        } catch (_: Throwable) { return }
        val snap = mutableMapOf<String, Pair<Int, String>>()
        for (c in list) {
            snap[c.conversationId] = (c.unreadCount to (c.lastMessageAt.orEmpty()))
            val prev = lastSnapshot[c.conversationId]
            val newerMessage = (c.lastMessageAt.orEmpty()) > (prev?.second ?: "")
            val unreadGrew = c.unreadCount > (prev?.first ?: 0)
            if (primed && unreadGrew && newerMessage && !c.lastMessagePreview.isNullOrBlank()) {
                ScottsMessagingService.notifyMessage(
                    context,
                    name = c.otherPartyDisplayName,
                    preview = c.lastMessagePreview ?: "",
                )
            }
        }
        lastSnapshot = snap
        primed = true
    }
}
