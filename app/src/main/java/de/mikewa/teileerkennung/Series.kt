package de.mikewa.teileerkennung

import org.json.JSONArray
import org.json.JSONObject

/** Eine Figur einer Sammelserie, so wie sie auf der Beilage abgebildet ist. */
data class CmfFigure(
    /** BrickLink-Setnummer der Einzelfigur, z. B. "71045-3" – damit wird sie in der Sammlung geführt. */
    val setNum: String,
    /** Position auf der Beilage (1, 2, 3 …). */
    val number: Int,
    val name: String,
    val imgUrl: String,
)

data class CmfSeries(
    /** Grundnummer der Serie, z. B. "71045". */
    val base: String,
    val title: String,
    val figures: List<CmfFigure>,
)

object SeriesJson {
    fun write(list: List<CmfSeries>): String = JSONArray().apply {
        list.forEach { s ->
            put(
                JSONObject()
                    .put("base", s.base)
                    .put("title", s.title)
                    .put("figures", JSONArray().apply {
                        s.figures.forEach { f ->
                            put(
                                JSONObject()
                                    .put("setNum", f.setNum)
                                    .put("number", f.number)
                                    .put("name", f.name)
                                    .put("imgUrl", f.imgUrl),
                            )
                        }
                    }),
            )
        }
    }.toString()

    fun read(raw: String?): List<CmfSeries> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val figs = o.optJSONArray("figures") ?: JSONArray()
                CmfSeries(
                    base = o.getString("base"),
                    title = o.optString("title"),
                    figures = (0 until figs.length()).map { j ->
                        val f = figs.getJSONObject(j)
                        CmfFigure(f.getString("setNum"), f.optInt("number"), f.optString("name"), f.optString("imgUrl"))
                    },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

/** Grundnummer eines Sets: "71045-3" -> "71045". */
fun baseNumber(setNum: String): String = setNum.substringBefore('-')

/** Laufnummer: "71045-3" -> 3. */
fun suffixNumber(setNum: String): Int? = setNum.substringAfter('-', "").toIntOrNull()

/**
 * Grobe Vorauswahl, damit nicht jedes Set der Sammlung bei BrickLink nachgeschlagen werden muss.
 * Die eigentliche Prüfung macht danach die BrickLink-Kategorie.
 */
fun looksLikeCmf(set: LegoSet): Boolean {
    if (set.name.contains("minifig", ignoreCase = true)) return true
    val base = baseNumber(set.setNum)
    val n = base.toIntOrNull() ?: return false
    val suffix = suffixNumber(set.setNum) ?: return false
    if (suffix < 1) return false
    return n in 8683..8684 || n in 8800..8834 || n in 71000..71099
}
