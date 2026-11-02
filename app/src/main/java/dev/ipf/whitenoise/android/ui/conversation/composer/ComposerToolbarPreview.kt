@file:Suppress("FunctionNaming")

package dev.ipf.whitenoise.android.ui.conversation.composer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.ipf.whitenoise.android.state.ComposerShortcut
import dev.ipf.whitenoise.android.state.ComposerToolbarSettings
import dev.ipf.whitenoise.android.ui.common.accountActionColors

private val PreviewPrimaryEndInset = 4.dp

/** Uses production geometry with synthetic text and inert adapters; it never acquires media or clipboard data. */
@Composable
@Suppress("LongMethod") // One inert production-pill preview keeps its geometry and overlay together.
internal fun ComposerToolbarPreview(settings: ComposerToolbarSettings) {
    val previewHeightLimit =
        ComposerToolbarMaximumHeight + ComposerActionRowHeight +
            with(LocalDensity.current) {
                MaterialTheme.typography.bodyLarge.lineHeight
                    .toDp()
            }
    val voiceSelected = ComposerShortcut.VoiceNote in settings.shortcuts
    val protectedWidth = if (voiceSelected) 0.dp else ComposerShortcutTarget
    var editingProgress by remember { mutableStateOf<State<Float>?>(null) }
    val primaryAlignment = remember { ComposerActionRowAlignment(Alignment.End) { editingProgress?.value ?: 0f } }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(vertical = ComposerToolbarGutter)
            .heightIn(max = previewHeightLimit)
            .testTag("composer.toolbar.preview"),
    ) {
        ComposerPill(
            textFieldValue = TextFieldValue(),
            composerFocus = remember { FocusRequester() },
            emojiPickerOpen = false,
            onValueChange = {},
            onEmojiPickerToggle = {},
            onAttachmentsToggle = {},
            attachmentSheetOpen = false,
            onPickFromGallery = {},
            onPickDocument = {},
            onDictation = {},
            hasCameraCapture = true,
            hasLocationShare = true,
            hasUserShare = true,
            hasContactShare = true,
            actionColors = accountActionColors(null),
            sendingIdentity = { Icon(painterResource(ComposerShortcut.SendingIdentity.iconRes), null) },
            inputFocusEnabled = false,
            toolbar =
                ComposerToolbarBindings(
                    settings,
                    settings.normalized().shortcuts,
                    emptyMap(),
                    enabled = false,
                    scrollEnabled = true,
                ),
            compactMeasurementWidth = maxWidth,
            expandedTrailingActionInset = protectedWidth,
            onEditingProgress = { editingProgress = it },
            modifier = Modifier.fillMaxWidth(),
            toolbarPreview = true,
        )
        if (!voiceSelected) {
            Box(
                Modifier
                    .align(primaryAlignment)
                    .testTag("composer.toolbar.preview.primary")
                    .width(ComposerShortcutTarget + PreviewPrimaryEndInset)
                    .height(ComposerShortcutTarget)
                    .padding(end = PreviewPrimaryEndInset),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(dev.ipf.whitenoise.android.R.drawable.ic_arrow_upward),
                    null,
                    Modifier.size(ComposerShortcutIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
