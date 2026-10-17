package de.mikewa.teileerkennung

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var apiKey by remember { mutableStateOf(vm.apiKey) }
    var user by remember { mutableStateOf(vm.username) }
    var password by remember { mutableStateOf("") }
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Einstellungen", style = MaterialTheme.typography.titleLarge)

        Text("1. Rebrickable-API-Key", style = MaterialTheme.typography.titleSmall)
        Text(
            "Für die Set-Suche nötig. Kostenlos nach der Anmeldung auf rebrickable.com unter " +
                "Einstellungen → API zu finden.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API-Key") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { vm.saveApiKey(apiKey) }, modifier = Modifier.fillMaxWidth()) {
            Text("API-Key speichern")
        }
        OutlinedButton(
            onClick = { uriHandler.openUri("https://rebrickable.com/api/") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Rebrickable-API-Seite öffnen") }

        Spacer(Modifier.height(12.dp))
        Text("2. Abgleich mit dem PC", style = MaterialTheme.typography.titleSmall)
        Text(
            "Die Sammlung wird mit deinem Rebrickable-Konto abgeglichen. Am PC verwaltest du sie " +
                "auf rebrickable.com. Das Passwort wird nur für die einmalige Anmeldung benutzt und nicht gespeichert.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (vm.loggedIn) {
            Text("Angemeldet als ${vm.username}", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) {
                Text("Abmelden")
            }
        } else {
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Rebrickable-Benutzername") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.login(user, password)
                    password = ""
                },
                enabled = !vm.loginRunning,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.loginRunning) "Anmelden …" else "Anmelden") }
        }

        vm.settingsMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Die Teileerkennung läuft über den kostenlosen Dienst Brickognize. " +
                "Dafür wird das aufgenommene Foto an dessen Server gesendet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
