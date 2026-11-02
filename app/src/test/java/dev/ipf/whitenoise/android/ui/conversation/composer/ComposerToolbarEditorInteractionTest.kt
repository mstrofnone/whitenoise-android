package dev.ipf.whitenoise.android.ui.conversation.composer

import android.window.OnBackInvokedCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.ipf.whitenoise.android.state.ComposerShortcut
import dev.ipf.whitenoise.android.state.ComposerToolbarSettings
import dev.ipf.whitenoise.android.ui.theme.WhiteNoiseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-w420dp-h1000dp-mdpi")
class ComposerToolbarEditorInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun draggingPaletteIntoSelectedPreviewsOneDraftAndDoneCommitsOnce() {
        val original = ComposerToolbarSettings(listOf(ComposerShortcut.More))
        val edit = ComposerToolbarEditState(original)
        val saved = mutableListOf<ComposerToolbarSettings>()
        render(edit, { config, complete ->
            saved += config
            complete(true)
        }, {})
        val source = composeRule.onNodeWithTag("composer.customize.drag.camera")
        val target = composeRule.onNodeWithTag("composer.customize.selected.more")
        val from = source.fetchSemanticsNode().boundsInRoot.center
        val targetBounds = target.fetchSemanticsNode().boundsInRoot
        val to = Offset(targetBounds.center.x, targetBounds.top + 8f)
        source.performTouchInput {
            down(center)
            moveBy(to - from)
            up()
        }
        assertEquals(emptyList<ComposerToolbarSettings>(), saved)
        assertEquals(listOf(ComposerShortcut.Camera, ComposerShortcut.More), edit.draft.shortcuts)
        composeRule.onNodeWithTag("composer.customize.done").performClick()
        assertEquals(listOf(edit.draft), saved)
    }

    @Test
    fun preImeBackCancelsWithoutSavingAndCancelledDragCannotInsert() {
        val edit = ComposerToolbarEditState(ComposerToolbarSettings(listOf(ComposerShortcut.More)))
        var back: OnBackInvokedCallback? = null
        var cancels = 0
        var saves = 0
        composeRule.setContent {
            WhiteNoiseTheme {
                Box(Modifier.width(420.dp).height(900.dp)) {
                    ComposerToolbarEditor(
                        edit,
                        false,
                        { _, _ -> saves++ },
                        { cancels++ },
                        overlayBackRegistrar =
                            ComposerOverlayBackRegistrar { _, callback ->
                                back = callback;
                                {}
                            },
                    )
                }
            }
        }
        val source = composeRule.onNodeWithTag("composer.customize.drag.camera")
        source.performTouchInput {
            down(center)
            moveBy(Offset(0f, -100f))
            cancel()
        }
        assertFalse(ComposerShortcut.Camera in edit.draft.shortcuts)
        composeRule.runOnIdle {
            assertNotNull(back)
            back!!.onBackInvoked()
        }
        assertEquals(1, cancels)
        assertEquals(0, saves)
    }

    @Test
    fun addAndResetRemainLocalUntilDoneAndCancelNeverSaves() {
        val edit = ComposerToolbarEditState(ComposerToolbarSettings(listOf(ComposerShortcut.More)))
        var saves = 0
        var cancels = 0
        render(edit, { _, _ -> saves++ }, { cancels++ })
        composeRule.onNodeWithText("Reset to default").performScrollTo().performClick()
        assertEquals(ComposerToolbarSettings(), edit.draft)
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancels)
        assertEquals(0, saves)
    }

    @Test
    fun previousSessionSaveCompletionCannotCloseTheReplacementEditor() {
        val current = mutableStateOf(ComposerToolbarEditState(ComposerToolbarSettings()))
        val pending = mutableListOf<(Boolean) -> Unit>()
        var cancellations = 0
        composeRule.setContent {
            WhiteNoiseTheme {
                Box(Modifier.width(420.dp).height(900.dp)) {
                    ComposerToolbarEditor(
                        current.value,
                        false,
                        { _, complete -> pending += complete },
                        { cancellations++ },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("composer.customize.done").performClick()
        composeRule.runOnIdle {
            current.value =
                ComposerToolbarEditState(ComposerToolbarSettings(listOf(ComposerShortcut.More)))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { pending.single()(true) }
        assertEquals(0, cancellations)
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancellations)
    }

    @Test
    fun accessibleMoveKeepsTheSameShortcutNodeAcrossRepeatedReorders() {
        val edit =
            ComposerToolbarEditState(
                ComposerToolbarSettings(listOf(ComposerShortcut.More, ComposerShortcut.Emoji, ComposerShortcut.Camera)),
            )
        render(edit, { _, _ -> }, {})
        val tag = "composer.customize.label.camera"
        val originalNode = composeRule.onNodeWithTag(tag).fetchSemanticsNode().id
        repeat(2) {
            val earlier =
                composeRule
                    .onNodeWithTag(tag)
                    .fetchSemanticsNode()
                    .config[SemanticsActions.CustomActions]
                    .single { it.label == "Move earlier" }
            composeRule.runOnIdle { assertTrue(earlier.action()) }
            composeRule.waitForIdle()
            assertEquals(originalNode, composeRule.onNodeWithTag(tag).fetchSemanticsNode().id)
        }
        assertEquals(
            listOf(ComposerShortcut.Camera, ComposerShortcut.More, ComposerShortcut.Emoji),
            edit.draft.shortcuts,
        )
    }

    @Test
    fun addingOneAvailableShortcutDoesNotRebindTheNextAvailableNode() {
        val edit = ComposerToolbarEditState(ComposerToolbarSettings(listOf(ComposerShortcut.More)))
        render(edit, { _, _ -> }, {})
        val remainingTag = "composer.customize.label.emoji"
        val originalNode = composeRule.onNodeWithTag(remainingTag).fetchSemanticsNode().id
        val add =
            composeRule
                .onNodeWithTag("composer.customize.label.sending_identity")
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == "Add shortcut" }
        composeRule.runOnIdle { assertTrue(add.action()) }
        composeRule.waitForIdle()
        assertEquals(originalNode, composeRule.onNodeWithTag(remainingTag).fetchSemanticsNode().id)
        assertEquals(listOf(ComposerShortcut.More, ComposerShortcut.SendingIdentity), edit.draft.shortcuts)
    }

    @Test
    fun layoutOptionsExposeRadioRolesAndRemainActivatable() {
        val edit = ComposerToolbarEditState(ComposerToolbarSettings(listOf(ComposerShortcut.More)))
        render(edit, { _, _ -> }, {})
        val expanded = composeRule.onNodeWithText("Always expanded")
        assertEquals(Role.RadioButton, expanded.fetchSemanticsNode().config[SemanticsProperties.Role])
        expanded.performClick()
        assertTrue(expanded.fetchSemanticsNode().config[SemanticsProperties.Selected])
        assertFalse(composeRule.onNodeWithText("Automatic").fetchSemanticsNode().config[SemanticsProperties.Selected])
    }

    @Test
    fun stackedMoveButtonKeepsItsShortcutIdentityAndTouchTarget() {
        val edit =
            ComposerToolbarEditState(
                ComposerToolbarSettings(listOf(ComposerShortcut.More, ComposerShortcut.Camera)),
            )
        render(edit, { _, _ -> }, {}, width = 240, fontScale = 2f)
        val label = composeRule.onNodeWithTag("composer.customize.label.camera")
        val originalNode = label.fetchSemanticsNode().id
        val earlier = composeRule.onNodeWithContentDescription("Camera: Move earlier").performScrollTo()
        val labelBounds = label.getUnclippedBoundsInRoot()
        val buttonBounds = earlier.getUnclippedBoundsInRoot()
        assertTrue(buttonBounds.top >= labelBounds.bottom)
        earlier.assertTouchWidthIsEqualTo(48.dp)
        earlier.assertTouchHeightIsEqualTo(48.dp)

        earlier.performClick()

        assertEquals(listOf(ComposerShortcut.Camera, ComposerShortcut.More), edit.draft.shortcuts)
        assertEquals(originalNode, label.fetchSemanticsNode().id)
    }

    private fun render(
        edit: ComposerToolbarEditState,
        save: (ComposerToolbarSettings, (Boolean) -> Unit) -> Unit,
        cancel: () -> Unit,
        width: Int = 420,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            WhiteNoiseTheme(fontScale = fontScale) {
                Box(Modifier.width(width.dp).height(900.dp)) { ComposerToolbarEditor(edit, false, save, cancel) }
            }
        }
    }
}
