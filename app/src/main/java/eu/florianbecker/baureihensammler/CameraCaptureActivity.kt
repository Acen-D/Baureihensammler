package eu.florianbecker.baureihensammler

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.Manifest
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.canhub.cropper.CropImageView
import eu.florianbecker.baureihensammler.data.TrainSeriesOrigin
import eu.florianbecker.baureihensammler.ui.theme.BaureihensammlerTheme
import eu.florianbecker.baureihensammler.util.DebugLogStore
import java.io.File
import java.io.FileOutputStream

class CameraCaptureActivity : ComponentActivity() {

    private lateinit var baureihe: String
    private lateinit var origin: TrainSeriesOrigin
    private var captureSource: CaptureSource = CaptureSource.Camera

    private var captureUri: Uri? = null

    private val phase = mutableStateOf(Phase.RequestingPermission)
    private val cameraSessionId = mutableIntStateOf(0)
    private val galleryPickSessionId = mutableIntStateOf(0)

    private val pickVisualMedia =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                setResult(RESULT_CANCELED)
                finish()
            } else {
                captureUri = uri
                phase.value = Phase.Cropping
            }
        }

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (!success) {
                DebugLogStore.logError(
                    context = this,
                    source = "CameraCaptureActivity.TakePicture",
                    message = "TakePicture wurde abgebrochen oder lieferte kein Bild."
                )
                setResult(RESULT_CANCELED)
                finish()
            } else {
                phase.value = Phase.Cropping
            }
        }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                phase.value = Phase.LaunchingCamera
            } else {
                DebugLogStore.logError(
                    context = this,
                    source = "CameraCaptureActivity.RequestPermission",
                    message = "CAMERA-Berechtigung wurde abgelehnt."
                )
                Toast.makeText(
                    this,
                    "Kamera-Berechtigung fehlt. Bitte in den App-Einstellungen aktivieren.",
                    Toast.LENGTH_LONG
                ).show()
                setResult(RESULT_CANCELED)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            baureihe = intent.getStringExtra(EXTRA_BAUREIHE) ?: ""
            origin = TrainSeriesOrigin.fromName(intent.getStringExtra(EXTRA_ORIGIN))
            captureSource =
                when (intent.getStringExtra(EXTRA_CAPTURE_SOURCE)) {
                    CAPTURE_SOURCE_GALLERY -> CaptureSource.Gallery
                    else -> CaptureSource.Camera
                }
            phase.value =
                when (captureSource) {
                    CaptureSource.Gallery -> Phase.LaunchingGalleryPicker
                    CaptureSource.Camera -> Phase.RequestingPermission
                }
        } catch (t: Throwable) {
            DebugLogStore.logError(
                context = this,
                source = "CameraCaptureActivity.onCreate",
                message = "Intent-Extras konnten nicht gelesen werden.",
                throwable = t
            )
            Toast.makeText(this, "Kamera konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        hideSystemBars()

        setContent {
            BaureihensammlerTheme {
                val currentPhase by phase
                val cameraSession by cameraSessionId
                val gallerySession by galleryPickSessionId
                when (currentPhase) {
                    Phase.RequestingPermission -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                        LaunchedEffect(Unit) {
                            if (hasCameraPermission()) {
                                phase.value = Phase.LaunchingCamera
                            } else {
                                requestCameraPermission.launch(Manifest.permission.CAMERA)
                            }
                        }
                    }
                    Phase.LaunchingCamera -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                        LaunchedEffect(cameraSession) {
                            try {
                                val uri = prepareCaptureUri()
                                takePicture.launch(uri)
                            } catch (t: Throwable) {
                                DebugLogStore.logError(
                                    context = this@CameraCaptureActivity,
                                    source = "CameraCaptureActivity.launchCamera",
                                    message = "Kamera-Start fehlgeschlagen (URI/FileProvider).",
                                    throwable = t
                                )
                                Toast.makeText(
                                    this@CameraCaptureActivity,
                                    "Kamera konnte nicht gestartet werden",
                                    Toast.LENGTH_SHORT
                                ).show()
                                setResult(RESULT_CANCELED)
                                finish()
                            }
                        }
                    }
                    Phase.LaunchingGalleryPicker -> {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                        LaunchedEffect(gallerySession) {
                            pickVisualMedia.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    }
                    Phase.Cropping -> {
                        val uri = captureUri
                        if (uri != null) {
                            CropReviewScreen(
                                imageUri = uri,
                                baureihe = baureihe,
                                gallerySource = captureSource == CaptureSource.Gallery,
                                onClose = {
                                    setResult(RESULT_CANCELED)
                                    finish()
                                },
                                onRetake = {
                                    if (captureSource == CaptureSource.Gallery) {
                                        phase.value = Phase.LaunchingGalleryPicker
                                        galleryPickSessionId.intValue++
                                    } else {
                                        phase.value = Phase.LaunchingCamera
                                        cameraSessionId.intValue++
                                    }
                                },
                                onCroppedBitmap = { bitmap -> saveCroppedAndFinish(bitmap) }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
        showSystemBars()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        // AI says this is needed for backwards compatibility but I don't know if it even applied to the system requirements anyway...
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }

    private fun prepareCaptureUri(): Uri {
        val dir = File(cacheDir, "camera_capture").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri =
            FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
        captureUri = uri
        return uri
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun saveCroppedAndFinish(bitmap: Bitmap) {
        val snapshotsDir = File(filesDir, "snapshots").apply { mkdirs() }
        val outFile = File(snapshotsDir, "${baureihe}_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
        } catch (t: Exception) {
            DebugLogStore.logError(
                context = this,
                source = "CameraCaptureActivity.saveCroppedAndFinish",
                message = "Speichern des zugeschnittenen Bildes fehlgeschlagen.",
                throwable = t
            )
            Toast.makeText(this, "Speichern fehlgeschlagen", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_BAUREIHE, baureihe)
                .putExtra(EXTRA_ORIGIN, origin.name)
                .putExtra(EXTRA_IMAGE_PATH, outFile.absolutePath)
        )
        finish()
    }

    companion object {
        const val EXTRA_BAUREIHE = "extra_baureihe"
        const val EXTRA_IMAGE_PATH = "extra_image_path"
        const val EXTRA_ORIGIN = "extra_origin"
        const val EXTRA_CAPTURE_SOURCE = "extra_capture_source"
        const val CAPTURE_SOURCE_CAMERA = "camera"
        const val CAPTURE_SOURCE_GALLERY = "gallery"

        fun createIntent(
            context: Context,
            baureihe: String,
            origin: TrainSeriesOrigin = TrainSeriesOrigin.DB,
            fromGallery: Boolean = false,
        ): Intent {
            return Intent(context, CameraCaptureActivity::class.java)
                .putExtra(EXTRA_BAUREIHE, baureihe)
                .putExtra(EXTRA_ORIGIN, origin.name)
                .putExtra(
                    EXTRA_CAPTURE_SOURCE,
                    if (fromGallery) CAPTURE_SOURCE_GALLERY else CAPTURE_SOURCE_CAMERA
                )
        }
    }
}

private enum class CaptureSource {
    Camera,
    Gallery,
}

private enum class Phase {
    RequestingPermission,
    LaunchingCamera,
    LaunchingGalleryPicker,
    Cropping,
}

@Composable
private fun CropReviewScreen(
    imageUri: Uri,
    baureihe: String,
    gallerySource: Boolean,
    onClose: () -> Unit,
    onRetake: () -> Unit,
    onCroppedBitmap: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val cropViewHolder = remember { mutableStateOf<CropImageView?>(null) }
    val imageReady = remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        key(imageUri) {
            AndroidView(
                factory = { ctx ->
                    CropImageView(ctx).apply {
                        guidelines = CropImageView.Guidelines.ON
                        setAspectRatio(16, 9)
                        setFixedAspectRatio(true)
                        setOnSetImageUriCompleteListener { _, _, error ->
                            imageReady.value = error == null
                            if (error != null) {
                                DebugLogStore.logError(
                                    context = ctx,
                                    source = "CropReviewScreen.setImageUriAsync",
                                    message = "Bild konnte nicht in CropImageView geladen werden.",
                                    throwable = error
                                )
                            }
                        }
                        setImageUriAsync(imageUri)
                        cropViewHolder.value = this
                    }
                },
                modifier = Modifier.fillMaxSize().padding(bottom = 96.dp)
            )
        }

        IconButton(
            onClick = onClose,
            modifier =
                Modifier.align(Alignment.TopStart)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                        )
                    )
                    .padding(8.dp)
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Abbrechen",
                tint = Color.White
            )
        }

        Text(
            "BR $baureihe — 16:9 zuschneiden",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Top
                        )
                    )
                    .padding(top = 8.dp)
        )

        Row(
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, Color.White),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    )
            ) {
                Text(if (gallerySource) "Anderes wählen" else "Neu aufnehmen")
            }
            Button(
                onClick = {
                    val view = cropViewHolder.value
                    if (view == null || !imageReady.value) {
                        Toast.makeText(context, "Bild wird noch geladen …", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    busy = true
                    val bmp =
                        try {
                            view.getCroppedImage(
                                4096,
                                2304,
                                CropImageView.RequestSizeOptions.RESIZE_INSIDE
                            )
                        } catch (t: Throwable) {
                            DebugLogStore.logError(
                                context = context,
                                source = "CropReviewScreen.getCroppedImage",
                                message = "Cropping ist mit Ausnahme fehlgeschlagen.",
                                throwable = t
                            )
                            null
                        }
                    busy = false
                    if (bmp != null) {
                        onCroppedBitmap(bmp)
                    } else {
                        Toast.makeText(context, "Zuschnitt fehlgeschlagen", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !busy && imageReady.value,
                modifier = Modifier.weight(1f)
            ) {
                Text("Übernehmen")
            }
        }
    }
}
