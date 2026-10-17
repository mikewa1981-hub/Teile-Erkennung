package de.mikewa.teileerkennung

import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : IOException(message)

/** Alle Aufrufe sind blockierend und gehören in Dispatchers.IO. */
object Api {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val BRICKOGNIZE = "https://api.brickognize.com/predict/parts/"
    private const val REBRICKABLE = "https://rebrickable.com/api/v3"

    // ---------------------------------------------------------------- Erkennung

    fun recognize(jpeg: ByteArray): List<PartCandidate> {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "query_image",
                "teil.jpg",
                jpeg.toRequestBody("image/jpeg".toMediaType()),
            )
            .build()
        val request = Request.Builder().url(BRICKOGNIZE).post(body).build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException("Erkennung fehlgeschlagen (HTTP ${resp.code})", resp.code)
            val items = JSONObject(text).optJSONArray("items") ?: JSONArray()
            return (0 until items.length()).map { i ->
                val o = items.getJSONObject(i)
                PartCandidate(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    imgUrl = o.optString("img_url").ifBlank { null },
                    score = o.optDouble("score", 0.0),
                )
            }.filter { it.id.isNotBlank() }
        }
    }

    // ---------------------------------------------------------------- Rebrickable

    private fun rebrickableGet(url: String, apiKey: String): JSONObject {
        var attempt = 0
        while (true) {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "key $apiKey")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> return JSONObject(text)
                    resp.code == 429 && attempt < 4 -> {
                        val wait = resp.header("Retry-After")?.toLongOrNull() ?: 2L
                        Thread.sleep((wait.coerceIn(1, 10)) * 1000)
                        attempt++
                    }
                    resp.code == 401 || resp.code == 403 ->
                        throw ApiException("Rebrickable lehnt den API-Key ab (HTTP ${resp.code}). Bitte in den Einstellungen prüfen.", resp.code)
                    else -> throw ApiException("Rebrickable-Fehler (HTTP ${resp.code})", resp.code)
                }
            }
        }
    }

    private fun parsePart(o: JSONObject) = PartInfo(
        partNum = o.getString("part_num"),
        name = o.optString("name"),
        imgUrl = o.optString("part_img_url").ifBlank { null },
        url = o.optString("part_url").ifBlank { null },
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** Wandelt die erkannte Nummer (BrickLink) in ein Rebrickable-Teil um. */
    fun resolvePart(candidateId: String, apiKey: String): PartInfo? {
        val byBricklink = rebrickableGet("$REBRICKABLE/lego/parts/?bricklink_id=${enc(candidateId)}", apiKey)
        val results = byBricklink.optJSONArray("results")
        if (results != null && results.length() > 0) return parsePart(results.getJSONObject(0))
        return try {
            parsePart(rebrickableGet("$REBRICKABLE/lego/parts/${enc(candidateId)}/", apiKey))
        } catch (e: ApiException) {
            if (e.code == 404) null else throw e
        }
    }

    fun partColors(partNum: String, apiKey: String): List<PartColor> {
        val json = rebrickableGet("$REBRICKABLE/lego/parts/${enc(partNum)}/colors/?page_size=1000", apiKey)
        val arr = json.optJSONArray("results") ?: JSONArray()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PartColor(
                colorId = o.getInt("color_id"),
                colorName = o.optString("color_name"),
                numSets = o.optInt("num_sets"),
                imgUrl = o.optString("part_img_url").ifBlank { null },
            )
        }.sortedByDescending { it.numSets }
    }

    fun setsForPartColor(partNum: String, colorId: Int, apiKey: String): List<LegoSet> {
        val out = ArrayList<LegoSet>()
        var url: String? = "$REBRICKABLE/lego/parts/${enc(partNum)}/colors/$colorId/sets/?page_size=1000"
        while (url != null) {
            val json = rebrickableGet(url, apiKey)
            val arr = json.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += LegoSet(
                    setNum = o.getString("set_num"),
                    name = o.optString("name"),
                    year = o.optInt("year"),
                    numParts = o.optInt("num_parts"),
                    imgUrl = o.optString("set_img_url").ifBlank { null },
                    url = o.optString("set_url").ifBlank { null },
                )
            }
            url = json.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }
        return out
    }

    // ---------------------------------------------------------------- Konto / Sammlung

    /** Meldet sich einmalig mit Benutzername und Passwort an und liefert den Benutzer-Token. */
    fun login(username: String, password: String, apiKey: String): String {
        val form = FormBody.Builder().add("username", username).add("password", password).build()
        val request = Request.Builder()
            .url("$REBRICKABLE/users/_token/")
            .header("Authorization", "key $apiKey")
            .post(form)
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw ApiException("Anmeldung fehlgeschlagen (HTTP ${resp.code}). Benutzername, Passwort und API-Key prüfen.", resp.code)
            }
            return JSONObject(text).getString("user_token")
        }
    }

    fun remoteSets(userToken: String, apiKey: String): List<LegoSet> {
        val out = ArrayList<LegoSet>()
        var url: String? = "$REBRICKABLE/users/${enc(userToken)}/sets/?page_size=1000"
        while (url != null) {
            val json = rebrickableGet(url, apiKey)
            val arr = json.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i).getJSONObject("set")
                out += LegoSet(
                    setNum = s.getString("set_num"),
                    name = s.optString("name"),
                    year = s.optInt("year"),
                    numParts = s.optInt("num_parts"),
                    imgUrl = s.optString("set_img_url").ifBlank { null },
                    url = s.optString("set_url").ifBlank { null },
                )
            }
            url = json.optString("next").takeIf { it.isNotBlank() && it != "null" }
        }
        return out
    }

    fun addRemoteSet(setNum: String, userToken: String, apiKey: String) {
        val form = FormBody.Builder().add("set_num", setNum).add("quantity", "1").build()
        val request = Request.Builder()
            .url("$REBRICKABLE/users/${enc(userToken)}/sets/")
            .header("Authorization", "key $apiKey")
            .post(form)
            .build()
        client.newCall(request).execute().use { resp ->
            // 400 kommt, wenn das Set schon in der Sammlung ist – das ist für uns in Ordnung.
            if (!resp.isSuccessful && resp.code != 400) {
                throw ApiException("Set $setNum konnte nicht übertragen werden (HTTP ${resp.code})", resp.code)
            }
        }
    }

    fun deleteRemoteSet(setNum: String, userToken: String, apiKey: String) {
        val request = Request.Builder()
            .url("$REBRICKABLE/users/${enc(userToken)}/sets/${enc(setNum)}/")
            .header("Authorization", "key $apiKey")
            .delete()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) {
                throw ApiException("Set $setNum konnte nicht entfernt werden (HTTP ${resp.code})", resp.code)
            }
        }
    }
}
