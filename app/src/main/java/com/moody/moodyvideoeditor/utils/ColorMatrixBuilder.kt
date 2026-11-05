package com.moody.moodyvideoeditor.utils

import android.graphics.ColorMatrix
import com.moody.moodyvideoeditor.data.AdjustmentData

object ColorMatrixBuilder {

    fun build(adj: AdjustmentData): ColorMatrix =
        AdjustmentNative.buildColorMatrix(adj)

    fun hasRealTimeAdjustments(adj: AdjustmentData): Boolean =
        adj.brightness != 0f || adj.contrast != 0f || adj.exposure != 0f ||
                adj.whites != 0f || adj.blacks != 0f || adj.shadows != 0f ||
                adj.highlights != 0f || adj.saturation != 0f || adj.vibrance != 0f ||
                adj.clarity != 0f || adj.temperature != 0f || adj.tint != 0f ||
                adj.reds != 0f || adj.oranges != 0f || adj.yellows != 0f ||
                adj.greens != 0f || adj.cyans != 0f || adj.blues != 0f ||
                adj.purples != 0f || adj.magentas != 0f || adj.skinTones != 0f
}
