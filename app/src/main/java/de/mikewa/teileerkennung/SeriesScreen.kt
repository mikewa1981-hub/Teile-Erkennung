package de.mikewa.teileerkennung

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// Farben angelehnt an die Beilagen: kräftiger Kopf, helles "Papier"
private val LeafletHead = Color(0xFF2B1C5E)
private val LeafletAccent = Color(0xFFF2CD37)
private val LeafletPaper = Color(0xFFFFF8E7)
private val Owned = Color(0xFF2E9E44)

private val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun SeriesScreen(vm: AppViewModel) {
    val open = vm.openSeries
    BackHandler(enabled = open != null) { vm.showSeries(null) }
    if (open != null) SeriesSheet(vm, open) else SeriesList(vm)
}

// ------------------------------------------------------------------ Übersicht aller Serien

@Composable
private fun SeriesList(vm: AppViewModel) {
    var manual by remember { mutableStateOf("") }

    // Beim ersten Öffnen einmal die Sammlung durchsuchen
    LaunchedEffect(Unit) {
        if (vm.series.isEmpty() && vm.brickLink.complete) vm.refreshSeries()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sammelserien", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Alle Minifiguren-Sammelserien mit Figuren aus deiner Sammlung. " +
                        "Antippen zeigt die Übersicht wie auf der Beilage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { vm.refreshSeries() },
                    enabled = !vm.seriesLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (vm.seriesLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Sammlung nach Sammelfiguren durchsuchen")
                }
                vm.seriesProgress?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                vm.seriesMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Serie per Nummer, z. B. 71045") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            vm.refreshSeries(manual)
                            manual = ""
                        },
                        enabled = manual.length >= 4 && !vm.seriesLoading,
                    ) { Text("Laden") }
                }
            }
        }

        items(vm.series, key = { it.base }) { s ->
            SeriesCard(vm, s)
        }
    }
}

@Composable
private fun SeriesCard(vm: AppViewModel, s: CmfSeries) {
    val owned = vm.ownedCount(s)
    val total = s.figures.size
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { vm.showSeries(s.base) },
        colors = CardDefaults.cardColors(containerColor = LeafletPaper),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${s.base} · $owned von $total Figuren", style = MaterialTheme.typography.bodySmall)
                }
                if (owned == total) {
                    Text("Komplett!", color = Owned, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else owned.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Owned,
                trackColor = Color(0xFFE6DCC3),
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(s.figures, key = { it.setNum }) { f ->
                    val has = vm.isInCollection(f.setNum)
                    AsyncImage(
                        model = f.imgUrl,
                        contentDescription = f.name,
                        contentScale = ContentScale.Fit,
                        colorFilter = if (has) null else grayscale,
                        modifier = Modifier.size(width = 34.dp, height = 46.dp).alpha(if (has) 1f else 0.3f),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Beilage einer Serie

@Composable
private fun SeriesSheet(vm: AppViewModel, s: CmfSeries) {
    val owned = vm.ownedCount(s)
    var detail by remember { mutableStateOf<CmfFigure?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize().background(LeafletPaper),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Kopf wie auf der Beilage
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LeafletHead)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.showSeries(null) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "MINIFIGUREN",
                            color = LeafletAccent,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 3.sp,
                        )
                        Text(
                            s.title,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { vm.removeSeries(s.base) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Serie ausblenden", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { if (s.figures.isEmpty()) 0f else owned.toFloat() / s.figures.size },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = LeafletAccent,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("$owned / ${s.figures.size}", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(
                    "Farbig = in deiner Sammlung · Antippen zum Abhaken",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        items(s.figures, key = { it.setNum }) { f ->
            FigureTile(
                figure = f,
                owned = vm.isInCollection(f.setNum),
                onClick = { vm.toggleFigure(f) },
                onLongClick = { detail = f },
            )
        }
    }

    detail?.let { f ->
        SetDialog(
            set = LegoSet(f.setNum, f.name, 0, 0, f.imgUrl, BrickLink.setUrl(f.setNum)),
            inCollection = vm.isInCollection(f.setNum),
            onToggle = {
                vm.toggleFigure(f)
                detail = null
            },
            onDismiss = { detail = null },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FigureTile(figure: CmfFigure, owned: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(
                if (owned) BorderStroke(2.dp, Owned) else BorderStroke(1.dp, Color(0xFFE6DCC3)),
                RoundedCornerShape(12.dp),
            )
            .combinedClickableCompat(onClick, onLongClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f)) {
            AsyncImage(
                model = figure.imgUrl,
                contentDescription = figure.name,
                contentScale = ContentScale.Fit,
                colorFilter = if (owned) null else grayscale,
                modifier = Modifier.fillMaxSize().padding(top = 6.dp).alpha(if (owned) 1f else 0.3f),
            )
            // Nummer wie auf der Beilage
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (owned) LeafletHead else Color(0xFFBDB5A0)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${figure.number}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            // Häkchen-Kästchen
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (owned) Owned else Color.White)
                    .border(BorderStroke(1.5.dp, if (owned) Owned else Color(0xFFBDB5A0)), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (owned) Icon(Icons.Filled.Check, contentDescription = "Vorhanden", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            figure.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (owned) Color.Black else Color.Gray,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)
