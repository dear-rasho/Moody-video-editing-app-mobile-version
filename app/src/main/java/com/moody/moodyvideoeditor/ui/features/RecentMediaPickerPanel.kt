package com.moody.moodyvideoeditor.ui.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moody.moodyvideoeditor.data.RecentMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecentMediaPickerPanel(
    recentMedia: List<RecentMediaItem>,
    onRecentSelected: (RecentMediaItem) -> Unit,
    onOpenStorage: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151515),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF666666))
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        ) {
            Text(
                "Add Media",
                color = Color.White,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF151515),
                contentColor = Color(0xFF60EFFF),
                divider = {}
            ) {
                listOf("Recent", "Storage").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontSize = 14.sp) }
                    )
                }
            }

            if (selectedTab == 0) {
                if (recentMedia.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No recent media found.",
                            color = Color(0xFF999999),
                            fontSize = 13.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        recentMedia.forEach { item ->
                            RecentMediaCard(item, onClick = { onRecentSelected(item) })
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Browse your device for videos and images.",
                        color = Color(0xFFAAAAAA),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Choose from Storage",
                        color = Color(0xFF101010),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF60EFFF))
                            .clickable(onClick = onOpenStorage)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentMediaCard(
    item: RecentMediaItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        bitmap = withContext(Dispatchers.IO) {
            loadThumbnail(context, item.uri)
        }
    }
    Column(
        modifier = Modifier
            .size(width = 104.dp, height = 132.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF222222))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF303030)),
            contentAlignment = Alignment.Center
        ) {
            val thumbnail = bitmap
            if (thumbnail != null && !thumbnail.isRecycled) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Text(
                    if (item.uri.toString().contains("video", ignoreCase = true)) "▶" else "▧",
                    color = Color(0xFFAAAAAA),
                    fontSize = 24.sp
                )
            }
        }
        Text(
            item.name,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

private fun loadThumbnail(context: Context, uri: Uri): Bitmap? {
    return try {
        val mimeType = context.contentResolver.getType(uri).orEmpty()
        if (mimeType.startsWith("image/") ||
            uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() in
            setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
        ) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 320 ||
                bounds.outHeight / sampleSize > 320
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { frame ->
                        val scale = minOf(320f / frame.width, 320f / frame.height, 1f)
                        if (scale >= 1f) frame else Bitmap.createScaledBitmap(
                            frame,
                            (frame.width * scale).toInt().coerceAtLeast(1),
                            (frame.height * scale).toInt().coerceAtLeast(1),
                            true
                        ).also { if (it !== frame) frame.recycle() }
                    }
            } finally {
                retriever.release()
            }
        }
    } catch (error: Exception) {
        Log.w("RECENT_MEDIA", "Could not load thumbnail for $uri", error)
        null
    }
}
