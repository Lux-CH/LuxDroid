package ch.cclerc.luxapp.ui.stop.expanded

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

@Stable
class EntranceTracker {
    private val played = mutableSetOf<String>()

    fun claim(key: String): Boolean = played.add(key)
}

@Composable
fun rememberEntrancePlayback(tracker: EntranceTracker, key: String): Boolean =
    remember(tracker, key) { tracker.claim(key) }
