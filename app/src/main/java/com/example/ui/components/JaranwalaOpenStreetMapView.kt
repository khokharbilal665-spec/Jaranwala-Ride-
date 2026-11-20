package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.RideBooking
import com.example.data.RideStatus
import com.example.data.VehicleCategory
import com.example.util.PakistanGeoService
import com.example.util.PakistanLocationPoint
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale

/**
 * High-performance OpenStreetMap component for JARANWALA RIDE.
 * Powered by osmdroid, strictly adhering to OSM tile usage policies (custom User-Agent & caching).
 * Provides full map coverage of Jaranwala, rural villages, Syedwala corridor, and nationwide Pakistan
 * with zero Google Maps API keys or billing requirements.
 */
@Composable
fun JaranwalaOpenStreetMapView(
    modifier: Modifier = Modifier,
    pickupText: String = "Committee Chowk",
    destinationText: String = "Jaranwala Railway Station",
    rideStatus: RideStatus = RideStatus.IDLE,
    category: VehicleCategory = VehicleCategory.RICKSHAW,
    activeRide: RideBooking? = null,
    pinSelectionMode: PinSelectionMode? = null,
    onPinSelectionModeChange: ((PinSelectionMode) -> Unit)? = null,
    onPickupChange: ((String) -> Unit)? = null,
    onDestinationChange: ((String) -> Unit)? = null,
    onSwitchToCanvas: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Ensure osmdroid config is loaded BEFORE setting custom userAgentValue and BEFORE MapView instantiation
    remember(context) {
        ensureOsmDroidConfigured(context)
        true
    }

    // Resolve active pickup & destination coordinates (preferring activeRide Firestore coords or embedded pin coords)
    val pickupPoint = remember(pickupText, activeRide?.pickupLat, activeRide?.pickupLng, activeRide?.pickup) {
        resolveMapPoint(
            label = pickupText,
            defaultPoint = PakistanGeoService.PAKISTAN_LOCATIONS[0],
            overrideLat = activeRide?.pickupLat,
            overrideLng = activeRide?.pickupLng,
            overrideName = activeRide?.pickup
        )
    }

    val destPoint = remember(destinationText, activeRide?.destLat, activeRide?.destLng, activeRide?.destination) {
        resolveMapPoint(
            label = destinationText,
            defaultPoint = PakistanGeoService.PAKISTAN_LOCATIONS[1],
            overrideLat = activeRide?.destLat,
            overrideLng = activeRide?.destLng,
            overrideName = activeRide?.destination
        )
    }

    // Jaranwala default center (31.3344, 73.4316)
    val jaranwalaGeoPoint = remember {
        GeoPoint(PakistanGeoService.JARANWALA_LAT, PakistanGeoService.JARANWALA_LNG)
    }

    // Interactive UI states
    var internalPinMode by remember { mutableStateOf(PinSelectionMode.DESTINATION) }
    val activePinMode = pinSelectionMode ?: internalPinMode
    val updatePinMode: (PinSelectionMode) -> Unit = { newMode ->
        internalPinMode = newMode
        onPinSelectionModeChange?.invoke(newMode)
    }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showManualPinDialog by remember { mutableStateOf(false) }
    var showAttributionDialog by remember { mutableStateOf(false) }
    var manualPinLat by remember { mutableDoubleStateOf(PakistanGeoService.JARANWALA_LAT) }
    var manualPinLng by remember { mutableDoubleStateOf(PakistanGeoService.JARANWALA_LNG) }
    var manualPinVillageName by remember { mutableStateOf("") }

    // Location permission state
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Reference to MapView to control pan/zoom imperatively
    var osmMapView by remember { mutableStateOf<MapView?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        if (granted) {
            PakistanGeoService.fetchCurrentGpsLocation(
                context = context,
                onSuccess = { loc ->
                    osmMapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                    osmMapView?.controller?.setZoom(15.5)
                    onPickupChange?.invoke(loc.name)
                    Toast.makeText(context, "GPS لوکیشن: ${loc.name}", Toast.LENGTH_SHORT).show()
                },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            Toast.makeText(context, "GPS لوکیشن حاصل کرنے کیلئے اجازت درکار ہے", Toast.LENGTH_SHORT).show()
        }
    }

    // Driver simulation animation
    val infiniteTransition = rememberInfiniteTransition(label = "osmMapPulse")
    val vehicleProgress by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "osmVehicleProgress"
    )

    val isRideActive = rideStatus == RideStatus.ACCEPTED ||
            rideStatus == RideStatus.DRIVER_ARRIVED ||
            rideStatus == RideStatus.IN_PROGRESS

    var followDriverOnMap by remember(activeRide?.id) { mutableStateOf(true) }
    val hasLiveFirestoreGps = activeRide?.driverLat != null && activeRide?.driverLng != null

    // Real-time Captain GPS coordinates synced from Firestore (/ride_requests & /drivers)
    val (driverLat, driverLng) = remember(
        rideStatus,
        activeRide?.driverLat,
        activeRide?.driverLng,
        vehicleProgress,
        pickupPoint.latitude,
        pickupPoint.longitude,
        destPoint.latitude,
        destPoint.longitude
    ) {
        val liveLat = activeRide?.driverLat
        val liveLng = activeRide?.driverLng
        if (liveLat != null && liveLng != null) {
            liveLat to liveLng
        } else {
            when (rideStatus) {
                RideStatus.ACCEPTED -> {
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
                    val midLat = (pickupPoint.latitude + destPoint.latitude) / 2.0 + 0.002
                    val midLng = (pickupPoint.longitude + destPoint.longitude) / 2.0 - 0.002
                    val lat = (1 - progress) * (1 - progress) * pickupPoint.latitude + 2 * (1 - progress) * progress * midLat + progress * progress * destPoint.latitude
                    val lng = (1 - progress) * (1 - progress) * pickupPoint.longitude + 2 * (1 - progress) * progress * midLng + progress * progress * destPoint.longitude
                    lat to lng
                }
                else -> pickupPoint.latitude to pickupPoint.longitude
            }
        }
    }

    // Distance and ETA (Route pickup->dest + Live driver-to-pickup approach telemetry)
    val distanceKm = remember(pickupPoint, destPoint) {
        PakistanGeoService.calculateDistanceKm(
            pickupPoint.latitude, pickupPoint.longitude,
            destPoint.latitude, destPoint.longitude
        )
    }

    val liveDriverRemainingKm = remember(rideStatus, driverLat, driverLng, pickupPoint, destPoint) {
        when (rideStatus) {
            RideStatus.ACCEPTED -> PakistanGeoService.calculateDistanceKm(
                driverLat, driverLng,
                pickupPoint.latitude, pickupPoint.longitude
            )
            RideStatus.DRIVER_ARRIVED -> 0.0
            RideStatus.IN_PROGRESS -> PakistanGeoService.calculateDistanceKm(
                driverLat, driverLng,
                destPoint.latitude, destPoint.longitude
            )
            else -> distanceKm
        }
    }

    val etaMinutes = remember(distanceKm, liveDriverRemainingKm, isRideActive, category) {
        val targetKm = if (isRideActive) liveDriverRemainingKm else distanceKm
        val speedKmh = when (category) {
            VehicleCategory.BIKE -> 28.0
            VehicleCategory.RICKSHAW -> 22.0
            VehicleCategory.CAR -> 32.0
        }
        if (rideStatus == RideStatus.DRIVER_ARRIVED) 0
        else (targetKm / speedKmh * 60.0).toInt().coerceAtLeast(1)
    }

    // Real OSRM Pakistan road polyline coordinates (with automatic curved fallback if offline)
    var osrmMainRoutePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var osrmApproachRoutePoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }

    LaunchedEffect(pickupPoint.latitude, pickupPoint.longitude, destPoint.latitude, destPoint.longitude) {
        osrmMainRoutePoints = emptyList()
        val fetched = com.example.pricing.LocationDistanceService.getInstance()
            .fetchDrivingRoutePoints(
                pickupPoint.latitude,
                pickupPoint.longitude,
                destPoint.latitude,
                destPoint.longitude
            )
        if (fetched.size >= 2) {
            osrmMainRoutePoints = fetched.map { (lat, lng) -> GeoPoint(lat, lng) }
        }
    }

    val driverGridLat = if (hasLiveFirestoreGps) (driverLat * 1000.0).toInt() else 0
    val driverGridLng = if (hasLiveFirestoreGps) (driverLng * 1000.0).toInt() else 0
    LaunchedEffect(hasLiveFirestoreGps, rideStatus, driverGridLat, driverGridLng, pickupPoint.latitude, pickupPoint.longitude) {
        if (hasLiveFirestoreGps && rideStatus == RideStatus.ACCEPTED) {
            val fetched = com.example.pricing.LocationDistanceService.getInstance()
                .fetchDrivingRoutePoints(
                    driverLat,
                    driverLng,
                    pickupPoint.latitude,
                    pickupPoint.longitude
                )
            if (fetched.size >= 2) {
                osrmApproachRoutePoints = fetched.map { (lat, lng) -> GeoPoint(lat, lng) }
            }
        } else if (rideStatus != RideStatus.ACCEPTED) {
            osrmApproachRoutePoints = emptyList()
        }
    }

    // Smoothly track driver's Firestore coordinate changes on the osmdroid MapView
    LaunchedEffect(activeRide?.driverLat, activeRide?.driverLng, isRideActive, followDriverOnMap) {
        val lat = activeRide?.driverLat
        val lng = activeRide?.driverLng
        if (isRideActive && followDriverOnMap && lat != null && lng != null) {
            osmMapView?.controller?.animateTo(GeoPoint(lat, lng))
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .testTag("jaranwala_osm_map_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1914))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar: Search + Manual Pin Drop + Map Switch + Navigation
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
                            .testTag("osm_map_search_trigger"),
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
                                contentDescription = "Search Jaranwala & Villages",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "تلاش کریں (گاؤں، چک، شہر)...",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Manual Pin Button (When a rural village/chak is missing)
                    ElevatedButton(
                        onClick = {
                            manualPinLat = pickupPoint.latitude
                            manualPinLng = pickupPoint.longitude
                            showManualPinDialog = true
                        },
                        colors = ButtonDefaults.elevatedButtonColors(
                            containerColor = Color(0xFF064E3B),
                            contentColor = Color(0xFF6EE7B7)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("osm_manual_pin_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = "Manual Pin",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("پن لگائیں", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Switch to Offline Vector Canvas if requested
                    if (onSwitchToCanvas != null) {
                        ElevatedButton(
                            onClick = onSwitchToCanvas,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = Color(0xFF0F3B2C),
                                contentColor = Color(0xFF34D399)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("switch_to_canvas_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = "Vector Canvas",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ویکٹر نقشہ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Open Turn-by-Turn Directions in external Maps App
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
                            .testTag("osm_external_nav_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "Turn-by-turn Navigation",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("نیویگیشن", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // OpenStreetMap Interactive Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Color(0xFF081511))
                    .testTag("osm_map_viewport")
            ) {
                val latestPinMode by androidx.compose.runtime.rememberUpdatedState(activePinMode)
                val latestOnPickupChange by androidx.compose.runtime.rememberUpdatedState(onPickupChange)
                val latestOnDestinationChange by androidx.compose.runtime.rememberUpdatedState(onDestinationChange)
                val overlayHolder = remember { OsmOverlayHolder() }

                // osmdroid MapView hosted in Jetpack Compose AndroidView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        ensureOsmDroidConfigured(ctx)
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                            controller.setZoom(14.5)
                            controller.setCenter(jaranwalaGeoPoint)

                            // Setup Tap detection for location selection & manual pin placement
                            val tapReceiver = object : MapEventsReceiver {
                                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                    val closest = PakistanGeoService.findClosestLocation(p.latitude, p.longitude)
                                    val distToClosestKm = PakistanGeoService.calculateDistanceKm(
                                        p.latitude, p.longitude,
                                        closest.latitude, closest.longitude
                                    )
                                    val selectedLabel = if (distToClosestKm <= 0.4) {
                                        closest.name
                                    } else {
                                        val coords = String.format(Locale.US, "%.4f, %.4f", p.latitude, p.longitude)
                                        "${closest.name.substringBefore(",")} ($coords)"
                                    }
                                    if (latestPinMode == PinSelectionMode.PICKUP) {
                                        latestOnPickupChange?.invoke(selectedLabel)
                                        Toast.makeText(ctx, "پک اپ: $selectedLabel", Toast.LENGTH_SHORT).show()
                                    } else {
                                        latestOnDestinationChange?.invoke(selectedLabel)
                                        Toast.makeText(ctx, "منزل: $selectedLabel", Toast.LENGTH_SHORT).show()
                                    }
                                    return true
                                }

                                override fun longPressHelper(p: GeoPoint): Boolean {
                                    manualPinLat = p.latitude
                                    manualPinLng = p.longitude
                                    showManualPinDialog = true
                                    return true
                                }
                            }
                            overlays.add(MapEventsOverlay(tapReceiver))

                            // Pre-allocate reusable polylines & markers to avoid Bitmap churn during live GPS tracking
                            overlayHolder.initialize(ctx, this)
                            osmMapView = this
                        }
                    },
                    update = { mapView ->
                        osmMapView = mapView
                        overlayHolder.initialize(context, mapView)

                        // 1. Update Pickup Marker position & metadata
                        overlayHolder.pickupMarker?.apply {
                            position = GeoPoint(pickupPoint.latitude, pickupPoint.longitude)
                            title = "پک اپ (Pickup): ${pickupPoint.urduName}"
                            snippet = pickupPoint.name
                        }

                        // 2. Update Destination Marker position & metadata
                        overlayHolder.destMarker?.apply {
                            position = GeoPoint(destPoint.latitude, destPoint.longitude)
                            title = "منزل (Dropoff): ${destPoint.urduName}"
                            snippet = destPoint.name
                        }

                        // 3. Update Route Polyline dynamically during active trips (real OSRM road geometry with offline curved fallback)
                        overlayHolder.routePolyline?.let { route ->
                            if (isRideActive && rideStatus == RideStatus.IN_PROGRESS) {
                                route.setPoints(
                                    buildCurvedRoutePoints(
                                        startLat = driverLat,
                                        startLng = driverLng,
                                        endLat = destPoint.latitude,
                                        endLng = destPoint.longitude
                                    )
                                )
                            } else if (osrmMainRoutePoints.size >= 2) {
                                route.setPoints(osrmMainRoutePoints)
                            } else {
                                route.setPoints(
                                    buildCurvedRoutePoints(
                                        startLat = pickupPoint.latitude,
                                        startLng = pickupPoint.longitude,
                                        endLat = destPoint.latitude,
                                        endLng = destPoint.longitude
                                    )
                                )
                            }
                        }

                        // 4. Update Live Captain/Driver Marker & Approach/Traveled Polyline (from Firestore listeners)
                        val driverMarker = overlayHolder.driverMarker
                        val approachPolyline = overlayHolder.approachPolyline
                        if (isRideActive && driverMarker != null && approachPolyline != null) {
                            driverMarker.position = GeoPoint(driverLat, driverLng)
                            driverMarker.title = "★ Captain ${activeRide?.driverName ?: "Arshad"}"
                            driverMarker.snippet = when (rideStatus) {
                                RideStatus.ACCEPTED -> "On way to pickup • ${category.name}"
                                RideStatus.DRIVER_ARRIVED -> "At pickup location • ${category.name}"
                                RideStatus.IN_PROGRESS -> "En route to destination • ${category.name}"
                                else -> category.name
                            }
                            if (!mapView.overlays.contains(driverMarker)) {
                                mapView.overlays.add(driverMarker)
                            }
                            when (rideStatus) {
                                RideStatus.ACCEPTED -> {
                                    if (osrmApproachRoutePoints.size >= 2) {
                                        approachPolyline.setPoints(osrmApproachRoutePoints)
                                    } else {
                                        approachPolyline.setPoints(
                                            buildCurvedRoutePoints(
                                                startLat = driverLat,
                                                startLng = driverLng,
                                                endLat = pickupPoint.latitude,
                                                endLng = pickupPoint.longitude
                                            )
                                        )
                                    }
                                    if (!mapView.overlays.contains(approachPolyline)) {
                                        mapView.overlays.add(approachPolyline)
                                    }
                                }
                                RideStatus.IN_PROGRESS -> {
                                    approachPolyline.setPoints(
                                        buildCurvedRoutePoints(
                                            startLat = pickupPoint.latitude,
                                            startLng = pickupPoint.longitude,
                                            endLat = driverLat,
                                            endLng = driverLng
                                        )
                                    )
                                    if (!mapView.overlays.contains(approachPolyline)) {
                                        mapView.overlays.add(approachPolyline)
                                    }
                                }
                                else -> {
                                    mapView.overlays.remove(approachPolyline)
                                }
                            }
                        } else {
                            if (driverMarker != null) mapView.overlays.remove(driverMarker)
                            if (approachPolyline != null) mapView.overlays.remove(approachPolyline)
                        }

                        mapView.invalidate()
                    }
                )

                // Top Left: Map Engine Badge & Status
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
                            text = "OpenStreetMap • مکمل پاکستان نقشہ",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Top Right: Map Controls (+, -, Center Jaranwala, GPS, Attribution Info)
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
                            .clickable {
                                osmMapView?.controller?.zoomIn()
                            }
                            .testTag("osm_map_zoom_in_btn")
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
                            .clickable {
                                osmMapView?.controller?.zoomOut()
                            }
                            .testTag("osm_map_zoom_out_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Center Jaranwala Button (31.3344, 73.4316)
                    Surface(
                        color = Color(0xCC06231A),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable {
                                osmMapView?.controller?.animateTo(jaranwalaGeoPoint)
                                osmMapView?.controller?.setZoom(14.5)
                                Toast.makeText(context, "مرکز: جرانوالہ (31.3344, 73.4316)", Toast.LENGTH_SHORT).show()
                            }
                            .testTag("osm_map_recenter_jaranwala_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CenterFocusStrong, contentDescription = "Center Jaranwala", tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                        }
                    }

                    // GPS Location Button
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
                            .testTag("osm_map_gps_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.MyLocation, contentDescription = "My GPS Location", tint = Color(0xFF3B82F6), modifier = Modifier.size(18.dp))
                        }
                    }

                    // OSM Attribution & Info Button
                    Surface(
                        color = Color(0xCC06231A),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable { showAttributionDialog = true }
                            .testTag("osm_attribution_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Info, contentDescription = "OpenStreetMap Info", tint = Color(0xFF6EE7B7), modifier = Modifier.size(18.dp))
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
                        Text("نقشے پر ٹیپ کریں: ", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        Surface(
                            color = if (activePinMode == PinSelectionMode.PICKUP) Color(0xFF10B981) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clickable { updatePinMode(PinSelectionMode.PICKUP) }
                                .testTag("select_pickup_pin_mode")
                        ) {
                            Text(
                                text = "پک اپ",
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
                            modifier = Modifier
                                .clickable { updatePinMode(PinSelectionMode.DESTINATION) }
                                .testTag("select_destination_pin_mode")
                        ) {
                            Text(
                                text = "منزل",
                                color = if (activePinMode == PinSelectionMode.DESTINATION) Color.Black else Color(0xFFF59E0B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Bottom Right: Live Driver Tracking HUD Overlay Card (Active Booking + Firestore GPS)
                if (isRideActive) {
                    val formattedRemainingKm = String.format(Locale.US, "%.2f", liveDriverRemainingKm)
                    Surface(
                        color = Color(0xF2041A13),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (hasLiveFirestoreGps) Color(0xFF10B981) else Color(0xFFF59E0B)
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .testTag("osm_driver_tracking_hud")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (hasLiveFirestoreGps) Color(0xFF059669) else Color(0xFFD97706)),
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "★ ${activeRide?.driverName ?: "Captain Assigned"}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Surface(
                                        color = if (hasLiveFirestoreGps) Color(0xFF065F46) else Color(0xFF78350F),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (hasLiveFirestoreGps) "GPS LIVE" else "SYNCING",
                                            color = if (hasLiveFirestoreGps) Color(0xFF6EE7B7) else Color(0xFFFDE68A),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                .testTag("osm_gps_sync_badge")
                                        )
                                    }
                                }
                                Text(
                                    text = when (rideStatus) {
                                        RideStatus.ACCEPTED -> "$formattedRemainingKm km دور • ${etaMinutes}m پک اپ تک"
                                        RideStatus.DRIVER_ARRIVED -> "پک اپ پر پہنچ گئے • Arrived"
                                        RideStatus.IN_PROGRESS -> "$formattedRemainingKm km منزل تک • ${etaMinutes}m"
                                        else -> "لائیو ٹریکنگ"
                                    },
                                    color = Color(0xFF34D399),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.testTag("osm_driver_eta_distance_text")
                                )
                                Text(
                                    text = String.format(Locale.US, "%.4f°N, %.4f°E", driverLat, driverLng),
                                    color = Color(0xFF94A3B8),
                                    fontSize = 8.sp,
                                    modifier = Modifier.testTag("osm_driver_coords_text")
                                )
                            }

                            // Center on Captain + Toggle Follow button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (followDriverOnMap) Color(0xFF10B981) else Color(0xFF065F46),
                                modifier = Modifier
                                    .clickable {
                                        followDriverOnMap = true
                                        osmMapView?.controller?.animateTo(GeoPoint(driverLat, driverLng))
                                        osmMapView?.controller?.setZoom(16.0)
                                        Toast.makeText(context, "کپتان کی لائیو لوکیشن پر فوکس", Toast.LENGTH_SHORT).show()
                                    }
                                    .testTag("osm_center_captain_btn")
                            ) {
                                Text(
                                    text = "کپتان",
                                    color = if (followDriverOnMap) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Prominent OpenStreetMap Attribution Bar (Policy compliant)
            Surface(
                color = Color(0xFF041812),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "© OpenStreetMap contributors • پاکستان دیہات اور شہر نقشہ",
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "اوپن اسٹریٹ میپ",
                        color = Color(0xFF10B981),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { showAttributionDialog = true }
                    )
                }
            }

            // Quick Hotspots & Rural Villages Carousel
            Surface(
                color = Color(0xFF02130E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "فوری منتخب کریں:",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val villageHotspots = listOf(
                        "کمیٹی چوک" to "Committee Chowk, Jaranwala",
                        "سیدوالا" to "Syedwala, Jaranwala",
                        "منڈی سیدوالا" to "Mandi Syedwala",
                        "بچیانہ" to "Buchiana, Jaranwala",
                        "لنڈیانوالہ" to "Lundianwala, Jaranwala",
                        "آواگت" to "Awagat, Jaranwala",
                        "ستیانہ" to "Satyana, Jaranwala",
                        "چک 58 جی بی" to "Chak 58 GB, Jaranwala",
                        "چک 115 جی بی" to "Chak 115 GB, Jaranwala",
                        "چک 240 جی بی" to "Chak 240 GB, Jaranwala",
                        "ہیڈ گوگیرہ" to "Head Gugera, Jaranwala",
                        "فیصل آباد" to "Faisalabad (Lyallpur)",
                        "لاہور" to "Lahore"
                    )

                    villageHotspots.forEach { (urduLabel, fullName) ->
                        Surface(
                            color = Color(0xFF0D3325),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                            modifier = Modifier.clickable {
                                val match = PakistanGeoService.PAKISTAN_LOCATIONS.find { it.name == fullName }
                                if (match != null) {
                                    if (activePinMode == PinSelectionMode.PICKUP) {
                                        onPickupChange?.invoke(match.name)
                                    } else {
                                        onDestinationChange?.invoke(match.name)
                                    }
                                    osmMapView?.controller?.animateTo(GeoPoint(match.latitude, match.longitude))
                                    osmMapView?.controller?.setZoom(15.0)
                                    Toast.makeText(context, "${urduLabel} منتخب ہو گیا", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Text(
                                text = urduLabel,
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Search Location Dialog across Pakistan (Jaranwala, Villages, Towns, Cities)
    if (showSearchDialog) {
        var query by remember { mutableStateOf("") }
        val searchResults = remember(query) { PakistanGeoService.searchPakistan(query) }

        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = {
                Text(
                    text = "پاکستان بھر میں لوکیشن تلاش کریں",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("osm_search_text_input"),
                        placeholder = { Text("جرانوالہ، سیدوالا، چک، یا شہر کا نام لکھیں...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF10B981)) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF0F3B2C),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = Color.LightGray,
                            unfocusedPlaceholderColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "دستیاب نتائج (${searchResults.size}):",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    ) {
                        items(searchResults) { loc ->
                            Surface(
                                color = Color(0xFF06231A),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        if (activePinMode == PinSelectionMode.PICKUP) {
                                            onPickupChange?.invoke(loc.name)
                                        } else {
                                            onDestinationChange?.invoke(loc.name)
                                        }
                                        osmMapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                                        osmMapView?.controller?.setZoom(15.0)
                                        showSearchDialog = false
                                        Toast.makeText(context, "${loc.urduName} منتخب کر لیا گیا", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = if (loc.name.contains("Jaranwala")) Color(0xFF10B981) else Color(0xFF38BDF8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = loc.urduName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${loc.name} • ${loc.category}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("بند کریں (Close)", color = Color(0xFF10B981))
                }
            },
            containerColor = Color(0xFF041812)
        )
    }

    // Manual Pin Drop Dialog (When a specific village or settlement is unlisted or custom)
    if (showManualPinDialog) {
        AlertDialog(
            onDismissRequest = { showManualPinDialog = false },
            title = {
                Text(
                    text = "نیا مقام / دیہات پن کریں (Manual Pin)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "اگر آپ کا گاؤں یا چک نقشے پر درج نہیں تو یہاں نام لکھیں یا نقشے کے کوآرڈینیٹس استعمال کریں:",
                        color = Color(0xFFD1FAE5),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = manualPinVillageName,
                        onValueChange = { manualPinVillageName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("osm_manual_village_name_input"),
                        placeholder = { Text("گاؤں یا چک کا نام (مثال: چک 117 جی بی)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF0F3B2C),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = Color.LightGray,
                            unfocusedPlaceholderColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "کوآرڈینیٹس: ${String.format(Locale.US, "%.5f, %.5f", manualPinLat, manualPinLng)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                ElevatedButton(
                    onClick = {
                        val customPoint = PakistanGeoService.createCustomPinnedLocation(
                            lat = manualPinLat,
                            lng = manualPinLng,
                            customName = manualPinVillageName.ifBlank { "Custom Pin" }
                        )
                        if (activePinMode == PinSelectionMode.PICKUP) {
                            onPickupChange?.invoke(customPoint.name)
                        } else {
                            onDestinationChange?.invoke(customPoint.name)
                        }
                        osmMapView?.controller?.animateTo(GeoPoint(manualPinLat, manualPinLng))
                        osmMapView?.controller?.setZoom(15.5)
                        showManualPinDialog = false
                        Toast.makeText(context, "${customPoint.name} پن ہو گیا", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.Black
                    )
                ) {
                    Text("یہاں پن لگائیں", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualPinDialog = false }) {
                    Text("منسوخ کریں", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF041812)
        )
    }

    // OpenStreetMap Attribution Information Dialog
    if (showAttributionDialog) {
        AlertDialog(
            onDismissRequest = { showAttributionDialog = false },
            title = {
                Text(
                    text = "OpenStreetMap معلومات و لائسنس",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "یہ ایپلیکیشن مکمل طور پر اوپن اسٹریٹ میپ (OpenStreetMap) کا ڈیٹا اور ٹائلز استعمال کرتی ہے۔",
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• کوئی گوگل میپس API کی یا کریڈٹ کارڈ بلنگ درکار نہیں۔\n• پاکستان کے تمام شہر، تحصیل جرانوالہ، اور دیہی چکس شامل ہیں۔\n• ڈیٹا © اوپن اسٹریٹ میپ کنٹری بیوٹرز کے تحت اوپن ڈیٹا کامنز (ODbL) لائسنس یافتہ ہے۔",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttributionDialog = false }) {
                    Text("ٹھیک ہے (OK)", color = Color(0xFF10B981))
                }
            },
            containerColor = Color(0xFF041812)
        )
    }
}

/**
 * Loads osmdroid preferences first, then applies a compliant non-default User-Agent
 * so tile requests never emit `Please configure a relevant user agent; current value is: osmdroid`.
 */
fun ensureOsmDroidConfigured(context: Context) {
    val appCtx = context.applicationContext ?: context
    val prefs = appCtx.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE)
    val config = Configuration.getInstance()
    config.load(appCtx, prefs)
    val pkg = appCtx.packageName?.takeIf { it.isNotBlank() && !it.equals("osmdroid", ignoreCase = true) }
        ?: "com.aistudio.jaranwalaride.xmpqtr"
    config.userAgentValue = "JaranwalaRide/1.0 ($pkg)"
}

private val MAP_COORD_REGEX = Regex("""\(\s*(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)\s*\)""")

private fun resolveMapPoint(
    label: String,
    defaultPoint: PakistanLocationPoint,
    overrideLat: Double?,
    overrideLng: Double?,
    overrideName: String? = null
): PakistanLocationPoint {
    if (overrideLat != null && overrideLng != null) {
        val baseName = overrideName?.takeIf { it.isNotBlank() } ?: label.ifBlank { defaultPoint.name }
        val catalogMatch = PakistanGeoService.PAKISTAN_LOCATIONS.find {
            it.name.contains(baseName, true) || baseName.contains(it.name, true)
        }
        return (catalogMatch ?: defaultPoint).copy(
            name = baseName,
            urduName = catalogMatch?.urduName ?: baseName,
            latitude = overrideLat,
            longitude = overrideLng
        )
    }

    val clean = label.trim()
    if (clean.isNotBlank()) {
        val coordMatch = MAP_COORD_REGEX.find(clean)
        if (coordMatch != null) {
            val lat = coordMatch.groupValues[1].toDoubleOrNull()
            val lng = coordMatch.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null && PakistanGeoService.isWithinPakistan(lat, lng)) {
                return PakistanLocationPoint(
                    name = clean,
                    urduName = clean,
                    category = "Map Pin",
                    latitude = lat,
                    longitude = lng
                )
            }
        }
        val catalogMatch = PakistanGeoService.PAKISTAN_LOCATIONS.find {
            it.name.equals(clean, ignoreCase = true) ||
                it.name.contains(clean, ignoreCase = true) ||
                clean.contains(it.name.substringBefore(","), ignoreCase = true)
        }
        if (catalogMatch != null) return catalogMatch
    }
    return defaultPoint
}

internal fun buildCurvedRoutePoints(
    startLat: Double,
    startLng: Double,
    endLat: Double,
    endLng: Double,
    steps: Int = 8
): List<GeoPoint> {
    val pts = ArrayList<GeoPoint>(steps + 1)
    pts.add(GeoPoint(startLat, startLng))
    for (i in 1 until steps) {
        val t = i.toDouble() / steps.toDouble()
        val curveOffset = kotlin.math.sin(t * Math.PI) * 0.0018
        val lat = (1 - t) * startLat + t * endLat + curveOffset
        val lng = (1 - t) * startLng + t * endLng - (curveOffset * 0.6)
        pts.add(GeoPoint(lat, lng))
    }
    pts.add(GeoPoint(endLat, endLng))
    return pts
}

private class OsmOverlayHolder {
    var pickupMarker: Marker? = null
    var destMarker: Marker? = null
    var driverMarker: Marker? = null
    var routePolyline: Polyline? = null
    var approachPolyline: Polyline? = null
    private var initializedForMap: MapView? = null

    fun initialize(context: Context, mapView: MapView) {
        if (initializedForMap === mapView) return
        initializedForMap = mapView

        routePolyline = Polyline().apply {
            outlinePaint.color = android.graphics.Color.parseColor("#10B981")
            outlinePaint.strokeWidth = 12f
        }
        approachPolyline = Polyline().apply {
            outlinePaint.color = android.graphics.Color.parseColor("#F59E0B")
            outlinePaint.strokeWidth = 10f
        }
        pickupMarker = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = createPinDrawable(context, android.graphics.Color.parseColor("#10B981"), "P")
        }
        destMarker = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = createPinDrawable(context, android.graphics.Color.parseColor("#EF4444"), "D")
        }
        driverMarker = Marker(mapView).apply {
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = createPinDrawable(context, android.graphics.Color.parseColor("#3B82F6"), "★")
        }

        routePolyline?.let { mapView.overlays.add(it) }
        pickupMarker?.let { mapView.overlays.add(it) }
        destMarker?.let { mapView.overlays.add(it) }
    }
}

/**
 * Creates custom pin drawable for OpenStreetMap marker.
 */
private fun createPinDrawable(context: Context, colorInt: Int, label: String): Drawable {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorInt
        style = Paint.Style.FILL
    }
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        alpha = 80
        style = Paint.Style.FILL
    }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 24f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    // Outer circle
    canvas.drawCircle(size / 2f, size / 2.4f, 22f, paint)
    // Small triangle bottom
    val path = android.graphics.Path().apply {
        moveTo(size / 2f - 14f, size / 2.2f)
        lineTo(size / 2f + 14f, size / 2.2f)
        lineTo(size / 2f, size.toFloat() - 4f)
        close()
    }
    canvas.drawPath(path, paint)

    // Center label
    canvas.drawText(label, size / 2f, size / 2.4f + 8f, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
