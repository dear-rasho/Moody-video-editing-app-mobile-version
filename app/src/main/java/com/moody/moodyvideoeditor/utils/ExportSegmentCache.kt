package com.moody.moodyvideoeditor.utils

import android.content.Context
import android.util.Log
import com.moody.moodyvideoeditor.data.EditorClip
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

internal object ExportSegmentCache {
    private const val TAG = "EXPORT_CACHE"
    private const val CACHE_DIRECTORY = "moody_export_segments"
    private const val MAX_CACHE_BYTES = 2L * 1024L * 1024L * 1024L
    private const val RESERVED_FREE_BYTES = 512L * 1024L * 1024L
    private const val PARTIAL_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    private val reservations = mutableMapOf<String, Long>()

    fun keyFor(
        segmentIndex: Int,
        startMs: Long,
        endMs: Long,
        clips: List<EditorClip>,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        format: String
    ): String = keyForSignature(
        segmentIndex = segmentIndex,
        startMs = startMs,
        endMs = endMs,
        clipSignature = clips.joinToString(separator = "\n") { it.toString() },
        width = width,
        height = height,
        fps = fps,
        bitrateKbps = bitrateKbps,
        format = format
    )

    internal fun keyForSignature(
        segmentIndex: Int,
        startMs: Long,
        endMs: Long,
        clipSignature: String,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        format: String
    ): String {
        val signature = listOf(
            "segment-cache-v1",
            segmentIndex.toString(),
            startMs.toString(),
            endMs.toString(),
            width.toString(),
            height.toString(),
            fps.toString(),
            bitrateKbps.toString(),
            format.lowercase(),
            clipSignature
        ).joinToString(separator = "\u0000")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
    }

    fun find(context: Context, key: String, format: String): File? =
        synchronized(reservations) {
            val file = cacheFile(context, key, format)
            file.takeIf { it.isFile && it.length() > 0L }?.also {
                it.setLastModified(System.currentTimeMillis())
            }
        }

    fun reserve(context: Context, key: String, estimatedBytes: Long): Boolean =
        synchronized(reservations) {
            if (estimatedBytes <= 0L || reservations.containsKey(key)) return false
            val directory = directory(context)
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Could not create export cache directory")
            }
            pruneStalePartialFiles(directory)

            val cachedFiles = cacheFiles(directory).sortedBy { it.lastModified() }
            var cachedBytes = cachedFiles.sumOf { it.length() }
            val pendingBytes = reservations.values.sum()
            var availableBytes = directory.usableSpace
            val iterator = cachedFiles.iterator()
            while (
                cachedBytes + pendingBytes + estimatedBytes > MAX_CACHE_BYTES ||
                pendingBytes + estimatedBytes >
                (availableBytes - RESERVED_FREE_BYTES).coerceAtLeast(0L)
            ) {
                if (!iterator.hasNext()) break
                val stale = iterator.next()
                val staleBytes = stale.length()
                if (!stale.delete()) {
                    Log.w(TAG, "Could not evict cached segment ${stale.name}")
                    continue
                }
                cachedBytes -= staleBytes
                availableBytes = directory.usableSpace
            }
            if (
                cachedBytes + pendingBytes + estimatedBytes > MAX_CACHE_BYTES ||
                pendingBytes + estimatedBytes >
                (directory.usableSpace - RESERVED_FREE_BYTES).coerceAtLeast(0L)
            ) return false

            reservations[key] = estimatedBytes
            true
        }

    fun temporaryFile(context: Context, key: String, format: String): File {
        val directory = directory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create export cache directory")
        }
        return File(
            directory,
            "segment_${key}_${UUID.randomUUID()}.partial.${extension(format)}"
        )
    }

    fun commit(context: Context, key: String, format: String, temporary: File): File =
        synchronized(reservations) {
            if (!reservations.containsKey(key)) {
                temporary.delete()
                throw IOException("Export cache reservation expired")
            }
            val target = cacheFile(context, key, format)
            if (!temporary.isFile || temporary.length() <= 0L) {
                throw IOException("Rendered cache segment is empty")
            }
            if (target.exists() && target.length() > 0L) {
                temporary.delete()
                reservations.remove(key)
                return@synchronized target
            }
            val directory = directory(context)
            val actualBytes = temporary.length()
            val cachedFiles = cacheFiles(directory).sortedBy { it.lastModified() }
            var cachedBytes = cachedFiles.sumOf { it.length() }
            val pendingBytes = reservations
                .filterKeys { it != key }
                .values
                .sum()
            val iterator = cachedFiles.iterator()
            fun hasCapacity(): Boolean =
                cachedBytes + pendingBytes + actualBytes <= MAX_CACHE_BYTES &&
                        pendingBytes <=
                        (directory.usableSpace - RESERVED_FREE_BYTES).coerceAtLeast(0L)

            while (!hasCapacity() && iterator.hasNext()) {
                val stale = iterator.next()
                val staleBytes = stale.length()
                if (!stale.delete()) {
                    Log.w(TAG, "Could not evict cached segment ${stale.name}")
                    continue
                }
                cachedBytes -= staleBytes
            }
            if (!hasCapacity()) {
                throw IOException("Rendered segment exceeds the export cache budget")
            }
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                if (!temporary.delete()) {
                    Log.w(TAG, "Could not remove temporary segment ${temporary.name}")
                }
            }
            if (!target.isFile || target.length() <= 0L) {
                throw IOException("Could not save rendered cache segment")
            }
            target.setLastModified(System.currentTimeMillis())
            reservations.remove(key)
            target
        }

    fun release(key: String) {
        synchronized(reservations) {
            reservations.remove(key)
        }
    }

    fun clear(context: Context) {
        synchronized(reservations) {
            val directory = directory(context)
            if (directory.exists() && !directory.deleteRecursively()) {
                Log.w(TAG, "Could not clear export cache directory")
            }
            reservations.clear()
        }
    }

    private fun cacheFile(context: Context, key: String, format: String): File =
        File(directory(context), "segment_${key}.${extension(format)}")

    private fun directory(context: Context): File =
        File(context.cacheDir, CACHE_DIRECTORY)

    private fun cacheFiles(directory: File): List<File> =
        directory.listFiles()
            ?.filter { it.isFile && ".partial." !in it.name }
            .orEmpty()

    private fun pruneStalePartialFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - PARTIAL_MAX_AGE_MS
        directory.listFiles()
            ?.filter { it.isFile && ".partial." in it.name && it.lastModified() < cutoff }
            ?.forEach { file ->
                if (!file.delete()) Log.w(TAG, "Could not remove stale segment ${file.name}")
            }
    }

    private fun extension(format: String): String =
        format.lowercase().takeIf { it in setOf("mp4", "mov", "mkv") } ?: "mp4"
}
