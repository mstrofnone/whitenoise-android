package dev.ipf.whitenoise.android.state

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val COMPOSER_TOOLBAR_KEY = "composer_toolbar_v1"

/** The AppState-owned host preference is shared across accounts, unlike per-draft expansion geometry. */
internal class ComposerToolbarPreference(
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
    private val commitMutex: Mutex,
) {
    var value by mutableStateOf(read())
        private set
    var saving by mutableStateOf(false)
        private set

    private fun read(): ComposerToolbarSettings =
        ComposerToolbarSettings.decode(
            runCatching {
                preferences.getString(COMPOSER_TOOLBAR_KEY, null)
            }.getOrNull(),
        )

    /** Publish only a completed edit and keep a failed disk write from changing either observable state or memory. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun save(
        settings: ComposerToolbarSettings,
        onComplete: (Boolean) -> Unit,
    ) {
        if (saving) {
            onComplete(false)
            return
        }
        val next = settings.normalized()
        if (next == value) {
            onComplete(true)
            return
        }
        saving = true
        scope.launch(start = CoroutineStart.ATOMIC) {
            try {
                commitMutex.withLock {
                    val previous = value
                    val committed =
                        withContext(NonCancellable + Dispatchers.IO) {
                            val success =
                                runCatching {
                                    preferences.edit().putString(COMPOSER_TOOLBAR_KEY, next.encode()).commit()
                                }.getOrDefault(false)
                            if (!success) {
                                // SharedPreferences updates its map before reporting a failed disk commit.
                                runCatching {
                                    preferences
                                        .edit()
                                        .putString(
                                            COMPOSER_TOOLBAR_KEY,
                                            previous.encode(),
                                        ).commit()
                                }
                            }
                            success
                        }
                    withContext(NonCancellable) {
                        if (committed) value = next
                        onComplete(committed)
                    }
                }
            } finally {
                withContext(NonCancellable) { saving = false }
            }
        }
    }
}
