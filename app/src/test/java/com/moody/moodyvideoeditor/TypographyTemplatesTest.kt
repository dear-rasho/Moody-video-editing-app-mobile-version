package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.utils.TypographyTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypographyTemplatesTest {
    @Test
    fun everyTypographyTemplateIsRegisteredAndUsable() {
        val templates = TypographyTemplates.ALL

        assertTrue(templates.isNotEmpty())
        assertEquals(
            templates.size,
            templates.map { it.templateId }.distinct().size
        )
        templates.forEach { template ->
            assertEquals(template, TypographyTemplates.find(template.templateId))
            assertTrue(template.nodes.isNotEmpty())
        }
    }
}
