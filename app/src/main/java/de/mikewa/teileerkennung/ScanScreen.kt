package de.mikewa.teileerkennung

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

@Composable
fun ScanScreen(vm: AppViewModel) {
    if (vm.photo == null) {
        CameraSection(vm)
    } else {
        ResultSection(vm)
    }
}

// ------------------------------------------------------------------ Kamera

@Composable
private fun CameraSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Die App braucht Zugriff auf die Kamera, um Teile zu scannen.", textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Kamera erlauben") }
        }
        return
    }

    var cameraError by remember { mutableStateOf<String?>(null) }
    CameraPreview(
        onCaptured = { vm.onPhoto(it) },
        onError = { cameraError = it },
    )
    cameraError?.let {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CameraPreview(onCaptured: (Bitmap) -> Unit, onError: (String) -> Unit) {
    val ctx = LocalContext.current
    val owner = remember(ctx) { ctx.findActivity() }
    val previewView = remember {
        PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by rememberSaveable { mutableStateOf(false) }
    val reportError: (String) -> Unit = onError

    DisposableEffect(owner) {
        val activity = owner
        if (activity == null) {
            onError("Kamera konnte nicht gestartet werden.")
            return@DisposableEffect onDispose { }
        }
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            try {
                val provider = future.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    activity,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                )
            } catch (e: Exception) {
                onError("Kamera konnte nicht gestartet werden: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            if (future.isDone) runCatching { future.get().unbindAll() }
            camera = null
        }
    }

    LaunchedEffect(camera, torch) {
        camera?.cameraControl?.enableTorch(torch)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Zielrahmen
        Box(
            Modifier
                .align(Alignment.Center)
                .size(280.dp)
                .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.8f)), RoundedCornerShape(20.dp)),
        )
        Text(
            "Teil in den Rahmen legen – am besten auf hellem, einfarbigem Untergrund",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        IconButton(
            onClick = { torch = !torch },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(28.dp)
                .size(52.dp)
                .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(
                if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Licht",
                tint = Color.White,
            )
        }

        // Auslöser
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .size(76.dp)
                .border(BorderStroke(4.dp, Color.White), CircleShape)
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable {
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(ctx),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val rotation = image.imageInfo.rotationDegrees
                                    val raw = image.toBitmap()
                                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                    val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                                    onCaptured(upright)
                                } catch (e: Exception) {
                                    reportError("Foto konnte nicht verarbeitet werden: ${e.message}")
                                } finally {
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                reportError("Aufnahme fehlgeschlagen: ${exception.message}")
                            }
                        },
                    )
                },
        )
    }
}

// ------------------------------------------------------------------ Ergebnis

@Composable
private fun ResultSection(vm: AppViewModel) {
    var highlight by remember(vm.candidates) { mutableIntStateOf(0) }
    val shown = vm.candidates.getOrNull(highlight)

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            // Vergleich: eigenes Foto neben dem Referenzbild des markierten Treffers
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CompareCard("Dein Foto", Modifier.weight(1f)) {
                    vm.photo?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Dein Foto",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                CompareCard(shown?.let { "Treffer: ${it.id}" } ?: "Vergleichsbild", Modifier.weight(1f)) {
                    ImageBox(shown?.imgUrl, Modifier.fillMaxSize())
                }
            }
        }

        if (vm.recognizing) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Teil wird erkannt …")
                }
            }
        }

        vm.scanError?.let { msg ->
            item {
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (vm.candidates.isNotEmpty()) {
            item {
                Text(
                    "Mögliche Teile – tippe eines an, um es mit deinem Foto zu vergleichen",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            itemsIndexed(vm.candidates, key = { i, c -> "${c.id}-$i" }) { index, c ->
                val selected = index == highlight
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { highlight = index },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ImageBox(c.imgUrl, Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                            Text(
                                "Nr. ${c.id} · Übereinstimmung ${(c.score * 100).roundToInt()} %",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { vm.clearScan() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            ) { Text("Neues Foto aufnehmen") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CompareCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF1F1F1)),
        ) { content() }
        Spacer(Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelMedium)
    }
}
