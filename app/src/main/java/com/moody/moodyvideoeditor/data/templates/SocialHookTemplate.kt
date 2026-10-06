package com.moody.moodyvideoeditor.data.templates

import com.moody.moodyvideoeditor.utils.TextNodeBlueprint
import com.moody.moodyvideoeditor.utils.TypographyTemplateBlueprint

val SocialHookTemplate = HomeTemplatePreset(
    id = "home_social_hook",
    title = "Social Hook",
    category = "Social",
    description = "High-energy hook text for scroll-stopping clips",
    icon = "📱",
    accentColor = 0xFFFF4F8B,
    blueprint = TypographyTemplateBlueprint(
        templateId = "home_social_hook",
        label = "Social Hook",
        icon = "📱",
        description = "High-energy hook text for scroll-stopping clips",
        category = "Social",
        nodes = listOf(
            TextNodeBlueprint(
                nodeId = "social_hook",
                defaultText = "WAIT FOR IT",
                relativeY = 42f,
                fontSizePct = 13f,
                fontFamily = "Impact",
                fontWeight = "bold",
                color = 0xFFFF4F8B,
                strokeEnabled = true,
                strokeWidth = 3f,
                letterSpacing = 2f,
                animation = "popIn",
                durationMs = 3500
            ),
            TextNodeBlueprint(
                nodeId = "social_part",
                defaultText = "PART 01",
                relativeY = 53f,
                fontSizePct = 5f,
                fontWeight = "bold",
                letterSpacing = 6f,
                animation = "fadeIn",
                timingOffsetMs = 250,
                durationMs = 3250
            )
        )
    )
)
