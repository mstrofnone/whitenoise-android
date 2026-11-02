@file:Suppress("FunctionNaming")

package dev.ipf.whitenoise.android.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.ipf.whitenoise.android.R
import dev.ipf.whitenoise.android.state.ComposerToolbarSettings
import dev.ipf.whitenoise.android.state.WhiteNoiseAppState
import dev.ipf.whitenoise.android.ui.conversation.composer.ComposerToolbarEditState
import dev.ipf.whitenoise.android.ui.conversation.composer.ComposerToolbarEditor

@Composable
internal fun ComposerSettingsScreen(
    appState: WhiteNoiseAppState,
    onBack: () -> Unit,
) {
    val preference = appState.composerToolbarPreference
    val edit = rememberComposerSettingsEditState(preference.value)
    SettingsScaffold(title = stringResource(R.string.composer_settings), onBack = onBack) {
        ComposerToolbarEditor(edit, preference.saving, preference::save, onBack, Modifier.fillMaxSize())
    }
}

/** Save only the uncommitted presentation choices; pointer/drag state never survives recreation. */
private val ComposerSettingsEditSaver =
    Saver<ComposerToolbarEditState, String>(
        save = { it.draft.encode() },
        restore = { ComposerToolbarEditState(ComposerToolbarSettings.decode(it)) },
    )

@Composable
internal fun rememberComposerSettingsEditState(initial: ComposerToolbarSettings): ComposerToolbarEditState =
    rememberSaveable(saver = ComposerSettingsEditSaver) { ComposerToolbarEditState(initial) }
