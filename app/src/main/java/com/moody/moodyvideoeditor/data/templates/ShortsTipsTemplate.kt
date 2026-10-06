package com.moody.moodyvideoeditor.data.templates

import com.moody.moodyvideoeditor.utils.TextNodeBlueprint
import com.moody.moodyvideoeditor.utils.TypographyTemplateBlueprint

val ShortsTipsTemplate = HomeTemplatePreset(
    id = "home_shorts_tips",
    title = "Quick Tips",
    category = "Shorts",
    description = "Numbered tip title made for vertical short videos",
    icon = "⚡",
    accentColor = 0xFFB388FF,
    blueprint = TypographyTemplateBlueprint(
        templateId = "home_shorts_tips",
        label = "Quick Tips",
        icon = "⚡",
        description = "Numbered tip title made for vertical short videos",
        category = "Shorts",
        nodes = listOf(
            TextNodeBlueprint(
                nodeId = "shorts_number",
                defaultText = "03 TIPS",
                relativeY = 43f,
                fontSizePct = 14f,
                fontFamily = "Impact",
                fontWeight = "bold",
                color = 0xFFB388FF,
                animation = "popIn",
                durationMs = 4000
            ),
            TextNodeBlueprint(
                nodeId = "shorts_subtitle",
                defaultText = "YOU NEED TO KNOW",
                relativeY = 54f,
                fontSizePct = 5f,
                fontWeight = "bold",
                letterSpacing = 4f,
                animation = "fadeIn",
                timingOffsetMs = 250,
                durationMs = 3750
            )
        )
    )
)
