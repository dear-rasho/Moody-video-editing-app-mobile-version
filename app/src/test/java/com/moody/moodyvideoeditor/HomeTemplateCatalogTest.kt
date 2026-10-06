package com.moody.moodyvideoeditor

import com.moody.moodyvideoeditor.data.templates.HomeTemplateCatalog
import com.moody.moodyvideoeditor.utils.TypographyTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTemplateCatalogTest {
    @Test
    fun everyHomeTemplateIsRegisteredAndUsable() {
        val templates = HomeTemplateCatalog.all

        assertTrue(templates.isNotEmpty())
        assertEquals(
            templates.size,
            templates.map { it.id }.distinct().size
        )
        templates.forEach { template ->
            assertEquals(template.id, template.blueprint.templateId)
            assertNotNull(TypographyTemplates.find(template.id))
            assertTrue(template.blueprint.nodes.isNotEmpty())
        }
    }
}
