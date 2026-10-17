package de.mikewa.teileerkennung

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.UnknownHostException

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    // ------------------------------------------------------------ Einstellungen
    var apiKey by mutableStateOf(store.apiKey); private set
    var username by mutableStateOf(store.username); private set
    var loggedIn by mutableStateOf(store.userToken.isNotBlank()); private set
    var settingsMessage by mutableStateOf<String?>(null); private set
    var loginRunning by mutableStateOf(false); private set

    fun saveApiKey(key: String) {
        store.apiKey = key
        apiKey = store.apiKey
        settingsMessage = "API-Key gespeichert."
    }

    fun login(user: String, password: String) {
        if (apiKey.isBlank()) {
            settingsMessage = "Bitte zuerst den API-Key speichern."
            return
        }
        if (user.isBlank() || password.isBlank()) {
            settingsMessage = "Benutzername und Passwort eingeben."
            return
        }
        loginRunning = true
        settingsMessage = null
        viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) { Api.login(user.trim(), password, apiKey) }
                store.userToken = token
                store.username = user
                username = store.username
                loggedIn = true
                settingsMessage = "Angemeldet. Das Passwort wird nicht gespeichert."
            } catch (e: Exception) {
                settingsMessage = friendly(e)
            } finally {
                loginRunning = false
            }
        }
    }

    fun logout() {
        store.userToken = ""
        loggedIn = false
        settingsMessage = "Abgemeldet."
    }

    // ------------------------------------------------------------ Scan
    var photo by mutableStateOf<Bitmap?>(null); private set
    var recognizing by mutableStateOf(false); private set
    var candidates by mutableStateOf<List<PartCandidate>>(emptyList()); private set
    var scanError by mutableStateOf<String?>(null); private set

    fun onPhoto(bitmap: Bitmap) {
        val scaled = scaleDown(bitmap, 1024)
        photo = scaled
        candidates = emptyList()
        scanError = null
        recognizing = true
        viewModelScope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) {
                    ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }.toByteArray()
                }
                val result = withContext(Dispatchers.IO) { Api.recognize(jpeg) }
                candidates = result.take(8)
                if (result.isEmpty()) scanError = "Kein Teil erkannt. Näher ran, besseres Licht, heller Hintergrund."
            } catch (e: Exception) {
                scanError = friendly(e)
            } finally {
                recognizing = false
            }
        }
    }

    fun clearScan() {
        photo = null
        candidates = emptyList()
        scanError = null
        recognizing = false
    }

    private fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= maxSide) return bmp
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt(), (bmp.height * f).toInt(), true)
    }

    // ------------------------------------------------------------ Teil + Sets
    var selectedCandidate by mutableStateOf<PartCandidate?>(null); private set
    var part by mutableStateOf<PartInfo?>(null); private set
    var partLoading by mutableStateOf(false); private set
    var partError by mutableStateOf<String?>(null); private set
    var colors by mutableStateOf<List<PartColor>>(emptyList()); private set

    /** null = alle Farben */
    var selectedColor by mutableStateOf<Int?>(null); private set
    var sets by mutableStateOf<List<LegoSet>>(emptyList()); private set
    var setsLoading by mutableStateOf(false); private set
    var setsProgress by mutableStateOf<String?>(null); private set

    val partOpen: Boolean get() = selectedCandidate != null

    private var partJob: Job? = null
    private var setsJob: Job? = null
    private var partGen = 0
    private var setsGen = 0

    fun openCandidate(c: PartCandidate) {
        selectedCandidate = c
        part = null
        colors = emptyList()
        sets = emptyList()
        partError = null
        selectedColor = null
        setsProgress = null
        if (apiKey.isBlank()) {
            partError = "Bitte zuerst in den Einstellungen den Rebrickable-API-Key eintragen."
            return
        }
        partJob?.cancel()
        setsJob?.cancel()
        partLoading = true
        val gen = ++partGen
        partJob = viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { Api.resolvePart(c.id, apiKey) }
                if (info == null) {
                    partError = "Teil ${c.id} wurde in der Rebrickable-Datenbank nicht gefunden."
                    return@launch
                }
                part = info
                val cols = withContext(Dispatchers.IO) { Api.partColors(info.partNum, apiKey) }
                colors = cols
                if (cols.isEmpty()) {
                    partError = "Für dieses Teil sind keine Sets bekannt."
                } else {
                    loadSets(cols.first().colorId)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                partError = friendly(e)
            } finally {
                if (gen == partGen) partLoading = false
            }
        }
    }

    fun closePart() {
        partGen++
        setsGen++
        partJob?.cancel()
        setsJob?.cancel()
        selectedCandidate = null
        part = null
        colors = emptyList()
        sets = emptyList()
        partError = null
        partLoading = false
        setsLoading = false
        setsProgress = null
    }

    /** [colorId] = null lädt die Sets aller Farben nacheinander (wegen des Rebrickable-Limits etwas langsamer). */
    fun loadSets(colorId: Int?) {
        val p = part ?: return
        selectedColor = colorId
        setsJob?.cancel()
        sets = emptyList()
        partError = null
        setsLoading = true
        val gen = ++setsGen
        setsJob = viewModelScope.launch {
            try {
                if (colorId != null) {
                    sets = withContext(Dispatchers.IO) { Api.setsForPartColor(p.partNum, colorId, apiKey) }
                        .sortedByDescending { it.year }
                } else {
                    val merged = LinkedHashMap<String, LegoSet>()
                    val all = colors
                    all.forEachIndexed { i, c ->
                        setsProgress = "Farbe ${i + 1} von ${all.size}: ${c.colorName}"
                        val batch = withContext(Dispatchers.IO) { Api.setsForPartColor(p.partNum, c.colorId, apiKey) }
                        batch.forEach { merged.putIfAbsent(it.setNum, it) }
                        sets = merged.values.sortedByDescending { it.year }
                        if (i < all.size - 1) delay(1100)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                partError = friendly(e)
            } finally {
                if (gen == setsGen) {
                    setsLoading = false
                    setsProgress = null
                }
            }
        }
    }

    // ------------------------------------------------------------ Sammlung
    var collection by mutableStateOf(store.loadCollection()); private set
    var syncing by mutableStateOf(false); private set
    var syncMessage by mutableStateOf<String?>(null); private set

    val visibleCollection: List<CollectionEntry>
        get() = collection.filterNot { it.deleted }.sortedBy { it.set.setNum }

    fun isInCollection(setNum: String) = collection.any { it.set.setNum == setNum && !it.deleted }

    fun addSet(set: LegoSet) {
        val existing = collection.firstOrNull { it.set.setNum == set.setNum }
        collection = if (existing == null) {
            collection + CollectionEntry(set)
        } else {
            // war zur Löschung vorgemerkt -> wieder aktiv
            collection.map { if (it.set.setNum == set.setNum) it.copy(deleted = false) else it }
        }
        store.saveCollection(collection)
    }

    fun removeSet(setNum: String) {
        collection = collection.mapNotNull {
            when {
                it.set.setNum != setNum -> it
                it.synced -> it.copy(deleted = true) // auf Rebrickable erst beim Abgleich löschen
                else -> null
            }
        }
        store.saveCollection(collection)
    }

    /** Gleicht die lokale Sammlung per Knopfdruck mit der Rebrickable-Sammlung ab (Verwaltung am PC auf rebrickable.com). */
    fun sync() {
        val token = store.userToken
        if (apiKey.isBlank() || token.isBlank()) {
            syncMessage = "Zuerst in den Einstellungen API-Key eintragen und bei Rebrickable anmelden."
            return
        }
        syncing = true
        syncMessage = null
        viewModelScope.launch {
            try {
                val snapshot = collection
                val result = withContext(Dispatchers.IO) {
                    val remote = Api.remoteSets(token, apiKey)
                    val remoteNums = remote.map { it.setNum }.toSet()

                    // 1. lokal gelöschte Sets auch auf Rebrickable löschen
                    val deletedNums = snapshot.filter { it.deleted }.map { it.set.setNum }.toSet()
                    var removedRemote = 0
                    deletedNums.filter { it in remoteNums }.forEach {
                        Api.deleteRemoteSet(it, token, apiKey)
                        removedRemote++
                    }
                    var list = snapshot.filterNot { it.deleted }

                    // 2. neue lokale Sets hochladen
                    var uploaded = 0
                    list.filter { !it.synced && it.set.setNum !in remoteNums }.forEach {
                        Api.addRemoteSet(it.set.setNum, token, apiKey)
                        uploaded++
                    }

                    // 3. früher abgeglichene Sets, die am PC gelöscht wurden, lokal entfernen
                    val before = list.size
                    list = list.filterNot { it.synced && it.set.setNum !in remoteNums }
                    val removedLocal = before - list.size

                    // 4. neue Sets vom PC übernehmen
                    val localNums = list.map { it.set.setNum }.toSet()
                    val incoming = remote.filter { it.setNum !in localNums && it.setNum !in deletedNums }
                    list = list + incoming.map { CollectionEntry(it) }

                    // 5. alles ist jetzt abgeglichen
                    val finalList = list.map { it.copy(synced = true, deleted = false) }
                    SyncResult(finalList, uploaded, incoming.size, removedRemote + removedLocal)
                }
                collection = result.list
                store.saveCollection(collection)
                syncMessage = "Abgleich fertig: ${result.uploaded} hochgeladen, ${result.downloaded} vom PC übernommen, ${result.removed} entfernt."
            } catch (e: Exception) {
                syncMessage = friendly(e)
            } finally {
                syncing = false
            }
        }
    }

    private class SyncResult(val list: List<CollectionEntry>, val uploaded: Int, val downloaded: Int, val removed: Int)

    // ------------------------------------------------------------ Hilfen
    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "Keine Internetverbindung."
        is ApiException -> e.message ?: "Netzwerkfehler."
        is java.net.SocketTimeoutException -> "Zeitüberschreitung. Bitte erneut versuchen."
        else -> e.message ?: "Unbekannter Fehler."
    }
}
