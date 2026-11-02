package dev.ipf.whitenoise.android.state

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ComposerToolbarPreferencesTest {
    @Test
    fun roundTripRetainsOrderAndIntentionalEmptyOptionalSelection() {
        val settings =
            ComposerToolbarSettings(
                listOf(ComposerShortcut.Camera, ComposerShortcut.More, ComposerShortcut.Gallery),
                ComposerToolbarLayout.AlwaysExpanded,
            )
        assertEquals(settings, ComposerToolbarSettings.decode(settings.encode()))
        assertEquals(
            listOf(ComposerShortcut.More),
            ComposerToolbarSettings
                .decode(
                    """{"version":1,"shortcuts":[],"layout":"automatic"}""",
                ).shortcuts,
        )
    }

    @Test
    fun unknownAndRepeatedIdsKeepSurvivingOrderWithoutAddingOptionalActions() {
        val settings =
            ComposerToolbarSettings.decode(
                """{"version":1,"shortcuts":["camera","future","camera","paste"],"layout":"future"}""",
            )
        assertEquals(listOf(ComposerShortcut.More, ComposerShortcut.Camera, ComposerShortcut.Paste), settings.shortcuts)
        assertEquals(ComposerToolbarLayout.Automatic, settings.layout)
    }

    @Test
    fun malformedVersionsAndPrimitiveTypesResetDeterministically() {
        listOf(
            "bad",
            "[]",
            """{"version":"1","shortcuts":[]}""",
            """{"version":2,"shortcuts":[]}""",
            """{"version":1,"shortcuts":[false]}""",
            """{"version":1,"shortcuts":"camera"}""",
        ).forEach { encoded ->
            assertEquals(ComposerToolbarSettings(), ComposerToolbarSettings.decode(encoded))
        }
    }

    @Test
    fun moreCannotBeRemovedAndMovingItDoesNotChangeOtherActions() {
        val settings =
            ComposerToolbarSettings(listOf(ComposerShortcut.More, ComposerShortcut.Camera, ComposerShortcut.Paste))
        assertEquals(settings, settings.without(ComposerShortcut.More))
        assertEquals(
            listOf(ComposerShortcut.Camera, ComposerShortcut.Paste, ComposerShortcut.More),
            settings.insert(ComposerShortcut.More, 2).shortcuts,
        )
    }

    @Test
    fun completedSaveSurvivesANewPreferenceOwnerAndBusySaveCannotReplaceIt() =
        runTest {
            val prefs = preferences("completed")
            val lock = Mutex(locked = true)
            val owner = ComposerToolbarPreference(prefs, this, lock)
            val changed = ComposerToolbarSettings(listOf(ComposerShortcut.More, ComposerShortcut.Camera))
            val result = CompletableDeferred<Boolean>()
            owner.save(changed) { result.complete(it) }
            assertTrue(owner.saving)
            var busy: Boolean? = null
            owner.save(ComposerToolbarSettings()) { busy = it }
            assertEquals(false, busy)
            assertEquals(ComposerToolbarSettings(), owner.value)
            lock.unlock()
            assertTrue(result.await())
            assertEquals(changed, owner.value)
            assertEquals(changed, ComposerToolbarPreference(prefs, this, Mutex()).value)
        }

    @Test
    fun failedDiskCommitRestoresTheInMemoryPreferenceAndReportsFailure() =
        runTest {
            val actual = preferences("failed")
            var commits = 0
            val failing =
                object : SharedPreferences by actual {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = actual.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(
                                key: String?,
                                value: String?,
                            ): SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }

                            override fun commit(): Boolean {
                                val committed = editor.commit()
                                commits++
                                return committed && commits != 1
                            }
                        }
                    }
                }
            val owner = ComposerToolbarPreference(failing, this, Mutex())
            val result = CompletableDeferred<Boolean>()
            owner.save(
                ComposerToolbarSettings(listOf(ComposerShortcut.More, ComposerShortcut.Camera)),
            ) { result.complete(it) }
            assertFalse(result.await())
            assertEquals(ComposerToolbarSettings(), owner.value)
            assertEquals(owner.value, ComposerToolbarPreference(actual, this, Mutex()).value)
        }

    private fun preferences(name: String): SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("composer-toolbar-test-$name", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
}
