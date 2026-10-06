package com.moody.moodyvideoeditor.data.templates

object HomeTemplateCatalog {
    val all: List<HomeTemplatePreset> = listOf(
        CinematicTitleTemplate,
        TravelDiaryTemplate,
        SocialHookTemplate,
        ProductSpotlightTemplate,
        MinimalQuoteTemplate,
        VlogIntroTemplate,
        ShortsTipsTemplate,
        StoryCaptionTemplate
    )

    fun find(id: String): HomeTemplatePreset? = all.firstOrNull { it.id == id }
}
