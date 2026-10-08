package me.bmax.apatch.ui.theme.tokens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FolkMotionTest {

    @Test
    fun pressDepthIsPinned() {
        assertEquals(0.97f, FolkMotion.PressedScale, 0.0001f)
    }

    @Test
    fun animationSpecsAreConfigured() {
        assertNotNull(FolkMotion.PressScale)
        assertNotNull(FolkMotion.PressDown)
        assertNotNull(FolkMotion.ScrollIntoView)
        assertNotNull(FolkMotion.FadeInOut)
        assertNotNull(FolkMotion.IconRotation)
        assertNotNull(FolkMotion.smoothSpring<Float>())
    }
}
