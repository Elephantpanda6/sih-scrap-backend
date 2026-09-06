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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.sihscrap.api.NearestRecyclerItem
import com.example.sihscrap.data.ScrapRepository
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

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
                        AndroidView(
                            factory = { ctx ->
                                // Ensure OSMDroid configuration is loaded synchronously before MapView creation
                                Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
                                Configuration.getInstance().userAgentValue = "SihScrapApp/1.0"

                                MapView(ctx).apply {
                                    setTileSource(TileSourceFactory.MAPNIK)
                                    setMultiTouchControls(true)
                                    controller.setZoom(13.0)
                                    controller.setCenter(GeoPoint(userLat, userLon))
                                    
                                    val userMarker = Marker(this)
                                    userMarker.position = GeoPoint(userLat, userLon)
                                    userMarker.title = "Your Location"
                                    userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    overlays.add(userMarker)

                                    recyclers.forEach { item ->
                                        val marker = Marker(this)
                                        marker.position = GeoPoint(item.recycler.latitude, item.recycler.longitude)
                                        marker.title = item.recycler.name
                                        marker.snippet = "${item.distance_km} km away"
                                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        overlays.add(marker)
                                    }
                                }
                            },
                            update = { view ->
                                view.controller.setCenter(GeoPoint(userLat, userLon))
                            },
                            modifier = Modifier.fillMaxSize()
                        )
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
