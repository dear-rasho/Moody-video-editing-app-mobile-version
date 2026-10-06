package com.moody.moodyvideoeditor.data.templates

import com.moody.moodyvideoeditor.utils.TextNodeBlueprint
import com.moody.moodyvideoeditor.utils.TypographyTemplateBlueprint

val VlogIntroTemplate = HomeTemplatePreset(
    id = "home_vlog_intro",
    title = "Vlog Intro",
    category = "Vlog",
    description = "Friendly creator intro with a bright accent",
    icon = "🎥",
    accentColor = 0xFF70D6FF,
    blueprint = TypographyTemplateBlueprint(
        templateId = "home_vlog_intro",
        label = "Vlog Intro",
        icon = "🎥",
        description = "Friendly creator intro with a bright accent",
        category = "Vlog",
        nodes = listOf(
            TextNodeBlueprint(
                nodeId = "vlog_day",
                defaultText = "A DAY WITH",
                relativeY = 40f,
                fontSizePct = 6f,
                fontWeight = "bold",
                letterSpacing = 5f,
                animation = "fadeIn",
                durationMs = 5500
            ),
            TextNodeBlueprint(
                nodeId = "vlog_creator",
                defaultText = "YOUR NAME",
                relativeY = 49f,
                fontSizePct = 12f,
                fontFamily = "Impact",
                fontWeight = "bold",
                color = 0xFF70D6FF,
                animation = "popIn",
                timingOffsetMs = 300,
                durationMs = 5200
            )
        )
    )
)
