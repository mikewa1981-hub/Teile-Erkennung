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

    private const val USER_AGENT = "TeileErkennung/1.1 (Android; private use)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    private const val BRICKOGNIZE = "https://api.brickognize.com/predict/parts/"
    private const val REBRICKABLE = "https://rebrickable.com/api/v3"

    /** org.json liefert für JSON-null den Text "null" – das hier gibt dann null zurück. */
    private fun JSONObject.str(name: String): String? {
        if (isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun snippet(body: String): String =
        body.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim().take(160)

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
                    id = o.str("id").orEmpty(),
                    name = o.str("name").orEmpty(),
                    imgUrl = o.str("img_url"),
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
                .header("Authorization", "key ${apiKey.trim()}")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.isSuccessful -> try {
                        return JSONObject(text)
                    } catch (e: Exception) {
                        throw ApiException("Rebrickable hat keine gültige Antwort geschickt: ${snippet(text)}")
                    }
                    resp.code == 429 && attempt < 5 -> {
                        val wait = resp.header("Retry-After")?.toLongOrNull() ?: 2L
                        Thread.sleep(wait.coerceIn(1, 10) * 1000)
                        attempt++
                    }
                    resp.code == 401 ->
                        throw ApiException("Rebrickable lehnt den API-Key ab (HTTP 401). Bitte den Key in den Einstellungen prüfen.", 401)
                    resp.code == 404 -> throw ApiException("Nicht gefunden (HTTP 404)", 404)
                    else -> throw ApiException("Rebrickable-Fehler HTTP ${resp.code}: ${snippet(text)}", resp.code)
                }
            }
        }
    }

    private fun parsePart(o: JSONObject) = PartInfo(
        partNum = o.getString("part_num"),
        name = o.str("name").orEmpty(),
        imgUrl = o.str("part_img_url"),
        url = o.str("part_url"),
    )

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun firstPart(json: JSONObject): PartInfo? {
        val results = json.optJSONArray("results") ?: return null
        return if (results.length() > 0) parsePart(results.getJSONObject(0)) else null
    }

    /** Ein billiger Aufruf, um API-Key und Verbindung zu prüfen. */
    fun testKey(apiKey: String): String {
        val json = rebrickableGet("$REBRICKABLE/lego/colors/?page_size=1", apiKey)
        return "Verbindung zu Rebrickable OK (${json.optInt("count")} Farben in der Datenbank)."
    }

    /**
     * Wandelt die erkannte Nummer in ein Rebrickable-Teil um.
     * Reihenfolge: direkte Teilenummer, BrickLink-Nummer, Nummer ohne Buchstaben-Endung, Textsuche.
     */
    fun resolvePart(candidateId: String, apiKey: String): PartInfo? {
        val id = candidateId.trim()

        fun direct(num: String): PartInfo? = try {
            parsePart(rebrickableGet("$REBRICKABLE/lego/parts/${enc(num)}/", apiKey))
        } catch (e: ApiException) {
            if (e.code == 404) null else throw e
        }

        direct(id)?.let { return it }
        firstPart(rebrickableGet("$REBRICKABLE/lego/parts/?bricklink_id=${enc(id)}", apiKey))?.let { return it }

        // z. B. "3001a" -> "3001"
        val base = Regex("^(\\d+)[a-z]$").find(id)?.groupValues?.get(1)
        if (base != null) direct(base)?.let { return it }

        return firstPart(rebrickableGet("$REBRICKABLE/lego/parts/?search=${enc(id)}&page_size=5", apiKey))
    }

    fun partColors(partNum: String, apiKey: String): List<PartColor> {
        val out = ArrayList<PartColor>()
        var url: String? = "$REBRICKABLE/lego/parts/${enc(partNum)}/colors/?page_size=1000"
        while (url != null) {
            val json = rebrickableGet(url, apiKey)
            val arr = json.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += PartColor(
                    colorId = o.getInt("color_id"),
                    colorName = o.str("color_name").orEmpty(),
                    numSets = o.optInt("num_sets"),
                    imgUrl = o.str("part_img_url"),
                )
            }
            url = json.str("next")
        }
        return out.sortedByDescending { it.numSets }
    }

    private fun parseSet(s: JSONObject) = LegoSet(
        setNum = s.getString("set_num"),
        name = s.str("name").orEmpty(),
        year = s.optInt("year"),
        numParts = s.optInt("num_parts"),
        imgUrl = s.str("set_img_url"),
        url = s.str("set_url"),
    )

    fun setsForPartColor(partNum: String, colorId: Int, apiKey: String): List<LegoSet> {
        val out = ArrayList<LegoSet>()
        var url: String? = "$REBRICKABLE/lego/parts/${enc(partNum)}/colors/$colorId/sets/?page_size=1000"
        while (url != null) {
            val json = rebrickableGet(url, apiKey)
            val arr = json.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) out += parseSet(arr.getJSONObject(i))
            url = json.str("next")
        }
        return out
    }

    // ---------------------------------------------------------------- Konto / Sammlung

    /** Meldet sich einmalig mit Benutzername und Passwort an und liefert den Benutzer-Token. */
    fun login(username: String, password: String, apiKey: String): String {
        val form = FormBody.Builder().add("username", username).add("password", password).build()
        val request = Request.Builder()
            .url("$REBRICKABLE/users/_token/")
            .header("Authorization", "key ${apiKey.trim()}")
            .post(form)
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw ApiException("Anmeldung fehlgeschlagen (HTTP ${resp.code}): ${snippet(text)}", resp.code)
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
            for (i in 0 until arr.length()) out += parseSet(arr.getJSONObject(i).getJSONObject("set"))
            url = json.str("next")
        }
        return out
    }

    fun addRemoteSet(setNum: String, userToken: String, apiKey: String) {
        val form = FormBody.Builder().add("set_num", setNum).add("quantity", "1").build()
        val request = Request.Builder()
            .url("$REBRICKABLE/users/${enc(userToken)}/sets/")
            .header("Authorization", "key ${apiKey.trim()}")
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
            .header("Authorization", "key ${apiKey.trim()}")
            .delete()
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 404) {
                throw ApiException("Set $setNum konnte nicht entfernt werden (HTTP ${resp.code})", resp.code)
            }
        }
    }
}
