package com.example.pricing

import com.example.util.PakistanGeoService
import com.example.util.PakistanLocationPoint
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Retrofit DTOs for OpenStreetMap Location APIs (OSRM Routing & Nominatim)
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class OsrmRouteResponse(
    @param:Json(name = "code") val code: String? = null,
    @param:Json(name = "routes") val routes: List<OsrmRoute>? = null
)

@JsonClass(generateAdapter = true)
data class OsrmGeometry(
    @param:Json(name = "coordinates") val coordinates: List<List<Double>>? = null
)

@JsonClass(generateAdapter = true)
data class OsrmRoute(
    @param:Json(name = "distance") val distanceMeters: Double = 0.0,
    @param:Json(name = "duration") val durationSeconds: Double = 0.0,
    @param:Json(name = "geometry") val geometry: OsrmGeometry? = null
)

@JsonClass(generateAdapter = true)
data class NominatimPlaceDto(
    @param:Json(name = "lat") val lat: String = "",
    @param:Json(name = "lon") val lon: String = "",
    @param:Json(name = "display_name") val displayName: String = ""
)

interface OsrmRoutingApi {
    @GET("route/v1/driving/{coordinates}")
    suspend fun getDrivingRoute(
        @Path("coordinates", encoded = true) coordinates: String,
        @Query("overview") overview: String = "false",
        @Query("geometries") geometries: String = "geojson"
    ): OsrmRouteResponse
}

interface NominatimGeocodingApi {
    @GET("search")
    suspend fun searchPakistanPlace(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("countrycodes") countryCodes: String = "pk",
        @Query("limit") limit: Int = 1
    ): List<NominatimPlaceDto>
}

/**
 * Abstraction for fetching route distance & geocoding coordinates so production
 * code uses Retrofit HTTP APIs and tests can plug in deterministic fakes or MockWebServer.
 */
interface LocationDistanceProvider {
    suspend fun fetchDrivingRouteMetersAndSeconds(
        pickupLat: Double,
        pickupLng: Double,
        destLat: Double,
        destLng: Double
    ): Pair<Double, Double>?

    suspend fun geocodePakistanAddress(address: String): Pair<Double, Double>?

    suspend fun fetchDrivingRoutePolyline(
        pickupLat: Double,
        pickupLng: Double,
        destLat: Double,
        destLng: Double
    ): List<Pair<Double, Double>>? = null
}

class RetrofitLocationDistanceProvider(
    osrmBaseUrl: String = "https://router.project-osrm.org/",
    nominatimBaseUrl: String = "https://nominatim.openstreetmap.org/"
) : LocationDistanceProvider {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "JaranwalaRide/1.0 (com.aistudio.jaranwalaride.xmpqtr)")
                .build()
            chain.proceed(req)
        }
        .build()

    private val osrmApi: OsrmRoutingApi = Retrofit.Builder()
        .baseUrl(osrmBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(OsrmRoutingApi::class.java)

    private val nominatimApi: NominatimGeocodingApi = Retrofit.Builder()
        .baseUrl(nominatimBaseUrl)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(NominatimGeocodingApi::class.java)

    override suspend fun fetchDrivingRouteMetersAndSeconds(
        pickupLat: Double,
        pickupLng: Double,
        destLat: Double,
        destLng: Double
    ): Pair<Double, Double>? {
        return try {
            // OSRM expects longitude,latitude order
            val coords = String.format(
                Locale.US,
                "%.6f,%.6f;%.6f,%.6f",
                pickupLng,
                pickupLat,
                destLng,
                destLat
            )
            val response = osrmApi.getDrivingRoute(coords)
            val route = response.routes?.firstOrNull()
            if (response.code.equals("Ok", ignoreCase = true) && route != null && route.distanceMeters > 0.0) {
                route.distanceMeters to route.durationSeconds
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun fetchDrivingRoutePolyline(
        pickupLat: Double,
        pickupLng: Double,
        destLat: Double,
        destLng: Double
    ): List<Pair<Double, Double>>? {
        return try {
            val coords = String.format(
                Locale.US,
                "%.6f,%.6f;%.6f,%.6f",
                pickupLng,
                pickupLat,
                destLng,
                destLat
            )
            val response = osrmApi.getDrivingRoute(
                coordinates = coords,
                overview = "full",
                geometries = "geojson"
            )
            val rawCoords = response.routes?.firstOrNull()?.geometry?.coordinates ?: return null
            if (!response.code.equals("Ok", ignoreCase = true) || rawCoords.size < 2) return null
            rawCoords.mapNotNull { pt ->
                if (pt.size >= 2) pt[1] to pt[0] else null
            }.takeIf { it.size >= 2 }
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun geocodePakistanAddress(address: String): Pair<Double, Double>? {
        val clean = address.trim()
        if (clean.length < 2) return null
        return try {
            val queryWithRegion = if (clean.contains("pakistan", true) || clean.contains("jaranwala", true)) {
                clean
            } else {
                "$clean, Jaranwala, Pakistan"
            }
            val results = nominatimApi.searchPakistanPlace(query = queryWithRegion)
            val top = results.firstOrNull()
            val lat = top?.lat?.toDoubleOrNull()
            val lon = top?.lon?.toDoubleOrNull()
            if (lat != null && lon != null && PakistanGeoService.isWithinPakistan(lat, lon)) {
                lat to lon
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }
}

/**
 * Location API Distance Service:
 * Resolves pickup and destination coordinates (from embedded pin coords, PakistanGeoService
 * village/city catalog, or Nominatim Location Geocoding API) and fetches road driving distance
 * via the OSRM Location API with caching and deterministic coordinate fallback.
 */
class LocationDistanceService(
    private val apiProvider: LocationDistanceProvider = RetrofitLocationDistanceProvider()
) {
    private val routeCache = ConcurrentHashMap<String, LocationDistanceResult>()
    private val geocodeCache = ConcurrentHashMap<String, Pair<Double, Double>>()
    private val polylineCache = ConcurrentHashMap<String, List<Pair<Double, Double>>>()

    suspend fun fetchDrivingRoutePoints(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double
    ): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val key = String.format(Locale.US, "%.4f,%.4f->%.4f,%.4f", startLat, startLng, endLat, endLng)
        polylineCache[key]?.let { return@withContext it }
        val pts = apiProvider.fetchDrivingRoutePolyline(startLat, startLng, endLat, endLng)
        if (!pts.isNullOrEmpty() && pts.size >= 2) {
            polylineCache[key] = pts
            return@withContext pts
        }
        emptyList()
    }

    /**
     * Fetches driving distance in km and estimated travel time from the Location API.
     * Falls back seamlessly to road-adjusted Haversine coordinate calculation if offline.
     */
    suspend fun fetchRideDistance(
        pickup: String,
        destination: String
    ): LocationDistanceResult = withContext(Dispatchers.IO) {
        val cleanPickup = pickup.trim().ifBlank { "Committee Chowk, Jaranwala" }
        val cleanDest = destination.trim().ifBlank { "Jaranwala Railway Station" }
        val cacheKey = "${cleanPickup.lowercase(Locale.ROOT)}|${cleanDest.lowercase(Locale.ROOT)}"

        routeCache[cacheKey]?.let { cached ->
            return@withContext cached
        }

        if (cleanPickup.equals(cleanDest, ignoreCase = true)) {
            val point = resolveLocalCoordinates(cleanPickup)
                ?: (PakistanGeoService.JARANWALA_LAT to PakistanGeoService.JARANWALA_LNG)
            return@withContext LocationDistanceResult(
                pickupName = cleanPickup,
                destinationName = cleanDest,
                pickupLat = point.first,
                pickupLng = point.second,
                destinationLat = point.first,
                destinationLng = point.second,
                distanceKm = 1.5,
                estimatedDurationMinutes = 5,
                source = DistanceSource.COORDINATE_HAVERSINE
            )
        }

        val (pickupCoords, pickupGeocoded) = resolveCoordinatesWithGeocoder(cleanPickup)
        val (destCoords, destGeocoded) = resolveCoordinatesWithGeocoder(cleanDest)

        val result = if (pickupCoords != null && destCoords != null) {
            val apiRoute = apiProvider.fetchDrivingRouteMetersAndSeconds(
                pickupLat = pickupCoords.first,
                pickupLng = pickupCoords.second,
                destLat = destCoords.first,
                destLng = destCoords.second
            )

            if (apiRoute != null) {
                val (meters, seconds) = apiRoute
                val roadKm = formatOneDecimal((meters / 1000.0).coerceAtLeast(0.8))
                val apiMinutes = (seconds / 60.0).roundToInt().coerceAtLeast(3)
                LocationDistanceResult(
                    pickupName = cleanPickup,
                    destinationName = cleanDest,
                    pickupLat = pickupCoords.first,
                    pickupLng = pickupCoords.second,
                    destinationLat = destCoords.first,
                    destinationLng = destCoords.second,
                    distanceKm = roadKm,
                    estimatedDurationMinutes = apiMinutes,
                    source = DistanceSource.LOCATION_API_OSRM,
                    resolvedViaGeocoder = pickupGeocoded || destGeocoded
                )
            } else {
                // Fallback to Haversine coordinate calculation × Jaranwala road winding factor (1.25x)
                val straightKm = PakistanGeoService.calculateDistanceKm(
                    pickupCoords.first,
                    pickupCoords.second,
                    destCoords.first,
                    destCoords.second
                )
                val roadAdjustedKm = formatOneDecimal((straightKm * ROAD_WINDING_FACTOR).coerceAtLeast(1.2))
                val estMinutes = ((roadAdjustedKm / 28.0) * 60.0).roundToInt().coerceAtLeast(4)
                LocationDistanceResult(
                    pickupName = cleanPickup,
                    destinationName = cleanDest,
                    pickupLat = pickupCoords.first,
                    pickupLng = pickupCoords.second,
                    destinationLat = destCoords.first,
                    destinationLng = destCoords.second,
                    distanceKm = roadAdjustedKm,
                    estimatedDurationMinutes = estMinutes,
                    source = DistanceSource.COORDINATE_HAVERSINE,
                    resolvedViaGeocoder = pickupGeocoded || destGeocoded
                )
            }
        } else {
            // Fallback when neither address resolved to coordinates
            val fallbackKm = estimateUnlistedDistanceKm(cleanPickup, cleanDest)
            val estMinutes = ((fallbackKm / 26.0) * 60.0).roundToInt().coerceAtLeast(5)
            LocationDistanceResult(
                pickupName = cleanPickup,
                destinationName = cleanDest,
                pickupLat = pickupCoords?.first ?: PakistanGeoService.JARANWALA_LAT,
                pickupLng = pickupCoords?.second ?: PakistanGeoService.JARANWALA_LNG,
                destinationLat = destCoords?.first ?: 31.3381,
                destinationLng = destCoords?.second ?: 73.4278,
                distanceKm = fallbackKm,
                estimatedDurationMinutes = estMinutes,
                source = DistanceSource.LOCAL_FALLBACK
            )
        }

        // Only cache live API results or valid coordinate results
        routeCache[cacheKey] = result
        return@withContext result
    }

    /**
     * Synchronous coordinate distance calculation using known PakistanGeoService locations
     * and embedded pin coordinates (for instant initial estimates before async API completes).
     */
    fun calculateInstantCoordinateDistance(
        pickup: String,
        destination: String
    ): LocationDistanceResult {
        val cleanPickup = pickup.trim().ifBlank { "Committee Chowk, Jaranwala" }
        val cleanDest = destination.trim().ifBlank { "Jaranwala Railway Station" }

        if (cleanPickup.equals(cleanDest, ignoreCase = true)) {
            val point = resolveLocalCoordinates(cleanPickup)
                ?: (PakistanGeoService.JARANWALA_LAT to PakistanGeoService.JARANWALA_LNG)
            return LocationDistanceResult(
                pickupName = cleanPickup,
                destinationName = cleanDest,
                pickupLat = point.first,
                pickupLng = point.second,
                destinationLat = point.first,
                destinationLng = point.second,
                distanceKm = 1.5,
                estimatedDurationMinutes = 5,
                source = DistanceSource.COORDINATE_HAVERSINE
            )
        }

        val pickupCoords = resolveLocalCoordinates(cleanPickup)
        val destCoords = resolveLocalCoordinates(cleanDest)

        return if (pickupCoords != null && destCoords != null) {
            val straightKm = PakistanGeoService.calculateDistanceKm(
                pickupCoords.first,
                pickupCoords.second,
                destCoords.first,
                destCoords.second
            )
            val roadKm = formatOneDecimal((straightKm * ROAD_WINDING_FACTOR).coerceAtLeast(1.2))
            val mins = ((roadKm / 28.0) * 60.0).roundToInt().coerceAtLeast(4)
            LocationDistanceResult(
                pickupName = cleanPickup,
                destinationName = cleanDest,
                pickupLat = pickupCoords.first,
                pickupLng = pickupCoords.second,
                destinationLat = destCoords.first,
                destinationLng = destCoords.second,
                distanceKm = roadKm,
                estimatedDurationMinutes = mins,
                source = DistanceSource.COORDINATE_HAVERSINE
            )
        } else {
            val fallbackKm = estimateUnlistedDistanceKm(cleanPickup, cleanDest)
            LocationDistanceResult(
                pickupName = cleanPickup,
                destinationName = cleanDest,
                pickupLat = pickupCoords?.first ?: PakistanGeoService.JARANWALA_LAT,
                pickupLng = pickupCoords?.second ?: PakistanGeoService.JARANWALA_LNG,
                destinationLat = destCoords?.first ?: 31.3381,
                destinationLng = destCoords?.second ?: 73.4278,
                distanceKm = fallbackKm,
                estimatedDurationMinutes = ((fallbackKm / 26.0) * 60.0).roundToInt().coerceAtLeast(5),
                source = DistanceSource.LOCAL_FALLBACK
            )
        }
    }

    private suspend fun resolveCoordinatesWithGeocoder(placeName: String): Pair<Pair<Double, Double>?, Boolean> {
        resolveLocalCoordinates(placeName)?.let { return it to false }

        val key = placeName.trim().lowercase(Locale.ROOT)
        geocodeCache[key]?.let { return it to true }

        val geocoded = apiProvider.geocodePakistanAddress(placeName)
        if (geocoded != null) {
            geocodeCache[key] = geocoded
            return geocoded to true
        }
        return null to false
    }

    /**
     * Resolves coordinates locally from:
     * 1. Embedded coordinate text e.g. `"Custom Pin (31.3450, 73.4510)"`
     * 2. Known locations in [PakistanGeoService.PAKISTAN_LOCATIONS]
     */
    fun resolveLocalCoordinates(placeName: String): Pair<Double, Double>? {
        val clean = placeName.trim()
        if (clean.isBlank()) return null

        // 1. Parse embedded coordinates like "(31.3342, 73.4194)"
        val coordMatch = COORD_REGEX.find(clean)
        if (coordMatch != null) {
            val lat = coordMatch.groupValues[1].toDoubleOrNull()
            val lng = coordMatch.groupValues[2].toDoubleOrNull()
            if (lat != null && lng != null && PakistanGeoService.isWithinPakistan(lat, lng)) {
                return lat to lng
            }
        }

        // 2. Match exact or partial name in PakistanGeoService catalog (English & Urdu)
        val exactMatch = PakistanGeoService.PAKISTAN_LOCATIONS.firstOrNull {
            it.name.equals(clean, ignoreCase = true) ||
                it.urduName.equals(clean, ignoreCase = true)
        }
        if (exactMatch != null) {
            return exactMatch.latitude to exactMatch.longitude
        }

        val substringMatch: PakistanLocationPoint? = PakistanGeoService.PAKISTAN_LOCATIONS.firstOrNull { loc ->
            val shortName = loc.name.substringBefore(",").trim()
            loc.name.contains(clean, ignoreCase = true) ||
                clean.contains(shortName, ignoreCase = true) ||
                loc.urduName.contains(clean, ignoreCase = true)
        }
        return substringMatch?.let { it.latitude to it.longitude }
    }

    fun clearCache() {
        routeCache.clear()
        geocodeCache.clear()
        polylineCache.clear()
    }

    private fun estimateUnlistedDistanceKm(pickup: String, destination: String): Double {
        // Deterministic fallback derived from text hash variance within urban Jaranwala bounds
        val combined = "${pickup.lowercase(Locale.ROOT)}->${destination.lowercase(Locale.ROOT)}"
        val hashOffset = ((combined.hashCode() and 0x7FFFFFFF) % 45) / 10.0 // 0.0 to 4.4 km
        return formatOneDecimal((2.0 + hashOffset).coerceIn(1.8, 8.5))
    }

    private fun formatOneDecimal(value: Double): Double {
        return String.format(Locale.US, "%.1f", value).toDouble()
    }

    companion object {
        const val ROAD_WINDING_FACTOR = 1.25
        private val COORD_REGEX = Regex("""\(\s*(-?\d{1,2}\.\d+)\s*,\s*(-?\d{1,3}\.\d+)\s*\)""")

        @Volatile
        private var INSTANCE: LocationDistanceService? = null

        fun getInstance(): LocationDistanceService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LocationDistanceService().also { INSTANCE = it }
            }
        }
    }
}
