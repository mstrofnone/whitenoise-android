@file:Suppress("FunctionNaming")

package dev.ipf.whitenoise.android.ui.conversation.composer

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.ipf.whitenoise.android.R
import dev.ipf.whitenoise.android.state.ComposerShortcut
import dev.ipf.whitenoise.android.state.ComposerToolbarLayout
import dev.ipf.whitenoise.android.state.ComposerToolbarSettings
import dev.ipf.whitenoise.android.ui.common.fadingVerticalScroll

private val EditorContentInset = 12.dp

/** Shared, inert customization surface. Only Done hands one complete preference transaction to its owner. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("LongMethod", "LongParameterList") // Owns one save/back session around the shared editor content.
internal fun ComposerToolbarEditor(
    state: ComposerToolbarEditState,
    saving: Boolean,
    onSave: (ComposerToolbarSettings, (Boolean) -> Unit) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    overlayBackRegistrar: ComposerOverlayBackRegistrar? = null,
) {
    var failed by remember(state, state.draft) { mutableStateOf(false) }
    var active by remember(state) { mutableStateOf(true) }
    val latestCancel by rememberUpdatedState(onCancel)
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf(Rect.Zero) }
    DisposableEffect(state) {
        active = true
        onDispose {
            active = false
            state.cancelDrag()
        }
    }
    BackHandler { if (!saving) latestCancel() }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ComposerPlatformPreImeBackHandler(true, { if (!saving) latestCancel() }, overlayBackRegistrar)
    }
    ComposerToolbarAutoScroll(state, scroll) { viewport }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier =
            modifier.testTag("composer.customize").pointerInput(Unit) {
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            },
    ) {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { latestCancel() }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    failed = false
                    state.cancelDrag()
                    onSave(state.draft) { success ->
                        if (active) {
                            if (success) latestCancel() else failed = true
                        }
                    }
                }, enabled = !saving, modifier = Modifier.testTag("composer.customize.done")) {
                    Text(stringResource(R.string.done))
                }
            }
            if (failed) {
                Text(
                    stringResource(R.string.composer_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = EditorContentInset),
                )
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .onGloballyPositioned {
                        val next = it.boundsInRoot()
                        if (viewport != Rect.Zero && viewport != next) state.cancelDrag()
                        viewport = next
                    }.fadingVerticalScroll(scroll)
                    .padding(horizontal = EditorContentInset),
            ) {
                ComposerToolbarPreview(state.draft)
                ComposerToolbarLayoutChoices(state, saving)
                ComposerToolbarPalette(state, saving)
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun ComposerShortcutEditRow(
    shortcut: ComposerShortcut,
    state: ComposerToolbarEditState,
    selected: Boolean,
    index: Int,
    saving: Boolean,
) {
    var bounds by remember(shortcut, selected) { mutableStateOf(Rect.Zero) }
    val label = stringResource(shortcut.labelRes)
    val highlighted = state.isInsertionTarget(shortcut)
    val accessibilityActions = composerShortcutAccessibilityActions(shortcut, state, selected, index, saving)
    val insertionColor = MaterialTheme.colorScheme.primary
    val insertAfter = state.insertion == state.draft.shortcuts.count { it != state.dragging }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("composer.customize.${if (selected) "selected" else "available"}.${shortcut.id}")
                .drawWithContent {
                    drawContent()
                    if (highlighted) {
                        val y = if (insertAfter) size.height - 1.dp.toPx() else 1.dp.toPx()
                        drawLine(insertionColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                    }
                }.onGloballyPositioned {
                    bounds =
                        Rect(
                            it.positionInRoot(),
                            androidx.compose.ui.geometry
                                .Size(it.size.width.toFloat(), it.size.height.toFloat()),
                        )
                    if (selected) state.record(shortcut, bounds)
                },
    ) {
        BoxWithConstraints {
            val labelTextSize =
                with(LocalDensity.current) {
                    MaterialTheme.typography.bodyLarge.fontSize
                        .toDp()
                }
            val inlineControlsWidth = ComposerShortcutTarget * 4 + ComposerShortcutIconSize + ComposerToolbarGutter * 2
            val minimumLabelWidth = maxOf(ComposerMinimumTextWidth, labelTextSize * 6)
            val controlsBelow = maxWidth - inlineControlsWidth < minimumLabelWidth
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ComposerShortcutDragHandle(shortcut, state, selected, saving)
                    if (!controlsBelow) {
                        if (shortcut == ComposerShortcut.VoiceNote) {
                            MiniWaveformGlyph(MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Icon(painterResource(shortcut.iconRes), null, Modifier.size(ComposerShortcutIconSize))
                        }
                    }
                    Text(
                        label,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = if (controlsBelow) 0.dp else ComposerToolbarGutter)
                                .testTag("composer.customize.label.${shortcut.id}")
                                .semantics { customActions = accessibilityActions },
                    )
                    if (selected && !controlsBelow) ComposerShortcutMoveControls(shortcut, state, index, saving)
                    ComposerShortcutToggleButton(shortcut, state, selected, saving)
                }
                if (selected && controlsBelow) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        ComposerShortcutMoveControls(shortcut, state, index, saving)
                    }
                }
            }
        }
    }
}

@Composable
private fun ComposerShortcutDragHandle(
    shortcut: ComposerShortcut,
    state: ComposerToolbarEditState,
    selected: Boolean,
    saving: Boolean,
) {
    var handleBounds by remember(shortcut, selected) { mutableStateOf(Rect.Zero) }
    val currentSaving by rememberUpdatedState(saving)
    Box(
        Modifier
            .size(ComposerShortcutTarget)
            .testTag("composer.customize.drag.${shortcut.id}")
            .onGloballyPositioned {
                handleBounds =
                    Rect(
                        it.positionInRoot(),
                        androidx.compose.ui.geometry
                            .Size(it.size.width.toFloat(), it.size.height.toFloat()),
                    )
            }.pointerInput(shortcut, selected, state) {
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!currentSaving) {
                            val start =
                                awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                    state.start(shortcut, handleBounds.topLeft + change.position)
                                    change.consume()
                                }
                            if (start != null) {
                                val completed =
                                    drag(start.id) { change ->
                                        if (!currentSaving) {
                                            state.drag(change.positionChange())
                                            change.consume()
                                        }
                                    }
                                if (completed && !currentSaving) state.drop() else state.cancelDrag()
                            }
                        }
                    }
                } finally {
                    if (state.dragging == shortcut) state.cancelDrag()
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text("≡", modifier = Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleLarge) }
}

@Composable
private fun ComposerToolbarPalette(
    state: ComposerToolbarEditState,
    saving: Boolean,
) {
    Text(stringResource(R.string.composer_selected), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.fillMaxWidth().onGloballyPositioned { state.selectedArea = it.boundsInRoot() }) {
        state.draft.shortcuts.forEachIndexed { index, shortcut ->
            key(shortcut) { ComposerShortcutEditRow(shortcut, state, true, index, saving) }
        }
    }
    Text(
        stringResource(R.string.composer_available),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = EditorContentInset),
    )
    ComposerShortcut.entries.filterNot { it in state.draft.shortcuts }.forEach { shortcut ->
        key(shortcut) { ComposerShortcutEditRow(shortcut, state, false, -1, saving) }
    }
    TextButton(onClick = { state.change(ComposerToolbarSettings()) }, enabled = !saving) {
        Text(stringResource(R.string.composer_reset))
    }
}

private fun ComposerToolbarEditState.isInsertionTarget(shortcut: ComposerShortcut): Boolean {
    val target = insertion ?: return false
    val remaining = draft.shortcuts.filterNot { it == dragging }
    return remaining.getOrNull(target) == shortcut ||
        (target == remaining.size && shortcut == remaining.lastOrNull())
}

@Composable
private fun ComposerToolbarAutoScroll(
    state: ComposerToolbarEditState,
    scroll: androidx.compose.foundation.ScrollState,
    viewport: () -> Rect,
) {
    val edge = with(LocalDensity.current) { ComposerShortcutTarget.toPx() }
    val scrollStep = with(LocalDensity.current) { EditorContentInset.toPx() }
    LaunchedEffect(state.dragging) {
        while (state.dragging != null) {
            withFrameNanos { }
            val bounds = viewport()
            val amount =
                when {
                    state.pointer.y < bounds.top + edge -> -scrollStep
                    state.pointer.y > bounds.bottom - edge -> scrollStep
                    else -> 0f
                }
            if (amount != 0f) scroll.scrollBy(amount)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ComposerToolbarLayoutChoices(
    state: ComposerToolbarEditState,
    saving: Boolean,
) {
    Text(stringResource(R.string.composer_layout), style = MaterialTheme.typography.titleSmall)
    FlowRow(Modifier.selectableGroup()) {
        ComposerToolbarLayout.entries.forEach { layout ->
            TextButton(
                onClick = { state.change(state.draft.copy(layout = layout)) },
                enabled = !saving,
                modifier =
                    Modifier.semantics {
                        role = Role.RadioButton
                        selected = state.draft.layout == layout
                    },
            ) {
                Text(
                    stringResource(
                        if (layout ==
                            ComposerToolbarLayout.Automatic
                        ) {
                            R.string.composer_layout_automatic
                        } else {
                            R.string.composer_layout_expanded
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun ComposerShortcutMoveControls(
    shortcut: ComposerShortcut,
    state: ComposerToolbarEditState,
    index: Int,
    saving: Boolean,
) {
    val label = stringResource(shortcut.labelRes)
    val earlier = stringResource(R.string.composer_move_earlier)
    val later = stringResource(R.string.composer_move_later)
    IconButton(
        onClick = { state.move(shortcut, -1) },
        enabled = !saving && index > 0,
    ) { Text("↑", Modifier.clearAndSetSemantics { contentDescription = "$label: $earlier" }) }
    IconButton(
        onClick = { state.move(shortcut, 1) },
        enabled =
            !saving && index < state.draft.shortcuts.lastIndex,
    ) {
        Text(
            "↓",
            Modifier.clearAndSetSemantics {
                contentDescription = "$label: $later"
            },
        )
    }
}

@Composable
private fun composerShortcutAccessibilityActions(
    shortcut: ComposerShortcut,
    state: ComposerToolbarEditState,
    selected: Boolean,
    index: Int,
    saving: Boolean,
): List<CustomAccessibilityAction> {
    val earlier = stringResource(R.string.composer_move_earlier)
    val later = stringResource(R.string.composer_move_later)
    val add = stringResource(R.string.composer_add)
    val remove = stringResource(R.string.composer_remove)
    return if (saving) {
        emptyList()
    } else {
        buildList {
            if (selected) {
                if (index > 0) {
                    add(
                        CustomAccessibilityAction(earlier) {
                            state.move(shortcut, -1)
                            true
                        },
                    )
                }
                if (index < state.draft.shortcuts.lastIndex) {
                    add(
                        CustomAccessibilityAction(later) {
                            state.move(shortcut, 1)
                            true
                        },
                    )
                }
                if (shortcut != ComposerShortcut.More) {
                    add(
                        CustomAccessibilityAction(remove) {
                            state.remove(shortcut)
                            true
                        },
                    )
                }
            } else {
                add(
                    CustomAccessibilityAction(add) {
                        state.add(shortcut)
                        true
                    },
                )
            }
        }
    }
}

@Composable
private fun ComposerShortcutToggleButton(
    shortcut: ComposerShortcut,
    state: ComposerToolbarEditState,
    selected: Boolean,
    saving: Boolean,
) {
    val label = stringResource(shortcut.labelRes)
    val add = stringResource(R.string.composer_add)
    val remove = stringResource(R.string.composer_remove)
    if (!selected || shortcut != ComposerShortcut.More) {
        IconButton(
            onClick = { if (selected) state.remove(shortcut) else state.add(shortcut) },
            enabled = !saving,
        ) {
            Icon(
                painterResource(if (selected) R.drawable.ic_close else R.drawable.ic_add),
                "$label: ${if (selected) remove else add}",
            )
        }
    }
}
