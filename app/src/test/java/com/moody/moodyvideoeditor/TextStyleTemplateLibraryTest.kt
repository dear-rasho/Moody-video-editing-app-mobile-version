package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.TextState
import com.moody.moodyvideoeditor.utils.FontLibrary
import com.moody.moodyvideoeditor.utils.TextStyleTemplateLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextStyleTemplateLibraryTest {

    @Test
    fun templatesApplyStyleAndAnimationWithoutReplacingTextOrPlacement() {
        val original = TextState(
            content = "My title",
            positionX = 28f,
            positionY = 72f,
            scale = 125f,
            templateId = "old-template",
            textureUri = "content://old-texture"
        )

        val applied = TextStyleTemplateLibrary.applyTo(original, "glass-frost")

        assertEquals("My title", applied.content)
        assertEquals(28f, applied.positionX)
        assertEquals(72f, applied.positionY)
        assertEquals(125f, applied.scale)
        assertEquals("glass-frost", applied.templateId)
        assertEquals("fadeUp", applied.animation)
        assertTrue(applied.gradientEnabled)
        assertTrue(applied.strokeEnabled)
        assertTrue(applied.glowEnabled)
        assertNull(applied.textureUri)
    }

    @Test
    fun threeDimensionalAndMotionStylesUseExistingAnimations() {
        val animations = TextStyleTemplateLibrary.templates.map { it.style.animation }

        assertTrue(animations.contains("flyDiagonalTL"))
        assertTrue(animations.contains("jellyBounce"))
        assertTrue(animations.contains("flip3DX"))
        assertTrue(animations.contains("yAxisFlip"))
        assertFalse(TextStyleTemplateLibrary.templates.any { it.id.isBlank() })
    }

    @Test
    fun addedTitleFontsAreUniqueAndAvailableInFontMetadata() {
        val additions = FontLibrary.TITLE_FONT_ADDITIONS
        val availableFonts = FontLibrary.FONT_METADATA.map { it.name }.toSet()

        assertEquals(34, additions.size)
        assertEquals(additions.size, additions.toSet().size)
        assertTrue(additions.all { it in availableFonts })
        assertTrue(FontLibrary.FONT_CATEGORIES.getValue("titles").containsAll(additions))
    }
}
