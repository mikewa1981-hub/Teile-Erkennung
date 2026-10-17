package de.mikewa.teileerkennung

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun PartScreen(vm: AppViewModel) {
    val candidate = vm.selectedCandidate ?: return
    var detail by remember { mutableStateOf<LegoSet?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.newScan() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Neues Foto")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.part?.name ?: candidate.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                    )
                    Text(
                        "Teil ${vm.part?.partNum ?: candidate.id}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Andere Treffer der Erkennung – antippen lädt deren Sets
        if (vm.candidates.size > 1) {
            item {
                Text(
                    "Andere Treffer",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(vm.candidates) { c ->
                        val selected = c.id == candidate.id
                        Column(
                            Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                )
                                .clickable(enabled = !selected) { vm.openCandidate(c) }
                                .padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ImageBox(c.imgUrl, Modifier.size(64.dp))
                            Text(c.id, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(
                                "${(c.score * 100).roundToInt()} %",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // Vergleichsbilder: dein Foto, Erkennungs-Referenz, Datenbank-Bild
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompareTile("Dein Foto", Modifier.weight(1f)) {
                    vm.photo?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Dein Foto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                CompareTile("Erkennung", Modifier.weight(1f)) {
                    ImageBox(candidate.imgUrl, Modifier.fillMaxSize())
                }
                CompareTile(vm.setsSource ?: "Datenbank", Modifier.weight(1f)) {
                    ImageBox(vm.part?.imgUrl, Modifier.fillMaxSize())
                }
            }
        }

        if (vm.partLoading) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Teil wird nachgeschlagen …")
                }
            }
        }

        vm.sourceNote?.let { note ->
            item {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        vm.partError?.let { msg ->
            item {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (vm.colors.isNotEmpty()) {
            item {
                Text(
                    "Farbe wählen",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterChip(
                            selected = vm.selectedColor == null,
                            onClick = { vm.loadSets(null) },
                            label = { Text("Alle Farben") },
                        )
                    }
                    items(vm.colors, key = { it.colorId }) { c ->
                        FilterChip(
                            selected = vm.selectedColor == c.colorId,
                            onClick = { vm.loadSets(c.colorId) },
                            label = { Text("${c.colorName} (${c.numSets})") },
                        )
                    }
                }
            }
        }

        if (vm.setsLoading) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(vm.setsProgress ?: "Sets werden geladen …", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (!vm.partLoading && !vm.setsLoading && vm.partError == null && vm.part != null && vm.sets.isEmpty()) {
            item {
                Text(
                    "Keine Sets mit diesem Teil in dieser Farbe gefunden. Wähle oben eine andere Farbe oder \"Alle Farben\".",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        if (vm.sets.isNotEmpty()) {
            item {
                Text(
                    "${vm.sets.size} Sets mit diesem Teil" + (vm.setsSource?.let { " (Quelle: $it)" } ?: "") +
                        if (vm.sets.any { it.partQty > 0 }) " – Zahl rechts = Anzahl im Set" else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(vm.sets, key = { it.setNum }) { set ->
                val inCollection = vm.isInCollection(set.setNum)
                SetRow(
                    set = set,
                    onClick = { detail = set },
                    trailing = {
                        IconButton(onClick = {
                            if (inCollection) vm.removeSet(set.setNum) else vm.addSet(set)
                        }) {
                            Icon(
                                if (inCollection) Icons.Filled.CheckCircle else Icons.Filled.AddCircleOutline,
                                contentDescription = if (inCollection) "In Sammlung" else "Zur Sammlung hinzufügen",
                                tint = if (inCollection) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }

        item {
            androidx.compose.material3.OutlinedButton(
                onClick = { vm.newScan() },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) { Text("Neues Foto aufnehmen") }
            Spacer(Modifier.height(16.dp))
        }
    }

    detail?.let { set ->
        val inCollection = vm.isInCollection(set.setNum)
        SetDialog(
            set = set,
            inCollection = inCollection,
            onToggle = {
                if (inCollection) vm.removeSet(set.setNum) else vm.addSet(set)
                detail = null
            },
            onDismiss = { detail = null },
        )
    }
}

@Composable
private fun CompareTile(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F1F1)),
        ) { content() }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelSmall)
    }
}
