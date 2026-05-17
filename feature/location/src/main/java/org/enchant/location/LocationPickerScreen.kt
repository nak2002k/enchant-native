package org.enchant.location

import android.location.Geocoder
import android.util.Log
import androidx.compose.foundation.clickable
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
    val context = LocalContext.current

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
                },
                label = { Text("Search address") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth().weight(1f)
                    .then(Modifier).then(Modifier),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize().padding(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(String.format("%.6f, %.6f", latitude, longitude), style = MaterialTheme.typography.titleMedium)
                        if (address.isNotEmpty()) {
                            Text(address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Tap to use current location or search above", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    address = "Current location"
                    latitude = 0.0; longitude = 0.0
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.MyLocation, null)
                Spacer(Modifier.width(8.dp))
                Text("Use current location")
            }
        }
    }
}
