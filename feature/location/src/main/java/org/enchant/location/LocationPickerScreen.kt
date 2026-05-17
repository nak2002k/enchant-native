package org.enchant.location

import android.location.Geocoder
import android.location.LocationManager
import android.util.Log
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    val context = LocalContext.current

    val scope = rememberCoroutineScope()

    fun reverseGeocode(lat: Double, lng: Double) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val results = geocoder.getFromLocation(lat, lng, 1)
                    address = if (!results.isNullOrEmpty()) {
                        results[0].getAddressLine(0) ?: "$lat, $lng"
                    } else {
                        "$lat, $lng"
                    }
                } catch (e: Exception) {
                    address = "$lat, $lng"
                }
            }
        }
    }

    fun requestCurrentLocation() {
        isGettingLocation = true
        try {
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            val provider = when {
                isGpsEnabled -> LocationManager.GPS_PROVIDER
                isNetworkEnabled -> LocationManager.NETWORK_PROVIDER
                else -> {
                    isGettingLocation = false
                    return
                }
            }

            locationManager.getLastKnownLocation(provider)?.let { loc ->
                latitude = loc.latitude
                longitude = loc.longitude
                reverseGeocode(loc.latitude, loc.longitude)
            }

            locationManager.requestSingleUpdate(provider, object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    reverseGeocode(loc.latitude, loc.longitude)
                    isGettingLocation = false
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) { isGettingLocation = false }
            }, null)
        } catch (e: SecurityException) {
            isGettingLocation = false
        } catch (e: Exception) {
            Log.w("Location", "Location fetch failed: ${e.message}")
            isGettingLocation = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Location") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { onLocationSelected(latitude, longitude, address) }) {
                        Text("Send", style = MaterialTheme.typography.labelLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    if (query.isNotEmpty()) {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val geocoder = Geocoder(context, Locale.getDefault())
                                    val results = geocoder.getFromLocationName(query, 1)
                                    if (!results.isNullOrEmpty()) {
                                        latitude = results[0].latitude
                                        longitude = results[0].longitude
                                        address = results[0].getAddressLine(0) ?: query
                                    }
                                } catch (e: Exception) { Log.w("Location", "Fetch failed: ${e.message}") }
                            }
                        }
                    }
                },
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
                        Text(String.format("%.6f, %.6f", latitude, longitude), style = MaterialTheme.typography.titleMedium)
                        if (address.isNotEmpty()) {
                            Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (isGettingLocation) "Getting current location..."
                            else "Search above or use current location",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { requestCurrentLocation() },
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
