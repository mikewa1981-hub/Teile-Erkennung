package de.mikewa.teileerkennung

/** Treffer der Bilderkennung (Brickognize). [id] ist in der Regel die BrickLink-Teilenummer. */
data class PartCandidate(
    val id: String,
    val name: String,
    val imgUrl: String?,
    val score: Double,
)

/** Teil aus der Rebrickable-Datenbank. */
data class PartInfo(
    val partNum: String,
    val name: String,
    val imgUrl: String?,
    val url: String?,
)

data class PartColor(
    val colorId: Int,
    val colorName: String,
    val numSets: Int,
    val imgUrl: String?,
)

data class LegoSet(
    val setNum: String,
    val name: String,
    val year: Int,
    val numParts: Int,
    val imgUrl: String?,
    val url: String?,
)

/**
 * Eintrag der lokalen Sammlung.
 * [synced]: war beim letzten Abgleich auf Rebrickable vorhanden.
 * [deleted]: lokal entfernt, Löschung auf Rebrickable steht noch aus.
 */
data class CollectionEntry(
    val set: LegoSet,
    val synced: Boolean = false,
    val deleted: Boolean = false,
)
