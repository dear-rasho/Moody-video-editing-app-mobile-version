package com.moody.moodyvideoeditor.data.templates

import com.moody.moodyvideoeditor.utils.TypographyTemplateBlueprint

data class HomeTemplatePreset(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val icon: String,
    val accentColor: Long,
    val blueprint: TypographyTemplateBlueprint
)
