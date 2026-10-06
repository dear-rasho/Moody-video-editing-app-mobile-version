package com.moody.moodyvideoeditor.data.templates

import com.moody.moodyvideoeditor.utils.TextNodeBlueprint
import com.moody.moodyvideoeditor.utils.TypographyTemplateBlueprint

val StoryCaptionTemplate = HomeTemplatePreset(
    id = "home_story_caption",
    title = "Story Caption",
    category = "Story",
    description = "A soft quote and author line for personal stories",
    icon = "💬",
    accentColor = 0xFFFF9F7A,
    blueprint = TypographyTemplateBlueprint(
        templateId = "home_story_caption",
        label = "Story Caption",
        icon = "💬",
        description = "A soft quote and author line for personal stories",
        category = "Story",
        nodes = listOf(
            TextNodeBlueprint(
                nodeId = "story_quote",
                defaultText = "COLLECT\nTHE MOMENTS",
                relativeY = 47f,
                fontSizePct = 9f,
                fontFamily = "Georgia",
                fontStyle = "italic",
                color = 0xFFFFE5D9,
                lineHeight = 1.2f,
                maxWidth = 80f,
                animation = "fadeIn",
                durationMs = 5000
            ),
            TextNodeBlueprint(
                nodeId = "story_author",
                defaultText = "YOUR STORY",
                relativeY = 59f,
                fontSizePct = 4f,
                color = 0xFFFF9F7A,
                letterSpacing = 5f,
                animation = "fadeIn",
                timingOffsetMs = 450,
                durationMs = 4550
            )
        )
    )
)
