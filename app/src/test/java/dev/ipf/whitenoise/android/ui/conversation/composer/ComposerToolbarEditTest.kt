package dev.ipf.whitenoise.android.ui.conversation.composer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.ipf.whitenoise.android.state.ComposerShortcut
import dev.ipf.whitenoise.android.state.ComposerToolbarLayout
import dev.ipf.whitenoise.android.state.ComposerToolbarSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerToolbarEditTest {
    @Test
    fun editTransactionChangesOnlyItsWorkingCopyAndCannotRemoveMore() {
        val original = ComposerToolbarSettings()
        val edit = ComposerToolbarEditState(original)
        edit.remove(ComposerShortcut.Paste)
        edit.add(ComposerShortcut.Camera)
        edit.move(ComposerShortcut.Camera, -2)
        edit.remove(ComposerShortcut.More)
        assertEquals(ComposerToolbarSettings(), original)
        assertFalse(ComposerShortcut.Paste in edit.draft.shortcuts)
        assertTrue(ComposerShortcut.Camera in edit.draft.shortcuts)
        assertTrue(ComposerShortcut.More in edit.draft.shortcuts)
        edit.change(ComposerToolbarSettings())
        assertEquals(original, edit.draft)
    }

    @Test
    fun paletteDropInsertsAtThePreviewedPositionAndInterruptedDragDoesNotApply() {
        val original = ComposerToolbarSettings(listOf(ComposerShortcut.More, ComposerShortcut.Paste))
        val edit = ComposerToolbarEditState(original)
        edit.selectedArea = Rect(0f, 0f, 300f, 200f)
        edit.record(ComposerShortcut.More, Rect(0f, 0f, 300f, 100f))
        edit.record(ComposerShortcut.Paste, Rect(0f, 100f, 300f, 200f))
        edit.start(ComposerShortcut.Camera, Offset(20f, 300f))
        edit.drag(Offset(0f, -200f))
        assertEquals(1, edit.insertion)
        edit.drop()
        assertEquals(
            listOf(ComposerShortcut.More, ComposerShortcut.Camera, ComposerShortcut.Paste),
            edit.draft.shortcuts,
        )
        val committedDraft = edit.draft
        edit.start(ComposerShortcut.Paste, Offset(20f, 180f))
        edit.drag(Offset(0f, -170f))
        edit.cancelDrag()
        assertEquals(committedDraft, edit.draft)
        assertNull(edit.dragging)
    }

    @Test
    fun outsideDropAndDraftReplacementRetirePendingInsertion() {
        val edit = ComposerToolbarEditState(ComposerToolbarSettings())
        edit.selectedArea = Rect(0f, 0f, 300f, 200f)
        edit.start(ComposerShortcut.Camera, Offset(30f, 300f))
        edit.drop()
        assertFalse(ComposerShortcut.Camera in edit.draft.shortcuts)
        edit.start(ComposerShortcut.Camera, Offset(20f, 20f))
        edit.change(edit.draft.copy(layout = ComposerToolbarLayout.AlwaysExpanded))
        edit.drop()
        assertFalse(ComposerShortcut.Camera in edit.draft.shortcuts)
    }

    @Test
    fun scrollingGeometryUpdatesTheInsertionForAStationaryHeldPointer() {
        val edit =
            ComposerToolbarEditState(ComposerToolbarSettings(listOf(ComposerShortcut.More, ComposerShortcut.Paste)))
        edit.selectedArea = Rect(0f, 0f, 300f, 200f)
        edit.record(ComposerShortcut.More, Rect(0f, 0f, 300f, 100f))
        edit.record(ComposerShortcut.Paste, Rect(0f, 100f, 300f, 200f))
        edit.start(ComposerShortcut.Camera, Offset(20f, 100f))
        assertEquals(1, edit.insertion)
        edit.record(ComposerShortcut.More, Rect(0f, -100f, 300f, 0f))
        edit.record(ComposerShortcut.Paste, Rect(0f, 0f, 300f, 100f))
        assertEquals(2, edit.insertion)
        edit.drop()
        assertEquals(
            listOf(ComposerShortcut.More, ComposerShortcut.Paste, ComposerShortcut.Camera),
            edit.draft.shortcuts,
        )
    }

    @Test
    fun droppingAfterTheLastVisibleRowDoesNotJumpPastOffscreenSelectedActions() {
        val settings = ComposerToolbarSettings(ComposerShortcut.entries - ComposerShortcut.Camera)
        val edit = ComposerToolbarEditState(settings)
        edit.selectedArea = Rect(0f, 0f, 300f, 100f)
        settings.shortcuts.forEachIndexed { index, action ->
            val top = index * 50f - 250f
            edit.record(action, Rect(0f, top, 300f, top + 50f))
        }
        edit.start(ComposerShortcut.Camera, Offset(20f, 90f))
        assertEquals(7, edit.insertion)
        edit.drop()
        assertEquals(ComposerShortcut.Camera, edit.draft.shortcuts[7])
        assertTrue(edit.draft.shortcuts.last() != ComposerShortcut.Camera)
    }

    @Test
    fun fitThresholdUsesTheHypotheticalCompactWidthAndMandatoryReservations() {
        assertTrue(composerToolbarRequiresSeparateRow(295f, 3, 48f, 96f, ComposerToolbarLayout.Automatic))
        assertFalse(composerToolbarRequiresSeparateRow(296f, 3, 48f, 96f, ComposerToolbarLayout.Automatic))
        assertFalse(composerToolbarRequiresSeparateRow(297f, 3, 48f, 96f, ComposerToolbarLayout.Automatic))
        assertTrue(composerToolbarRequiresSeparateRow(1000f, 0, 48f, 96f, ComposerToolbarLayout.AlwaysExpanded))
        assertEquals(3, composerToolbarRowCount(13, 240f))
        assertEquals(13, composerToolbarRowCount(13, 47f))
    }
}
