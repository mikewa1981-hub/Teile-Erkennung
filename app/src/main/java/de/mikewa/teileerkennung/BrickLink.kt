package de.mikewa.teileerkennung

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Zugangsdaten der BrickLink-API (OAuth 1.0, unter bricklink.com → My BrickLink → API erzeugt). */
data class BrickLinkCredentials(
    val consumerKey: String,
    val consumerSecret: String,
    val tokenValue: String,
    val tokenSecret: String,
) {
    val complete: Boolean
        get() = listOf(consumerKey, consumerSecret, tokenValue, tokenSecret).all { it.isNotBlank() }
}

/** Alle Aufrufe sind blockierend und gehören in Dispatchers.IO. */
object BrickLink {

    private const val BASE = "https://api.bricklink.com/api/store/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val random = SecureRandom()

    /** RFC 3986 – so wie OAuth 1.0 es verlangt. */
    private fun pct(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    private fun authHeader(method: String, baseUrl: String, query: Map<String, String>, c: BrickLinkCredentials): String {
        val nonce = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val oauth = sortedMapOf(
            "oauth_consumer_key" to c.consumerKey.trim(),
            "oauth_nonce" to nonce,
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000).toString(),
            "oauth_token" to c.tokenValue.trim(),
            "oauth_version" to "1.0",
        )
        val all = (oauth + query).toSortedMap()
        val paramString = all.entries.joinToString("&") { "${pct(it.key)}=${pct(it.value)}" }
        val baseString = "${method.uppercase()}&${pct(baseUrl)}&${pct(paramString)}"
        val key = "${pct(c.consumerSecret.trim())}&${pct(c.tokenSecret.trim())}"
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA1")) }
        val signature = android.util.Base64.encodeToString(mac.doFinal(baseString.toByteArray()), android.util.Base64.NO_WRAP)
        val headerParams = oauth + ("oauth_signature" to signature)
        return "OAuth realm=\"\", " + headerParams.entries.joinToString(", ") { "${it.key}=\"${pct(it.value)}\"" }
    }

    /** Liefert das "data"-Feld (Objekt oder Array) einer BrickLink-Antwort. */
    private fun get(path: String, query: Map<String, String>, c: BrickLinkCredentials): Any {
        val baseUrl = BASE + path
        val url = if (query.isEmpty()) baseUrl
        else baseUrl + "?" + query.entries.joinToString("&") { "${pct(it.key)}=${pct(it.value)}" }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader("GET", baseUrl, query, c))
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException("BrickLink HTTP ${resp.code}: ${text.take(160)}", resp.code)
            }
            val meta = json.optJSONObject("meta")
            val code = meta?.optInt("code", resp.code) ?: resp.code
            if (code !in 200..299) {
                val msg = meta?.optString("message").orEmpty()
                val desc = meta?.optString("description").orEmpty()
                val hint = when {
                    msg.contains("IP", ignoreCase = true) || desc.contains("IP", ignoreCase = true) ->
                        " – Tipp: Beim BrickLink-Token als IP-Adresse und Maske 0.0.0.0 eintragen, dann geht es von jedem Netz."
                    code == 401 -> " – Bitte die vier BrickLink-Schlüssel in den Einstellungen prüfen."
                    else -> ""
                }
                throw ApiException("BrickLink-Fehler $code: $msg $desc$hint".trim(), code)
            }
            return json.opt("data") ?: JSONObject()
        }
    }

    private fun fixUrl(u: String?): String? {
        if (u.isNullOrBlank() || u == "null") return null
        return if (u.startsWith("//")) "https:$u" else u
    }

    fun setImage(setNum: String) = "https://img.bricklink.com/ItemImage/SN/0/$setNum.png"
    fun setUrl(setNum: String) = "https://www.bricklink.com/v2/catalog/catalogitem.page?S=$setNum"

    fun test(c: BrickLinkCredentials): String {
        val data = get("/items/PART/3001", emptyMap(), c) as JSONObject
        return "Verbindung zu BrickLink OK (Testteil: ${data.optString("name")})."
    }

    fun part(no: String, c: BrickLinkCredentials): PartInfo? = try {
        val o = get("/items/PART/${pct(no)}", emptyMap(), c) as JSONObject
        PartInfo(
            partNum = o.optString("no", no),
            name = android.text.Html.fromHtml(o.optString("name"), 0).toString(),
            imgUrl = fixUrl(o.optString("image_url")),
            url = "https://www.bricklink.com/v2/catalog/catalogitem.page?P=$no",
        )
    } catch (e: ApiException) {
        if (e.code == 404) null else throw e
    }

    private var colorNames: Map<Int, String>? = null

    private fun colorNames(c: BrickLinkCredentials): Map<Int, String> {
        colorNames?.let { return it }
        val arr = get("/colors", emptyMap(), c) as JSONArray
        val map = (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            o.getInt("color_id") to o.optString("color_name")
        }
        colorNames = map
        return map
    }

    /**
     * Alle Sets, die das Teil enthalten – gruppiert nach BrickLink-Farbe.
     * Ein einziger Aufruf liefert alle Farben auf einmal.
     */
    fun setsByColor(partNo: String, c: BrickLinkCredentials): Map<PartColor, List<LegoSet>> {
        val arr = get("/items/PART/${pct(partNo)}/supersets", emptyMap(), c) as JSONArray
        val names = try { colorNames(c) } catch (e: Exception) { emptyMap() }
        val result = LinkedHashMap<PartColor, List<LegoSet>>()
        for (i in 0 until arr.length()) {
            val group = arr.getJSONObject(i)
            val colorId = group.optInt("color_id")
            val entries = group.optJSONArray("entries") ?: JSONArray()
            // Ein Set kann mehrfach auftauchen (regulär, als Ersatzteil, …) – Mengen zusammenzählen.
            val bySet = LinkedHashMap<String, LegoSet>()
            for (j in 0 until entries.length()) {
                val entry = entries.getJSONObject(j)
                val item = entry.optJSONObject("item") ?: continue
                if (item.optString("type") != "SET") continue
                val no = item.optString("no")
                if (no.isBlank()) continue
                val qty = entry.optInt("quantity", 0)
                val existing = bySet[no]
                bySet[no] = existing?.copy(partQty = existing.partQty + qty) ?: LegoSet(
                    setNum = no,
                    name = android.text.Html.fromHtml(item.optString("name"), 0).toString(),
                    year = 0,
                    numParts = 0,
                    imgUrl = setImage(no),
                    url = setUrl(no),
                    partQty = qty,
                )
            }
            val sets = bySet.values.toList()
            if (sets.isEmpty()) continue
            val color = PartColor(
                colorId = colorId,
                colorName = names[colorId] ?: if (colorId == 0) "Ohne Farbe" else "Farbe $colorId",
                numSets = sets.size,
                imgUrl = "https://img.bricklink.com/ItemImage/PN/$colorId/${partNo}.png",
            )
            result[color] = sets
        }
        return result.entries.sortedByDescending { it.key.numSets }.associate { it.key to it.value }
    }
}
