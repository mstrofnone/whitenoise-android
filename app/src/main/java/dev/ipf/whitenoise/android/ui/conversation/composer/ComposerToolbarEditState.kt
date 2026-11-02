package dev.ipf.whitenoise.android.ui.conversation.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.ipf.whitenoise.android.state.ComposerShortcut
import dev.ipf.whitenoise.android.state.ComposerToolbarSettings

/** A customization transaction never writes preferences until Done. Drag cancellation restores its input. */
internal class ComposerToolbarEditState(
    initial: ComposerToolbarSettings,
) {
    var draft by mutableStateOf(initial.normalized())
        private set
    var dragging by mutableStateOf<ComposerShortcut?>(null)
        private set
    var insertion by mutableStateOf<Int?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    private var dragSource: ComposerToolbarSettings? = null
    private val selectedBounds = mutableMapOf<ComposerShortcut, Rect>()
    var selectedArea = Rect.Zero
        set(value) {
            field = value
            updateInsertion()
        }

    fun change(settings: ComposerToolbarSettings) {
        cancelDrag()
        draft = settings.normalized()
    }

    fun move(
        shortcut: ComposerShortcut,
        delta: Int,
    ) {
        val index = draft.shortcuts.indexOf(shortcut)
        if (index >= 0) change(draft.insert(shortcut, index + delta))
    }

    fun add(shortcut: ComposerShortcut) = change(draft.insert(shortcut, draft.shortcuts.size))

    fun remove(shortcut: ComposerShortcut) = change(draft.without(shortcut))

    fun record(
        shortcut: ComposerShortcut,
        bounds: Rect,
    ) {
        selectedBounds[shortcut] = bounds
        updateInsertion()
    }

    fun start(
        shortcut: ComposerShortcut,
        position: Offset,
    ) {
        dragSource = draft
        dragging = shortcut
        pointer = position
        updateInsertion()
    }

    fun drag(delta: Offset) {
        if (dragging == null) return
        pointer += delta
        updateInsertion()
    }

    private fun updateInsertion() {
        val id = dragging ?: return
        val remaining = draft.shortcuts.filterNot { it == id }
        insertion =
            if (selectedArea.contains(pointer)) {
                remaining
                    .indexOfFirst { (selectedBounds[it]?.center?.y ?: Float.MAX_VALUE) > pointer.y }
                    .let { if (it < 0) remaining.size else it }
            } else {
                null
            }
    }

    fun drop() {
        val id = dragging
        val index = insertion
        if (id != null && index != null && dragSource == draft) draft = draft.insert(id, index)
        cancelDrag()
    }

    fun cancelDrag() {
        dragging = null
        insertion = null
        dragSource = null
    }
}
