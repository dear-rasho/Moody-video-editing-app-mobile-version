package com.moody.moodyvideoeditor.data.templates

import com.moody.moodyvideoeditor.utils.TextNodeBlueprint
import com.moody.moodyvideoeditor.utils.TypographyTemplateBlueprint

val TravelDiaryTemplate = HomeTemplatePreset(
    id = "home_travel_diary",
    title = "Travel Diary",
    category = "Travel",
    description = "A warm destination title and location tag",
    icon = "✈️",
    accentColor = 0xFF47C7A8,
    blueprint = TypographyTemplateBlueprint(
        templateId = "home_travel_diary",
        label = "Travel Diary",
        icon = "✈️",
        description = "A warm destination title and location tag",
        category = "Travel",
        nodes = listOf(
            TextNodeBlueprint(
                nodeId = "travel_title",
                defaultText = "WANDER MORE",
                relativeY = 72f,
                fontSizePct = 9f,
                fontFamily = "Arial",
                fontWeight = "bold",
                letterSpacing = 4f,
                animation = "fadeIn",
                durationMs = 6000
            ),
            TextNodeBlueprint(
                nodeId = "travel_location",
                defaultText = "YOUR NEXT ADVENTURE",
                relativeY = 79f,
                fontSizePct = 4f,
                color = 0xFF8DE3C9,
                letterSpacing = 5f,
                animation = "fadeIn",
                timingOffsetMs = 350,
                durationMs = 5650
            )
        )
    )
)
