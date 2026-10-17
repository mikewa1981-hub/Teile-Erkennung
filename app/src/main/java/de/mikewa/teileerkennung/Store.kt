package de.mikewa.teileerkennung

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Einstellungen und lokale Sammlung, gespeichert in SharedPreferences. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("teile_erkennung", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString("api_key", "").orEmpty()
        set(v) = prefs.edit().putString("api_key", v.trim()).apply()

    var username: String
        get() = prefs.getString("username", "").orEmpty()
        set(v) = prefs.edit().putString("username", v.trim()).apply()

    var userToken: String
        get() = prefs.getString("user_token", "").orEmpty()
        set(v) = prefs.edit().putString("user_token", v).apply()

    fun loadCollection(): List<CollectionEntry> {
        val raw = prefs.getString("collection", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CollectionEntry(
                    set = LegoSet(
                        setNum = o.getString("setNum"),
                        name = o.optString("name"),
                        year = o.optInt("year"),
                        numParts = o.optInt("numParts"),
                        imgUrl = o.optString("imgUrl").ifBlank { null },
                        url = o.optString("url").ifBlank { null },
                    ),
                    synced = o.optBoolean("synced"),
                    deleted = o.optBoolean("deleted"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCollection(list: List<CollectionEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("setNum", e.set.setNum)
                    .put("name", e.set.name)
                    .put("year", e.set.year)
                    .put("numParts", e.set.numParts)
                    .put("imgUrl", e.set.imgUrl ?: "")
                    .put("url", e.set.url ?: "")
                    .put("synced", e.synced)
                    .put("deleted", e.deleted),
            )
        }
        prefs.edit().putString("collection", arr.toString()).apply()
    }
}
