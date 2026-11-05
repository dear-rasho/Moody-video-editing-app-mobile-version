package com.moody.moodyvideoeditor.utils

import com.moody.moodyvideoeditor.data.AdjustmentData

object AdjustmentDataMerger {

    fun mergePreviewAdjustments(
        clipAdjustment: AdjustmentData,
        layerAdjustment: AdjustmentData?
    ): AdjustmentData {
        if (layerAdjustment == null) return clipAdjustment
        return clipAdjustment.copy(
            sharpen = clipAdjustment.sharpen + layerAdjustment.sharpen,
            noise = clipAdjustment.noise + layerAdjustment.noise,
            vignette = clipAdjustment.vignette + layerAdjustment.vignette,
            reds = clipAdjustment.reds + layerAdjustment.reds,
            oranges = clipAdjustment.oranges + layerAdjustment.oranges,
            yellows = clipAdjustment.yellows + layerAdjustment.yellows,
            greens = clipAdjustment.greens + layerAdjustment.greens,
            cyans = clipAdjustment.cyans + layerAdjustment.cyans,
            blues = clipAdjustment.blues + layerAdjustment.blues,
            purples = clipAdjustment.purples + layerAdjustment.purples,
            magentas = clipAdjustment.magentas + layerAdjustment.magentas,
            skinTones = clipAdjustment.skinTones + layerAdjustment.skinTones
        )
    }
}
