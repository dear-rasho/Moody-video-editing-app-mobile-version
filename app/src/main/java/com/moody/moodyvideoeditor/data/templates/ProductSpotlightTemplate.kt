package com.moody.moodyvideoeditor.data.templates

import com.moody.moodyvideoeditor.utils.TextNodeBlueprint
import com.moody.moodyvideoeditor.utils.TypographyTemplateBlueprint

val ProductSpotlightTemplate = HomeTemplatePreset(
    id = "home_product_spotlight",
    title = "Product Spotlight",
    category = "Product",
    description = "Clean product name with a bold launch label",
    icon = "🛍️",
    accentColor = 0xFFFFC857,
    blueprint = TypographyTemplateBlueprint(
        templateId = "home_product_spotlight",
        label = "Product Spotlight",
        icon = "🛍️",
        description = "Clean product name with a bold launch label",
        category = "Product",
        nodes = listOf(
            TextNodeBlueprint(
                nodeId = "product_label",
                defaultText = "JUST DROPPED",
                relativeY = 68f,
                fontSizePct = 4f,
                fontWeight = "bold",
                color = 0xFFFFC857,
                letterSpacing = 8f,
                animation = "fadeIn",
                durationMs = 5000
            ),
            TextNodeBlueprint(
                nodeId = "product_name",
                defaultText = "YOUR PRODUCT",
                relativeY = 76f,
                fontSizePct = 9f,
                fontFamily = "Arial",
                fontWeight = "bold",
                letterSpacing = 2f,
                animation = "popIn",
                timingOffsetMs = 250,
                durationMs = 4750
            )
        )
    )
)
