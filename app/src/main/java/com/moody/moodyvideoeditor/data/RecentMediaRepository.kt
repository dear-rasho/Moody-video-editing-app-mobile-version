package com.moody.moodyvideoeditor.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentMediaItem(
    val uri: Uri,
    val name: String
)

object RecentMediaRepository {
    private const val PREFERENCES_NAME = "recent_media"
    private const val MAX_ITEMS = 15

    fun getRecent(context: Context, projectId: String): List<RecentMediaItem> {
        val key = projectKey(projectId)
        val raw = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(key, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val entry = array.optJSONObject(index) ?: continue
                    val uri = entry.optString("uri").takeIf { it.isNotBlank() } ?: continue
                    val name = entry.optString("name").ifBlank { "Media" }
                    add(RecentMediaItem(Uri.parse(uri), name))
                }
            }
        } catch (error: org.json.JSONException) {
            throw IllegalStateException("Could not read recent media for project $projectId", error)
        }
    }

    fun add(context: Context, projectId: String, item: RecentMediaItem) {
        val key = projectKey(projectId)
        val updated = (listOf(item) + getRecent(context, projectId)
            .filterNot { it.uri == item.uri })
            .take(MAX_ITEMS)
        val array = JSONArray()
        updated.forEach { recent ->
            array.put(
                JSONObject()
                    .put("uri", recent.uri.toString())
                    .put("name", recent.name)
            )
        }
        val saved = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, array.toString())
            .commit()
        check(saved) { "Could not save recent media for project $projectId" }
    }

    private fun projectKey(projectId: String): String =
        "project_${projectId.ifBlank { "default" }}"
}
