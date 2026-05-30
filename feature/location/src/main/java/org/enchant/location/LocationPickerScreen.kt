package org.enchant.location

import android.Manifest
import android.location.Geocoder
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(
    initialLatitude: Double = 0.0,
    initialLongitude: Double = 0.0,
    onLocationSelected: (Double, Double, String) -> Unit,
    onBack: () -> Unit
) {
    var latitude by remember { mutableDoubleStateOf(initialLatitude) }
    var longitude by remember { mutableDoubleStateOf(initialLongitude) }
    var address by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var isGettingLocation by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var lastLocationTimestamp by remember { mutableLongStateOf(0L) }
    var permissionDenied by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var locationListener by remember { mutableStateOf<LocationListener?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            locationListener?.let { listener ->
                try {
                    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
                    lm.removeUpdates(listener)
                } catch (_: Exception) { }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        permissionDenied = !fineGranted && !coarseGranted
        if (!permissionDenied) {
            requestCurrentLocationInternal(
                context = context,
                scope = scope,
                onLocationObtained = { lat, lng, addr, timestamp ->
                    latitude = lat
                    longitude = lng
                    address = addr
                    lastLocationTimestamp = timestamp
                    isGettingLocation = false
                    locationError = null
                },
                onError = { errorMsg ->
                    locationError = errorMsg
                    isGettingLocation = false
                },
                onProviderDisabled = { isGettingLocation = false }
            )
        } else {
            isGettingLocation = false
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isEmpty()) return@LaunchedEffect
        locationError = null
        kotlinx.coroutines.delay(400)
        if (searchQuery.isEmpty()) return@LaunchedEffect
        val query = searchQuery
        withContext(Dispatchers.IO) {
            try {
                @Suppress("DEPRECATION")
                val geocoder = Geocoder(context, Locale.getDefault())
                val results = geocoder.getFromLocationName(query, 1)
                if (!results.isNullOrEmpty()) {
                    val result = results[0]
                    latitude = result.latitude
                    longitude = result.longitude
                    address = sanitizeAddress(result.getAddressLine(0) ?: query)
                }
            } catch (_: Exception) { }
        }
    }

    DisposableEffect(onBack) {
        onDispose {
            latitude = 0.0
            longitude = 0.0
            address = ""
            searchQuery = ""
            locationError = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Location") },
                navigationIcon = {
                    IconButton(onClick = {
                        latitude = 0.0
                        longitude = 0.0
                        address = ""
                        searchQuery = ""
                        locationError = null
                        onBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onLocationSelected(latitude, longitude, sanitizeAddress(address)) },
                        enabled = latitude != 0.0 || longitude != 0.0
                    ) {
                        Text("Send", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search address") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(String.format(Locale.US, "%.6f, %.6f", latitude, longitude), style = MaterialTheme.typography.titleMedium)
                        if (address.isNotEmpty()) {
                            Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                        if (lastLocationTimestamp > 0 && isGettingLocation) {
                            val ageMs = System.currentTimeMillis() - lastLocationTimestamp
                            val ageMinutes = ageMs / 60_000
                            if (ageMinutes > 5) {
                                Text("Cached location (${ageMinutes} min old)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        val statusText: String = when {
                            isGettingLocation -> "Getting current location..."
                            locationError != null -> locationError!!
                            permissionDenied -> "Location permission denied"
                            else -> "Search above or use current location"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (locationError != null || permissionDenied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    isGettingLocation = true
                    locationError = null
                    permissionLauncher.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isGettingLocation
            ) {
                Icon(Icons.Default.MyLocation, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isGettingLocation) "Locating..." else "Use current location")
            }
        }
    }
}

@android.annotation.SuppressLint("MissingPermission")
private fun requestCurrentLocationInternal(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onLocationObtained: (Double, Double, String, Long) -> Unit,
    onError: (String) -> Unit,
    onProviderDisabled: () -> Unit
) {
    try {
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val provider = when {
            isGpsEnabled -> LocationManager.GPS_PROVIDER
            isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
            else -> {
                onError("No location provider available")
                return
            }
        }

        var listener: LocationListener? = null
        listener = object : LocationListener {
            override fun onLocationChanged(loc: android.location.Location) {
                val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) loc.elapsedRealtimeNanos else 0L
                reverseGeocodeAddress(context, loc.latitude, loc.longitude) { addr ->
                    onLocationObtained(loc.latitude, loc.longitude, addr, timestamp)
                }
                try {
                    locationManager.removeUpdates(this)
                } catch (_: Exception) { }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                onProviderDisabled()
            }
        }

        try {
            locationManager.requestSingleUpdate(provider, listener, null)
        } catch (e: Exception) {
            onError("Location request failed")
            return
        }

        val lastKnown = try {
            locationManager.getLastKnownLocation(provider)
        } catch (_: Exception) { null }

        if (lastKnown != null) {
            val ageMs = System.currentTimeMillis() - lastKnown.time
            if (ageMs < 120_000) {
                reverseGeocodeAddress(context, lastKnown.latitude, lastKnown.longitude) { addr ->
                    onLocationObtained(lastKnown.latitude, lastKnown.longitude, addr, lastKnown.time)
                }
            }
        }
    } catch (_: SecurityException) {
        onError("Location permission denied")
    } catch (e: Exception) {
        onError("Location error: ${e.message}")
    }
}

private fun reverseGeocodeAddress(context: android.content.Context, lat: Double, lng: Double, callback: (String) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale.getDefault())
            val results = geocoder.getFromLocation(lat, lng, 1)
            val addr = if (!results.isNullOrEmpty()) {
                sanitizeAddress(results[0].getAddressLine(0) ?: "$lat, $lng")
            } else {
                "$lat, $lng"
            }
            callback(addr)
        } catch (_: Exception) {
            callback("$lat, $lng")
        }
    }
}

private fun sanitizeAddress(raw: String): String {
    if (raw.isBlank()) return raw
    return raw.take(500).replace(Regex("[\n\r\t]"), " ").trim()
}