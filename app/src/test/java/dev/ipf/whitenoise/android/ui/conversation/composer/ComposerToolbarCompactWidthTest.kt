package dev.ipf.whitenoise.android.ui.conversation.composer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposerToolbarCompactWidthTest {
    @Test
    fun wideCompressedViewportsKeepAllDefaultActionsReachable() {
        val toolbar = composerToolbarCompactWidth(780f, 3, 52f, 96f)
        assertEquals(144f, toolbar, 0f)
        assertTrue(toolbar + 52f + 96f + 8f <= 780f)
    }

    @Test
    fun narrowCompressedViewportsUseSpareWidthAndRetainTheLargeTextMinimum() {
        val toolbar = composerToolbarCompactWidth(360f, 13, 52f, 144f)
        assertEquals(144f, toolbar, 0f)
        assertTrue(toolbar + 52f + 144f + 8f <= 360f)
    }

    @Test
    fun unboundedInitialMeasurementRetainsEveryConfiguredTarget() {
        assertEquals(624f, composerToolbarCompactWidth(Float.POSITIVE_INFINITY, 13, 48f, 96f), 0f)
    }
}
