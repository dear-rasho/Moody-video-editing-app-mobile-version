package com.moody.moodyvideoeditor.data.templates

import com.moody.moodyvideoeditor.utils.TextNodeBlueprint
import com.moody.moodyvideoeditor.utils.TypographyTemplateBlueprint

val CinematicTitleTemplate = HomeTemplatePreset(
    id = "home_cinematic_title",
    title = "Cinematic Title",
    category = "Cinematic",
    description = "Elegant opening titles for film-like edits",
    icon = "🎬",
    accentColor = 0xFFB99A6B,
    blueprint = TypographyTemplateBlueprint(
        templateId = "home_cinematic_title",
        label = "Cinematic Title",
        icon = "🎬",
        description = "Elegant opening titles for film-like edits",
        category = "Cinematic",
        nodes = listOf(
            TextNodeBlueprint(
                nodeId = "cine_title",
                defaultText = "THE JOURNEY",
                relativeY = 47f,
                fontSizePct = 10f,
                fontFamily = "Georgia",
                fontStyle = "italic",
                letterSpacing = 10f,
                animation = "fadeIn",
                animationDuration = 1.2f,
                durationMs = 5000
            ),
            TextNodeBlueprint(
                nodeId = "cine_subtitle",
                defaultText = "A FILM BY YOU",
                relativeY = 56f,
                fontSizePct = 4f,
                fontFamily = "Georgia",
                color = 0xFFD5D0C7,
                letterSpacing = 8f,
                animation = "fadeIn",
                animationDuration = 1.5f,
                timingOffsetMs = 500,
                durationMs = 4500
            )
        )
    )
)
