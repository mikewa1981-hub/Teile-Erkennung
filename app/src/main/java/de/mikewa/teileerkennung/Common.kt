package de.mikewa.teileerkennung

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFD01012),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDAD5),
            secondary = Color(0xFF006CB7),
            tertiary = Color(0xFFF2CD37),
        ),
        content = content,
    )
}

tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Bild aus dem Netz mit grauem Platzhalter. */
@Composable
fun ImageBox(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F1F1)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Filled.Image, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
fun SetRow(set: LegoSet, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ImageBox(set.imgUrl, Modifier.size(72.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                set.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                set.details(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (set.partQty > 0 && set.qtyDetail != null && set.qtyDetail.contains(",")) {
                Text(
                    set.qtyDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (set.partQty > 0) {
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${set.partQty}×",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        trailing()
    }
}

/** Großansicht eines Sets mit Knopf zum Aufnehmen in die Sammlung. */
@Composable
fun SetDialog(
    set: LegoSet,
    inCollection: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(set.name, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageBox(set.imgUrl, Modifier.fillMaxWidth().aspectRatio(1.3f))
                Text("Set ${set.details()}")
                if (set.partQty > 0) {
                    Text(
                        "Gescanntes Teil: ${set.partQty}× in diesem Set" +
                            (set.qtyDetail?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                set.url?.let { url ->
                    OutlinedButton(onClick = { uriHandler.openUri(url) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (url.contains("bricklink")) "Auf BrickLink ansehen" else "Auf Rebrickable ansehen")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onToggle) {
                Text(if (inCollection) "Aus Sammlung entfernen" else "In meine Sammlung")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } },
    )
}

/** "6020-1 · 1984 · 330 Teile" – fehlende Angaben werden weggelassen. */
fun LegoSet.details(): String = buildList {
    add(setNum)
    if (year > 0) add(year.toString())
    if (numParts > 0) add("$numParts Teile")
}.joinToString(" · ")

@Composable
fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
