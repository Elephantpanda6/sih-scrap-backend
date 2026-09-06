package com.example.sihscrap.ui.screens

import android.graphics.Color as AndroidColor
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.sihscrap.ai.ClassificationResult
import com.example.sihscrap.ai.MaterialTier
import com.example.sihscrap.ai.ScrapClassifier
import com.example.sihscrap.ai.ThrottledImageAnalyzer
import java.util.concurrent.Executors

data class CameraDeviceInfo(
    val id: String,
    val label: String,
    val cameraSelector: CameraSelector
)

@Composable
fun CameraScreen(navController: NavController) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val classifier = remember { ScrapClassifier(context) }
    var currentResult by remember {
        mutableStateOf(
            ClassificationResult(
                categoryCode = "copper_bare_bright",
                categoryName = "Copper Bare Bright",
                confidence = 0.95f,
                rustPercentage = 12.0f,
                materialTier = MaterialTier.EMERALD_GREEN,
                priceDeductionPercentage = 0.024f
            )
        )
    }

    var isShutterLocked by remember { mutableStateOf(false) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val cameraOptions = remember { mutableStateListOf<CameraDeviceInfo>() }
    var selectedCameraOption by remember { mutableStateOf<CameraDeviceInfo?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            classifier.close()
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(selectedCameraOption, cameraProvider, previewViewRef) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val pView = previewViewRef ?: return@LaunchedEffect
        val option = selectedCameraOption ?: return@LaunchedEffect

        try {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(pView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(
                        cameraExecutor,
                        ThrottledImageAnalyzer(classifier) { result ->
                            if (!isShutterLocked) {
                                currentResult = result
                            }
                        }
                    )
                }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                option.cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // CameraX Preview + Throttled ImageAnalysis
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                previewViewRef = previewView

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    cameraProvider = provider

                    val discovered = mutableListOf<CameraDeviceInfo>()
                    try {
                        provider.availableCameraInfos.forEachIndexed { index, info ->
                            val lensFacing = try { info.lensFacing } catch (e: Exception) { -1 }
                            val label = when (lensFacing) {
                                CameraSelector.LENS_FACING_BACK -> if (index == 0) "Back Camera (Rear)" else "Back Camera #$index"
                                CameraSelector.LENS_FACING_FRONT -> if (index == 1 || index == 0) "Front Camera (Selfie)" else "Front Camera #$index"
                                CameraSelector.LENS_FACING_EXTERNAL -> "External Camera #$index"
                                else -> "Camera #$index"
                            }
                            val selector = try {
                                info.cameraSelector
                            } catch (e: Throwable) {
                                when (lensFacing) {
                                    CameraSelector.LENS_FACING_FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
                                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                                }
                            }
                            discovered.add(CameraDeviceInfo(id = "cam_$index", label = label, cameraSelector = selector))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    if (discovered.isEmpty()) {
                        if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                            discovered.add(CameraDeviceInfo("back", "Back Camera (Rear)", CameraSelector.DEFAULT_BACK_CAMERA))
                        }
                        if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                            discovered.add(CameraDeviceInfo("front", "Front Camera (Selfie)", CameraSelector.DEFAULT_FRONT_CAMERA))
                        }
                    }
                    if (discovered.isEmpty()) {
                        discovered.add(CameraDeviceInfo("default", "Default Camera", CameraSelector.DEFAULT_BACK_CAMERA))
                    }

                    cameraOptions.clear()
                    cameraOptions.addAll(discovered)

                    if (selectedCameraOption == null) {
                        selectedCameraOption = discovered.firstOrNull { it.label.contains("Back", ignoreCase = true) }
                            ?: discovered.firstOrNull()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // HUD High-Contrast Targeting Box
        val tierColor = Color(currentResult.materialTier.colorHex)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val boxLeft = width * 0.15f
            val boxTop = height * 0.25f
            val boxWidth = width * 0.70f
            val boxHeight = height * 0.40f

            // Bounding box border
            drawRect(
                color = tierColor,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxWidth, boxHeight),
                style = Stroke(width = 6f)
            )

            // Corner brackets
            val bracketLen = 36f
            val bracketStroke = 12f
            // Top-Left
            drawLine(tierColor, Offset(boxLeft, boxTop), Offset(boxLeft + bracketLen, boxTop), strokeWidth = bracketStroke)
            drawLine(tierColor, Offset(boxLeft, boxTop), Offset(boxLeft, boxTop + bracketLen), strokeWidth = bracketStroke)
            // Top-Right
            drawLine(tierColor, Offset(boxLeft + boxWidth, boxTop), Offset(boxLeft + boxWidth - bracketLen, boxTop), strokeWidth = bracketStroke)
            drawLine(tierColor, Offset(boxLeft + boxWidth, boxTop), Offset(boxLeft + boxWidth, boxTop + bracketLen), strokeWidth = bracketStroke)
            // Bottom-Left
            drawLine(tierColor, Offset(boxLeft, boxTop + boxHeight), Offset(boxLeft + bracketLen, boxTop + boxHeight), strokeWidth = bracketStroke)
            drawLine(tierColor, Offset(boxLeft, boxTop + boxHeight), Offset(boxLeft, boxTop + boxHeight - bracketLen), strokeWidth = bracketStroke)
            // Bottom-Right
            drawLine(tierColor, Offset(boxLeft + boxWidth, boxTop + boxHeight), Offset(boxLeft + boxWidth - bracketLen, boxTop + boxHeight), strokeWidth = bracketStroke)
            drawLine(tierColor, Offset(boxLeft + boxWidth, boxTop + boxHeight), Offset(boxLeft + boxWidth, boxTop + boxHeight - bracketLen), strokeWidth = bracketStroke)
        }

        // Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            // Camera Selection Dropdown Menu
            Box {
                Surface(
                    onClick = { isDropdownExpanded = !isDropdownExpanded },
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Camera,
                            contentDescription = "Select Camera",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = selectedCameraOption?.label ?: "Select Camera",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown Menu",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .widthIn(min = 200.dp)
                ) {
                    if (cameraOptions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Detecting cameras...", fontSize = 13.sp) },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        cameraOptions.forEach { option ->
                            val isSelected = option.id == selectedCameraOption?.id
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    selectedCameraOption = option
                                    isDropdownExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Camera,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                trailingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }

            // Material Tier Badge
            Surface(
                color = tierColor,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = currentResult.materialTier.displayName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        // Bottom Inspection & Classification Overlay Card
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = currentResult.categoryName,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Confidence: ${(currentResult.confidence * 100).toInt()}% | Throttled Edge Engine",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isShutterLocked) {
                            Surface(color = Color(0xFF00C853), shape = CircleShape) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(6.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Rust / Oxidation Percentage Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rust / Oxidation: ${currentResult.rustPercentage.toInt()}%",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "-${(currentResult.priceDeductionPercentage * 100).toInt()}% Price Penalty",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (currentResult.rustPercentage > 20f) Color(0xFFD50000) else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = (currentResult.rustPercentage / 100f).coerceIn(0f, 1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp),
                        color = if (currentResult.rustPercentage > 25f) Color(0xFFD50000) else Color(0xFFFFB300),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Single-Tap Capture & Lock Button
                    Button(
                        onClick = {
                            isShutterLocked = true
                            navController.navigate(
                                "calculator?code=${currentResult.categoryCode}&rust=${currentResult.rustPercentage}&name=${currentResult.categoryName}"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tierColor)
                    ) {
                        Icon(Icons.Default.Camera, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Lock & Valuate Scrap",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
