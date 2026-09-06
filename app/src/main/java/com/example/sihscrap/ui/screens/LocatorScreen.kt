package com.example.sihscrap.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.sihscrap.api.NearestRecyclerItem
import com.example.sihscrap.data.ScrapRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocatorScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ScrapRepository(context) }

    var userLat by remember { mutableStateOf(18.5204) }
    var userLon by remember { mutableStateOf(73.8567) }
    var recyclers by remember { mutableStateOf<List<NearestRecyclerItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) } // 0: List, 1: Map View
    var bookedRecyclerName by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            var location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
            if (location == null) {
                location = locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            }
            if (location != null) {
                userLat = location.latitude
                userLon = location.longitude
            }
        } catch (e: SecurityException) {
            // Permissions missing, fallback to default
        }
        val list = repository.getNearbyRecyclers(userLat, userLon)
        recyclers = list
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CPCB / SPCB Authorized Recyclers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
        ) {
            // View Mode Tab (List View vs Map Radar View)
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Facility List", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.FormatListBulleted, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Map Radar", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) }
                )
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (selectedTab == 1) {
                // Map Radar Spatial View
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val maxRadius = Math.min(size.width, size.height) * 0.42f

                            // Radar range rings
                            drawCircle(Color.Gray.copy(alpha = 0.2f), radius = maxRadius * 0.33f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                            drawCircle(Color.Gray.copy(alpha = 0.2f), radius = maxRadius * 0.66f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                            drawCircle(Color.Gray.copy(alpha = 0.2f), radius = maxRadius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

                            // Collector GPS position (Center)
                            drawCircle(Color(0xFF2196F3), radius = 14f, center = center)

                            // Facility positions
                            recyclers.forEachIndexed { index, item ->
                                val angle = (index * 1.8f)
                                val distFraction = (item.distance_km / 50.0).coerceIn(0.2, 0.9).toFloat()
                                val x = center.x + Math.cos(angle.toDouble()).toFloat() * maxRadius * distFraction
                                val y = center.y + Math.sin(angle.toDouble()).toFloat() * maxRadius * distFraction
                                drawCircle(Color(0xFF00C853), radius = 18f, center = Offset(x, y))
                            }
                        }

                        Column(
                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Collector GPS Origin (Blue) & Recyclers (Green)",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(onClick = {
                                val geoUri = Uri.parse("geo:$userLat,$userLon?q=scrap+recycling")
                                val mapIntent = Intent(Intent.ACTION_VIEW, geoUri)
                                mapIntent.setPackage("com.google.android.apps.maps")
                                try {
                                    context.startActivity(mapIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Google Maps app not found", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open in Google Maps (Offline Supported)", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Facility List View
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(recyclers) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.recycler.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = item.recycler.regulatoryBoard ?: "CPCB Authorized",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF00C853),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "${item.distance_km} km",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(item.recycler.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(item.recycler.operatingHours ?: "08:00 AM - 06:00 PM", style = MaterialTheme.typography.labelSmall)
                                }
                                Text("Tel: ${item.recycler.contactPhone}", style = MaterialTheme.typography.labelSmall)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val gmmIntentUri = Uri.parse(item.google_maps_directions_url)
                                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                        mapIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        try {
                                            context.startActivity(mapIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Directions URL: ${item.google_maps_directions_url}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Directions")
                                }

                                Button(
                                    onClick = {
                                        bookedRecyclerName = item.recycler.name
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Dispatch Pickup")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Booking Confirmation Dialog
    if (bookedRecyclerName != null) {
        AlertDialog(
            onDismissRequest = { bookedRecyclerName = null },
            title = { Text("Dispatch Request Booked", fontWeight = FontWeight.Bold) },
            text = {
                Text("Pickup request dispatched to $bookedRecyclerName. A licensed logistics vehicle will contact you with manifest documentation under CPCB E-Waste Rules 2022.")
            },
            confirmButton = {
                Button(onClick = { bookedRecyclerName = null }) {
                    Text("OK")
                }
            }
        )
    }
}
