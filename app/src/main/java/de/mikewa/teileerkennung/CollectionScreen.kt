package de.mikewa.teileerkennung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp

@Composable
fun CollectionScreen(vm: AppViewModel) {
    val entries = vm.visibleCollection
    var detail by remember { mutableStateOf<LegoSet?>(null) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Meine Sammlung (${entries.size})", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = { vm.sync() },
                enabled = !vm.syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.syncing) {
                    CircularProgressIndicator(
                        Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Filled.Sync, contentDescription = null)
                }
                Spacer(Modifier.width(8.dp))
                Text("Mit PC abgleichen (Rebrickable)")
            }
            vm.syncMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (entries.isEmpty()) {
            CenteredMessage("Noch keine Sets. Scanne ein Teil und tippe bei einem Set auf das Plus.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(entries, key = { it.set.setNum }) { e ->
                    SetRow(
                        set = e.set,
                        onClick = { detail = e.set },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!e.synced) {
                                    Text(
                                        "neu",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                IconButton(onClick = { vm.removeSet(e.set.setNum) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Entfernen")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    detail?.let { set ->
        SetDialog(
            set = set,
            inCollection = vm.isInCollection(set.setNum),
            onToggle = {
                if (vm.isInCollection(set.setNum)) vm.removeSet(set.setNum) else vm.addSet(set)
                detail = null
            },
            onDismiss = { detail = null },
        )
    }
}
