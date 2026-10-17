package de.mikewa.teileerkennung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val label: String, val icon: ImageVector) {
    Scan("Scannen", Icons.Filled.CameraAlt),
    Collection("Sammlung", Icons.Filled.Inventory2),
    Settings("Einstellungen", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme { Root(vm) }
        }
    }
}

@Composable
fun Root(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Scan) }

    BackHandler(enabled = tab == Tab.Scan && vm.partOpen) { vm.closePart() }
    BackHandler(enabled = tab == Tab.Scan && !vm.partOpen && vm.photo != null) { vm.clearScan() }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.Scan -> if (vm.partOpen) PartScreen(vm) else ScanScreen(vm)
                Tab.Collection -> CollectionScreen(vm)
                Tab.Settings -> SettingsScreen(vm)
            }
        }
    }
}
