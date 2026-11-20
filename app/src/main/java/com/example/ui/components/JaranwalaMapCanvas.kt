package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.RideStatus
import com.example.data.VehicleCategory
import com.example.util.PakistanGeoService
import com.example.util.PakistanLocationPoint
import java.util.Locale
import kotlin.math.cos

enum class PinSelectionMode {
    PICKUP,
    DESTINATION
}

@Composable
fun JaranwalaMapCanvas(
    modifier: Modifier = Modifier,
    pickupText: String = "Committee Chowk",
    destinationText: String = "Jaranwala Railway Station",
    rideStatus: RideStatus = RideStatus.IDLE,
    category: VehicleCategory = VehicleCategory.RICKSHAW,
    activeRide: com.example.data.RideBooking? = null,
    onPickupChange: ((String) -> Unit)? = null,
    onDestinationChange: ((String) -> Unit)? = null,
    onSwitchToOpenStreetMap: (() -> Unit)? = null,
    onSwitchToGoogleMaps: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Coordinates state centered strictly on Jaranwala, Punjab, Pakistan (31.3333, 73.4333)
    var centerLat by remember { mutableDoubleStateOf(PakistanGeoService.JARANWALA_LAT) }
    var centerLng by remember { mutableDoubleStateOf(PakistanGeoService.JARANWALA_LNG) }
    var zoomLevel by remember { mutableFloatStateOf(1.0f) } // 1.0f = Jaranwala City, 0.4f = Punjab, 0.15f = All Pakistan, 2.5f = High Street Detail

    // Pin selection mode
    var activePinMode by remember { mutableStateOf(PinSelectionMode.DESTINATION) }

    // Search dialog state
    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // GPS location state
    var userGpsLocation by remember { mutableStateOf<PakistanLocationPoint?>(null) }
    var isGpsLoading by remember { mutableStateOf(false) }

    // Resolve active pickup & destination coordinates from Pakistan database
    val pickupPoint = remember(pickupText) {
        PakistanGeoService.PAKISTAN_LOCATIONS.find {
            it.name.contains(pickupText, true) || pickupText.contains(it.name, true)
        } ?: PakistanGeoService.PAKISTAN_LOCATIONS[0]
    }

    val destPoint = remember(destinationText) {
        PakistanGeoService.PAKISTAN_LOCATIONS.find {
            it.name.contains(destinationText, true) || destinationText.contains(it.name, true)
        } ?: PakistanGeoService.PAKISTAN_LOCATIONS[1]
    }

    // Direct distance calculation
    val calculatedDistKm = remember(pickupPoint, destPoint) {
        PakistanGeoService.calculateDistanceKm(
            pickupPoint.latitude, pickupPoint.longitude,
            destPoint.latitude, destPoint.longitude
        )
    }

    // Permission launcher for GPS Location
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            isGpsLoading = true
            PakistanGeoService.fetchCurrentGpsLocation(
                context = context,
                onSuccess = { loc ->
                    isGpsLoading = false
                    userGpsLocation = loc
                    // Safe guard: center on GPS if in Pakistan, else enforce Jaranwala
                    if (PakistanGeoService.isWithinPakistan(loc.latitude, loc.longitude)) {
                        centerLat = loc.latitude
                        centerLng = loc.longitude
                        zoomLevel = 1.6f
                        onPickupChange?.invoke(loc.name)
                        Toast.makeText(context, "GPS Acquired: ${loc.name}", Toast.LENGTH_SHORT).show()
                    } else {
                        centerLat = PakistanGeoService.JARANWALA_LAT
                        centerLng = PakistanGeoService.JARANWALA_LNG
                        Toast.makeText(context, "Centered on Jaranwala, Punjab", Toast.LENGTH_SHORT).show()
                    }
                },
                onError = { err ->
                    isGpsLoading = false
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, "Permission needed for GPS location. Showing Jaranwala, Pakistan.", Toast.LENGTH_LONG).show()
        }
    }

    // Infinite pulse animations for pins and moving vehicle
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val vehicleProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "vehicleProgress"
    )

    val isRideActive = rideStatus == RideStatus.ACCEPTED ||
            rideStatus == RideStatus.DRIVER_ARRIVED ||
            rideStatus == RideStatus.IN_PROGRESS

    // Calculate real-time dynamic driver coordinates in Jaranwala
    val (driverCurrentLat, driverCurrentLng) = remember(
        rideStatus, vehicleProgress, pickupPoint.latitude, pickupPoint.longitude, destPoint.latitude, destPoint.longitude
    ) {
        when (rideStatus) {
            RideStatus.ACCEPTED -> {
                // Driver approaching pickup point from nearby in Jaranwala
                val startLat = pickupPoint.latitude + 0.0072
                val startLng = pickupPoint.longitude - 0.0065
                val progress = (vehicleProgress * 1.2f).coerceIn(0f, 1f)
                val lat = (1 - progress) * startLat + progress * pickupPoint.latitude
                val lng = (1 - progress) * startLng + progress * pickupPoint.longitude
                lat to lng
            }
            RideStatus.DRIVER_ARRIVED -> {
                pickupPoint.latitude to pickupPoint.longitude
            }
            RideStatus.IN_PROGRESS -> {
                val progress = vehicleProgress
                val midLat = (pickupPoint.latitude + destPoint.latitude) / 2.0 + 0.003
                val midLng = (pickupPoint.longitude + destPoint.longitude) / 2.0 - 0.002
                val lat = (1 - progress) * (1 - progress) * pickupPoint.latitude + 2 * (1 - progress) * progress * midLat + progress * progress * destPoint.latitude
                val lng = (1 - progress) * (1 - progress) * pickupPoint.longitude + 2 * (1 - progress) * progress * midLng + progress * progress * destPoint.longitude
                lat to lng
            }
            else -> pickupPoint.latitude to pickupPoint.longitude
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
            .testTag("jaranwala_map_canvas"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1914))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Interactive Pakistan Location Search & Navigation Control Bar
            Surface(
                color = Color(0xFF06231A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search Location in Pakistan Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showSearchDialog = true }
                            .testTag("map_search_box_trigger"),
                        color = Color(0xFF0F3B2C),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Pakistan",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Search Jaranwala & Pakistan...",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Switch to OpenStreetMap (or Google Maps callback)
                    val switchMapAction = onSwitchToOpenStreetMap ?: onSwitchToGoogleMaps
                    if (switchMapAction != null) {
                        ElevatedButton(
                            onClick = switchMapAction,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFF0F3B2C),
                                contentColor = Color(0xFF34D399)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("canvas_switch_to_osm_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Map,
                                contentDescription = "OpenStreetMap",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("اوپن اسٹریٹ میپ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Open Real Google Maps Navigation
                    ElevatedButton(
                        onClick = {
                            PakistanGeoService.launchGoogleMapsNavigation(
                                context = context,
                                destination = destinationText,
                                destLat = destPoint.latitude,
                                destLng = destPoint.longitude
                            )
                        },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = Color(0xFFF59E0B),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("open_google_maps_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Google Maps",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Google Maps", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Interactive Map Viewport (Supports Drag / Pan, Pinch Zoom, Tap-to-Place Pin)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Color(0xFF081511))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Scale factor to map lat/long per pixel
                            val latPerPx = 0.00015 / zoomLevel
                            val lngPerPx = 0.00015 / (zoomLevel * cos(Math.toRadians(centerLat)).coerceAtLeast(0.5))

                            // Pan camera
                            centerLat = (centerLat + (pan.y * latPerPx)).coerceIn(
                                PakistanGeoService.PAKISTAN_MIN_LAT,
                                PakistanGeoService.PAKISTAN_MAX_LAT
                            )
                            centerLng = (centerLng - (pan.x * lngPerPx)).coerceIn(
                                PakistanGeoService.PAKISTAN_MIN_LNG,
                                PakistanGeoService.PAKISTAN_MAX_LNG
                            )

                            // Zoom range: 0.12f (All Pakistan) to 3.5f (Detailed Jaranwala Streets)
                            zoomLevel = (zoomLevel * zoom).coerceIn(0.12f, 3.5f)
                        }
                    }
                    .pointerInput(activePinMode, centerLat, centerLng, zoomLevel) {
                        detectTapGestures { tapOffset ->
                            // Convert screen tap offset to approximate geo coordinate
                            val w = size.width
                            val h = size.height
                            val dx = tapOffset.x - (w / 2)
                            val dy = tapOffset.y - (h / 2)

                            val latPerPx = 0.00015 / zoomLevel
                            val lngPerPx = 0.00015 / (zoomLevel * cos(Math.toRadians(centerLat)).coerceAtLeast(0.5))

                            val tappedLat = centerLat - (dy * latPerPx)
                            val tappedLng = centerLng + (dx * lngPerPx)

                            val closest = PakistanGeoService.findClosestLocation(tappedLat, tappedLng)

                            if (activePinMode == PinSelectionMode.PICKUP) {
                                onPickupChange?.invoke(closest.name)
                                Toast.makeText(context, "Pickup set: ${closest.name}", Toast.LENGTH_SHORT).show()
                            } else {
                                onDestinationChange?.invoke(closest.name)
                                Toast.makeText(context, "Destination set: ${closest.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
            ) {
                // High-Fidelity Pakistan & Jaranwala Vector Map Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f

                    // Projection helper: lat/lng -> canvas x/y relative to center camera
                    fun project(lat: Double, lng: Double): Offset {
                        val latPerPx = 0.00015 / zoomLevel
                        val lngPerPx = 0.00015 / (zoomLevel * cos(Math.toRadians(centerLat)).coerceAtLeast(0.5))
                        val px = cx + ((lng - centerLng) / lngPerPx).toFloat()
                        val py = cy - ((lat - centerLat) / latPerPx).toFloat()
                        return Offset(px, py)
                    }

                    // 1. Draw Country / Regional Base Grid
                    drawGridBackground(w, h, zoomLevel)

                    // 2. Draw Major Waterways (Chenab, Ravi, Gugera Canal in Jaranwala)
                    drawPakistanRivers(w, h, ::project)

                    // 3. Draw Highway & Road Network (M-3 Motorway, Jaranwala Bypass, Faisalabad Rd, Satyana Rd, Syedwala Rd)
                    drawRoadNetwork(w, h, zoomLevel, ::project)

                    // 4. Render Pakistan Location Points & Nearby Villages
                    PakistanGeoService.PAKISTAN_LOCATIONS.forEach { loc ->
                        val pos = project(loc.latitude, loc.longitude)
                        if (pos.x in -50f..(w + 50f) && pos.y in -50f..(h + 50f)) {
                            drawLocationMarker(loc, pos, zoomLevel)
                        }
                    }

                    // 5. Draw Pickup (Green) & Destination (Gold) Pins and Dynamic Route
                    val startPos = project(pickupPoint.latitude, pickupPoint.longitude)
                    val endPos = project(destPoint.latitude, destPoint.longitude)

                    // Draw route path between pickup and destination
                    val routePath = Path().apply {
                        moveTo(startPos.x, startPos.y)
                        val midX = (startPos.x + endPos.x) / 2f + (25f * zoomLevel)
                        val midY = (startPos.y + endPos.y) / 2f - (15f * zoomLevel)
                        quadraticBezierTo(midX, midY, endPos.x, endPos.y)
                    }

                    drawPath(
                        path = routePath,
                        color = Color(0xFF10B981),
                        style = Stroke(
                            width = (4.5f * zoomLevel).coerceIn(3f, 8f).dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                        )
                    )

                    // Pickup Marker (Green)
                    drawCircle(
                        color = Color(0x3310B981),
                        radius = 18.dp.toPx() * pulseScale,
                        center = startPos
                    )
                    drawCircle(
                        color = Color(0xFF10B981),
                        radius = 8.dp.toPx(),
                        center = startPos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = startPos
                    )

                    // Destination Marker (Gold)
                    drawCircle(
                        color = Color(0x44F59E0B),
                        radius = 16.dp.toPx() * pulseScale,
                        center = endPos
                    )
                    drawCircle(
                        color = Color(0xFFF59E0B),
                        radius = 8.dp.toPx(),
                        center = endPos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = endPos
                    )

                    // User GPS location beacon if active
                    val gps = userGpsLocation
                    if (gps != null) {
                        val gpsPos = project(gps.latitude, gps.longitude)
                        drawCircle(
                            color = Color(0x443B82F6),
                            radius = 22.dp.toPx() * pulseScale,
                            center = gpsPos
                        )
                        drawCircle(
                            color = Color(0xFF3B82F6),
                            radius = 7.dp.toPx(),
                            center = gpsPos
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = gpsPos
                        )
                    }

                    // Real-Time Moving Driver Marker & Ride Vehicle along Route
                    val driverPos = if (isRideActive) {
                        project(driverCurrentLat, driverCurrentLng)
                    } else {
                        val t = if (rideStatus == RideStatus.SEARCHING) vehicleProgress else 0.5f
                        val midX = (startPos.x + endPos.x) / 2f + (25f * zoomLevel)
                        val midY = (startPos.y + endPos.y) / 2f - (15f * zoomLevel)
                        Offset(
                            x = (1 - t) * (1 - t) * startPos.x + 2 * (1 - t) * t * midX + t * t * endPos.x,
                            y = (1 - t) * (1 - t) * startPos.y + 2 * (1 - t) * t * midY + t * t * endPos.y
                        )
                    }

                    // Pulsing radar halo around Driver during active ride
                    if (isRideActive) {
                        drawCircle(
                            color = Color(0x3310B981),
                            radius = (22f * zoomLevel).coerceIn(16f, 36f).dp.toPx() * pulseScale,
                            center = driverPos
                        )
                        drawCircle(
                            color = Color(0x5510B981),
                            radius = (13f * zoomLevel).coerceIn(10f, 22f).dp.toPx() * pulseScale,
                            center = driverPos
                        )
                    }

                    drawCircle(
                        color = Color.White,
                        radius = 10.dp.toPx(),
                        center = driverPos
                    )
                    drawCircle(
                        color = when (category) {
                            VehicleCategory.BIKE -> Color(0xFF0284C7)
                            VehicleCategory.RICKSHAW -> Color(0xFFF59E0B)
                            VehicleCategory.CAR -> Color(0xFF10B981)
                        },
                        radius = 7.dp.toPx(),
                        center = driverPos
                    )

                    // Draw Driver Name Tag on Canvas
                    if (isRideActive) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 22f
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                setShadowLayer(6f, 0f, 2f, android.graphics.Color.BLACK)
                            }
                            val captainLabel = "★ Captain ${activeRide?.driverName?.take(10) ?: "Arshad"}"
                            drawText(captainLabel, driverPos.x + 12.dp.toPx(), driverPos.y + 6.dp.toPx(), paint)
                        }
                    }
                }

                // Top Left Overlay: Province / Camera Status Indicator
                Surface(
                    color = Color(0xDD000000),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (zoomLevel > 1.2f) "Jaranwala Streets • ۳۱.۳۳۳۳°"
                            else if (zoomLevel > 0.5f) "Faisalabad Div & Villages"
                            else "Pakistan National Map",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Top Right: Zoom & Reset Controls (+ / - / Center Jaranwala / GPS)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Zoom In Button
                    Surface(
                        color = Color(0xCC06231A),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { zoomLevel = (zoomLevel * 1.35f).coerceAtMost(3.5f) }
                            .testTag("map_zoom_in_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Zoom Out Button
                    Surface(
                        color = Color(0xCC06231A),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { zoomLevel = (zoomLevel / 1.35f).coerceAtLeast(0.12f) }
                            .testTag("map_zoom_out_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Recenter on Jaranwala Button (31.3333, 73.4333)
                    Surface(
                        color = Color(0xCC06231A),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable {
                                centerLat = PakistanGeoService.JARANWALA_LAT
                                centerLng = PakistanGeoService.JARANWALA_LNG
                                zoomLevel = 1.0f
                                Toast.makeText(context, "Centered on Jaranwala (31.3333, 73.4333)", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("recenter_jaranwala_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Center Jaranwala", tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        }
                    }

                    // Current GPS Location Button
                    Surface(
                        color = Color(0xCC06231A),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                            .testTag("my_gps_location_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MyLocation, contentDescription = "My GPS Location", tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                // Bottom Left: Tap mode selector (Drop Pickup vs Drop Destination)
                Surface(
                    color = Color(0xEE0B241B),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tap Map to Set: ", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        Surface(
                            color = if (activePinMode == PinSelectionMode.PICKUP) Color(0xFF10B981) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { activePinMode = PinSelectionMode.PICKUP }
                        ) {
                            Text(
                                text = "Pickup",
                                color = if (activePinMode == PinSelectionMode.PICKUP) Color.Black else Color(0xFF10B981),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            color = if (activePinMode == PinSelectionMode.DESTINATION) Color(0xFFF59E0B) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { activePinMode = PinSelectionMode.DESTINATION }
                        ) {
                            Text(
                                text = "Dropoff",
                                color = if (activePinMode == PinSelectionMode.DESTINATION) Color.Black else Color(0xFFF59E0B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Live Driver Tracking HUD Overlay Card (Active Booking)
                if (isRideActive) {
                    Surface(
                        color = Color(0xF2041A13),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .testTag("driver_live_tracking_hud")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF059669)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.NearMe,
                                    contentDescription = "Captain Tracking",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "★ ${activeRide?.driverName ?: "Captain Arshad"}",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = when (rideStatus) {
                                        RideStatus.ACCEPTED -> "On Way to Pickup"
                                        RideStatus.DRIVER_ARRIVED -> "At Pickup Point"
                                        RideStatus.IN_PROGRESS -> "En Route • Live GPS"
                                        else -> "Live Tracking"
                                    },
                                    color = Color(0xFF34D399),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Center on Captain button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981),
                                modifier = Modifier
                                    .clickable {
                                        centerLat = driverCurrentLat
                                        centerLng = driverCurrentLng
                                        zoomLevel = 1.6f
                                        Toast.makeText(context, "Centered on Captain", Toast.LENGTH_SHORT).show()
                                    }
                                    .testTag("btn_center_on_driver")
                            ) {
                                Text(
                                    text = "Center",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            // Maps SDK Turn-by-turn Navigation Button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B),
                                modifier = Modifier
                                    .clickable {
                                        PakistanGeoService.launchGoogleMapsNavigation(
                                            context = context,
                                            destination = destinationText,
                                            destLat = destPoint.latitude,
                                            destLng = destPoint.longitude
                                        )
                                    }
                                    .testTag("btn_maps_sdk_track")
                            ) {
                                Text(
                                    text = "Maps GPS",
                                    color = Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Information Bar: Active Route & Live Distance
            Surface(
                color = Color(0xFF041812),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(7.dp).background(Color(0xFF10B981), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "From: $pickupText",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(7.dp).background(Color(0xFFF59E0B), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "To: $destinationText",
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF063B2B),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${calculatedDistKm} km • Pakistan GPS",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Pakistan & Jaranwala Location Search Modal Dialog
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = {
                Text(
                    text = "Search Jaranwala & Pakistan Locations",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search city, town, village, chowk...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("map_search_input_field"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val searchResults = remember(searchQuery) {
                        PakistanGeoService.searchPakistan(searchQuery)
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(searchResults) { loc ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        centerLat = loc.latitude
                                        centerLng = loc.longitude
                                        zoomLevel = if (loc.category == "Jaranwala Hub") 1.8f else 0.8f

                                        if (activePinMode == PinSelectionMode.PICKUP) {
                                            onPickupChange?.invoke(loc.name)
                                        } else {
                                            onDestinationChange?.invoke(loc.name)
                                        }
                                        showSearchDialog = false
                                        Toast.makeText(context, "${loc.name} selected", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(loc.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(loc.urduName, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        if (loc.description.isNotBlank()) {
                                            Text(loc.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                    Surface(
                                        color = when (loc.category) {
                                            "Jaranwala Hub" -> Color(0xFF10B981)
                                            "Nearby Village" -> Color(0xFFF59E0B)
                                            else -> Color(0xFF3B82F6)
                                        }.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = loc.category,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (loc.category) {
                                                "Jaranwala Hub" -> Color(0xFF047857)
                                                "Nearby Village" -> Color(0xFFB45309)
                                                else -> Color(0xFF1D4ED8)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showSearchDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// Background coordinate grid representing Pakistan geographical plane
private fun DrawScope.drawGridBackground(w: Float, h: Float, zoom: Float) {
    val step = (40f * zoom).coerceIn(20f, 100f)
    var x = 0f
    while (x <= w) {
        drawLine(
            color = Color(0x1534D399),
            start = Offset(x, 0f),
            end = Offset(x, h),
            strokeWidth = 1f
        )
        x += step
    }
    var y = 0f
    while (y <= h) {
        drawLine(
            color = Color(0x1534D399),
            start = Offset(0f, y),
            end = Offset(w, y),
            strokeWidth = 1f
        )
        y += step
    }
}

// Draw realistic rivers of Pakistan (Chenab, Ravi, Indus, Gugera Canal in Jaranwala)
private fun DrawScope.drawPakistanRivers(w: Float, h: Float, project: (Double, Double) -> Offset) {
    val riverColor = Color(0xFF1E3A5F)
    val canalColor = Color(0xFF155E75)

    // Gugera Branch Canal (passes directly through Jaranwala)
    val p1 = project(31.3650, 73.4050)
    val p2 = project(31.3390, 73.4215)
    val p3 = project(31.3050, 73.4450)

    val canalPath = Path().apply {
        moveTo(p1.x, p1.y)
        quadraticBezierTo(p2.x, p2.y, p3.x, p3.y)
    }
    drawPath(
        path = canalPath,
        color = canalColor,
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
    )

    // Ravi River (Syedwala / South of Jaranwala)
    val r1 = project(31.2400, 73.5500)
    val r2 = project(31.2580, 73.6120)
    val r3 = project(31.2800, 73.7200)

    val raviPath = Path().apply {
        moveTo(r1.x, r1.y)
        quadraticBezierTo(r2.x, r2.y, r3.x, r3.y)
    }
    drawPath(
        path = raviPath,
        color = riverColor,
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
    )
}

// Draw arterial road network (M-3 Motorway, Faisalabad-Jaranwala, Lahore Rd, Syedwala, Satyana)
private fun DrawScope.drawRoadNetwork(w: Float, h: Float, zoom: Float, project: (Double, Double) -> Offset) {
    val motorwayColor = Color(0xFF334155)
    val highwayColor = Color(0xFF1E3A2F)
    val jaranwalaRoadColor = Color(0xFF166534)

    // Jaranwala - Faisalabad Road
    val jf1 = project(31.3342, 73.4194)
    val jf2 = project(31.3490, 73.3980)
    val jf3 = project(31.4187, 73.0791)
    drawLine(color = highwayColor, start = jf1, end = jf2, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color = highwayColor, start = jf2, end = jf3, strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)

    // Jaranwala - Lahore Road / By-pass (connecting toward M-3 Motorway Interchange)
    val jl1 = project(31.3342, 73.4194)
    val jl2 = project(31.3520, 73.4510)
    val jl3 = project(31.4285, 73.5780) // Buchiana
    val jl4 = project(31.5204, 74.3587) // Lahore
    drawLine(color = highwayColor, start = jl1, end = jl2, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    drawLine(color = highwayColor, start = jl2, end = jl3, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)

    // Jaranwala - Satyana Road
    val js1 = project(31.3342, 73.4194)
    val js2 = project(31.2850, 73.2840) // Satyana
    drawLine(color = highwayColor, start = js1, end = js2, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)

    // Jaranwala - Syedwala Road
    val jsw1 = project(31.3342, 73.4194)
    val jsw2 = project(31.2580, 73.6120) // Syedwala
    drawLine(color = highwayColor, start = jsw1, end = jsw2, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)

    // Committee Chowk Intersection Hub in Jaranwala
    val chowk = project(31.3342, 73.4194)
    drawCircle(color = jaranwalaRoadColor, radius = (12f * zoom).coerceIn(6f, 18f), center = chowk)
}

// Draw individual location point marker with label
private fun DrawScope.drawLocationMarker(loc: PakistanLocationPoint, pos: Offset, zoom: Float) {
    val isJaranwala = loc.category == "Jaranwala Hub"
    val isVillage = loc.category == "Nearby Village"

    val markerColor = when {
        isJaranwala -> Color(0xFF10B981)
        isVillage -> Color(0xFFF59E0B)
        else -> Color(0xFF60A5FA)
    }

    val radius = if (isJaranwala) (6f * zoom).coerceIn(4f, 10f) else (4.5f * zoom).coerceIn(2.5f, 7f)

    drawCircle(
        color = markerColor,
        radius = radius,
        center = pos
    )

    // Show labels when zoomed in or for major hubs
    if (zoom > 0.6f || isJaranwala || loc.category == "Major City") {
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = if (isJaranwala) 26f else 22f
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val shortName = loc.name.substringBefore(",").substringBefore("(")
            drawText(shortName, pos.x + radius + 6f, pos.y + 7f, paint)
        }
    }
}
