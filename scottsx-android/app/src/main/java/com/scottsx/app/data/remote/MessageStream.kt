package com.scottsx.app.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Lightweight polling ticker for chat screens. The thread screen collects
 * this flow and re-fetches messages on every tick, giving the "live" feel
 * without a websocket dependency.
 */
object MessageStream {
    fun ticker(intervalMs: Long = 3000): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(intervalMs)
        }
    }
}
