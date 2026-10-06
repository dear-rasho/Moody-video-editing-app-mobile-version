package com.moody.moodyvideoeditor.data.templates

import com.moody.moodyvideoeditor.utils.TextNodeBlueprint
import com.moody.moodyvideoeditor.utils.TypographyTemplateBlueprint

val MinimalQuoteTemplate = HomeTemplatePreset(
    id = "home_minimal_quote",
    title = "Minimal Quote",
    category = "Minimal",
    description = "Quiet, centered typography with a simple signature",
    icon = "◻️",
    accentColor = 0xFFD6D6D6,
    blueprint = TypographyTemplateBlueprint(
        templateId = "home_minimal_quote",
        label = "Minimal Quote",
        icon = "◻️",
        description = "Quiet, centered typography with a simple signature",
        category = "Minimal",
        nodes = listOf(
            TextNodeBlueprint(
                nodeId = "minimal_quote",
                defaultText = "MAKE IT\nMATTER",
                relativeY = 48f,
                fontSizePct = 10f,
                fontFamily = "Georgia",
                lineHeight = 1.15f,
                maxWidth = 75f,
                animation = "fadeIn",
                durationMs = 5000
            ),
            TextNodeBlueprint(
                nodeId = "minimal_signature",
                defaultText = "A MOMENT TO REMEMBER",
                relativeY = 58f,
                fontSizePct = 3.5f,
                color = 0xFFCCCCCC,
                letterSpacing = 4f,
                animation = "fadeIn",
                timingOffsetMs = 400,
                durationMs = 4600
            )
        )
    )
)
