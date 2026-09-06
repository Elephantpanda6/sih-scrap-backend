package com.example.sihscrap.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.sihscrap.data.ScrapRepository
import com.example.sihscrap.data.local.MaterialCatalogEntity
import com.example.sihscrap.data.security.AntiDoubleSpendEngine
import com.example.sihscrap.hardware.BleScaleService
import com.example.sihscrap.hardware.HapticHoldToConfirmButton
import com.example.sihscrap.voice.VoiceEngine
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValuationScreen(
    navController: NavController,
    voiceEngine: VoiceEngine,
    initialCode: String = "copper_bare_bright",
    initialRust: Float = 0f,
    initialWeight: Double = 5.0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ScrapRepository(context) }
    val bleScaleService = remember { BleScaleService(context) }

    var catalog by remember { mutableStateOf<List<MaterialCatalogEntity>>(emptyList()) }
    var selectedCode by remember { mutableStateOf(initialCode) }
    var weightInput by remember { mutableStateOf(initialWeight.toString()) }
    var rustPercentage by remember { mutableStateOf(initialRust) }
    var isSubmitted by remember { mutableStateOf(false) }
    var generatedReceiptHash by remember { mutableStateOf<String?>(null) }

    val scaleReading by bleScaleService.readingFlow.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            bleScaleService.stopListening()
        }
    }

    LaunchedEffect(Unit) {
        val list = repository.getCatalog()
        catalog = list
        repository.refreshCatalogFromBackend()
    }

    val selectedMaterial = catalog.firstOrNull { it.code == selectedCode }
        ?: MaterialCatalogEntity(
            "copper_bare_bright", "Copper Bare Bright", "तांबा", "तांबे",
            "non_ferrous", 695.0, 5.0, 0.99, 4.5, "EMERALD_GREEN"
        )

    val currentWeight = weightInput.toDoubleOrNull() ?: 0.0
    val spotRate = selectedMaterial.spotRatePerKg
    val eprBonusPerKg = selectedMaterial.eprSubsidyBonusPerKg
    val purity = selectedMaterial.defaultPurity

    // Rust deduction: max 20% penalty
    val rustDeductionPct = (rustPercentage / 100.0) * 0.20
    val grossValue = currentWeight * spotRate * purity
    val rustDeductionAmount = grossValue * rustDeductionPct
    val eprTotalBonus = currentWeight * eprBonusPerKg
    val finalPayout = Math.max(0.0, grossValue - rustDeductionAmount + eprTotalBonus)
    val co2Saved = currentWeight * selectedMaterial.co2SavingPerKg

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scrap Valuation", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            voiceEngine.speakValuation(currentWeight, selectedMaterial.name, finalPayout)
                        }
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Read Valuation", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Material Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Selected Material Grade", style = MaterialTheme.typography.labelMedium)
                    Text(selectedMaterial.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Base Rate: ₹${spotRate.toInt()}/kg", fontWeight = FontWeight.SemiBold)
                        Surface(color = Color(0xFF00C853), shape = RoundedCornerShape(8.dp)) {
                            Text(
                                "+₹${eprBonusPerKg.toInt()}/kg EPR Subsidy",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // BLE Hardware Scale Integration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Scale, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("BLE Hardware Scale", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                bleScaleService.startListening()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.BluetoothSearching, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Live Scale")
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Scale Reading: ${scaleReading.weightKg} kg",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (scaleReading.isStable) Color(0xFF00C853) else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                if (scaleReading.isStable) "Reading Stabilized & Locked" else if (scaleReading.isConnected) "Settling weight..." else "Idle (Press Live Scale to Read)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        if (scaleReading.weightKg > 0.0) {
                            FilledTonalButton(
                                onClick = {
                                    weightInput = String.format(Locale.US, "%.2f", scaleReading.weightKg)
                                }
                            ) {
                                Text("Apply Weight")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Zero-Literacy Large Keypad Weight Input
            Text("Weight Input (kg)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = weightInput,
                onValueChange = { weightInput = it },
                label = { Text("Weight (in kg)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Incremental Keypad Buttons (min 48dp target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0.5, 1.0, 5.0, 10.0).forEach { inc ->
                    OutlinedButton(
                        onClick = {
                            val w = (weightInput.toDoubleOrNull() ?: 0.0) + inc
                            weightInput = String.format(Locale.US, "%.1f", w)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("+$inc", fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = { weightInput = "0" },
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("C", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time Valuation Breakdown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Valuation Breakdown", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gross Value (${currentWeight}kg × ₹${spotRate.toInt()}):", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("₹${String.format(Locale.US, "%.2f", grossValue)}", fontWeight = FontWeight.SemiBold)
                    }

                    if (rustDeductionAmount > 0.0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Rust Penalty (${rustPercentage.toInt()}%):", color = Color(0xFFD50000))
                            Text("-₹${String.format(Locale.US, "%.2f", rustDeductionAmount)}", color = Color(0xFFD50000), fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("CPCB EPR Subsidy (+₹${eprBonusPerKg.toInt()}/kg):", color = Color(0xFF00C853))
                        Text("+₹${String.format(Locale.US, "%.2f", eprTotalBonus)}", color = Color(0xFF00C853), fontWeight = FontWeight.Bold)
                    }

                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Net Payable Amount:", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "₹${String.format(Locale.US, "%.2f", finalPayout)}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "🌱 Environmental Impact: ${String.format(Locale.US, "%.1f", co2Saved)} kg CO2 abated",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Anti-Accidental 2-Second Haptic Hold Button
            HapticHoldToConfirmButton(
                text = "Hold 2s to Submit Valuation",
                onConfirmed = {
                    scope.launch {
                        val tx = repository.createSubmission(
                            materialCode = selectedMaterial.code,
                            weightKg = currentWeight,
                            rustPercentage = rustPercentage,
                            latitude = 18.5204,
                            longitude = 73.8567,
                            voiceTranscript = "Valuation calculated for ${selectedMaterial.name}"
                        )
                        generatedReceiptHash = tx.receiptHash
                        isSubmitted = true
                        voiceEngine.speak("Valuation confirmed. Receipt generated.")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Submission Confirmation Dialog
    if (isSubmitted && generatedReceiptHash != null) {
        val shortId = AntiDoubleSpendEngine.getHumanReadableReceiptId(generatedReceiptHash!!)
        AlertDialog(
            onDismissRequest = { isSubmitted = false },
            icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(48.dp)) },
            title = { Text("Submission Confirmed", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Receipt Hash: $shortId", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Total Payout: ₹${String.format(Locale.US, "%.2f", finalPayout)}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Queued in offline Room database. WorkManager 2G/EDGE worker will sync immediately upon connection.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSubmitted = false
                        navController.navigate("history")
                    }
                ) {
                    Text("View in Ledger")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isSubmitted = false
                        navController.navigate("dashboard")
                    }
                ) {
                    Text("Dashboard")
                }
            }
        )
    }
}
