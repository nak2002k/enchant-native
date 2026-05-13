package org.enchant.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryCodePickerScreen(
    onCountrySelected: (Country) -> Unit,
    onDismiss: () -> Unit
) {
    val countries = remember {
        listOf(
            Country(1, "US", "United States", "\uD83C\uDDFA\uD83C\uDDF8"),
            Country(44, "GB", "United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7"),
            Country(91, "IN", "India", "\uD83C\uDDEE\uD83C\uDDF3"),
            Country(81, "JP", "Japan", "\uD83C\uDDEF\uD83C\uDDF5"),
            Country(86, "CN", "China", "\uD83C\uDDE8\uD83C\uDDF3"),
            Country(49, "DE", "Germany", "\uD83C\uDDE9\uD83C\uDDEA"),
            Country(33, "FR", "France", "\uD83C\uDDEB\uD83C\uDDF7"),
            Country(61, "AU", "Australia", "\uD83C\uDDE6\uD83C\uDDFA"),
            Country(7, "RU", "Russia", "\uD83C\uDDF7\uD83C\uDDFA"),
            Country(55, "BR", "Brazil", "\uD83C\uDDE7\uD83C\uDDF7"),
            Country(82, "KR", "South Korea", "\uD83C\uDDF0\uD83C\uDDF7"),
            Country(34, "ES", "Spain", "\uD83C\uDDEA\uD83C\uDDF8"),
            Country(39, "IT", "Italy", "\uD83C\uDDEE\uD83C\uDDF9"),
            Country(31, "NL", "Netherlands", "\uD83C\uDDF3\uD83C\uDDF1"),
            Country(46, "SE", "Sweden", "\uD83C\uDDF8\uD83C\uDDEA"),
            Country(41, "CH", "Switzerland", "\uD83C\uDDE8\uD83C\uDDED"),
            Country(1, "CA", "Canada", "\uD83C\uDDE8\uD83C\uDDE6"),
            Country(52, "MX", "Mexico", "\uD83C\uDDF2\uD83C\uDDFD"),
            Country(54, "AR", "Argentina", "\uD83C\uDDE6\uD83C\uDDF7"),
            Country(56, "CL", "Chile", "\uD83C\uDDE8\uD83C\uDDF1"),
            Country(57, "CO", "Colombia", "\uD83C\uDDE8\uD83C\uDDF4"),
            Country(27, "ZA", "South Africa", "\uD83C\uDDFF\uD83C\uDDE6"),
            Country(20, "EG", "Egypt", "\uD83C\uDDEA\uD83C\uDDEC"),
            Country(234, "NG", "Nigeria", "\uD83C\uDDF3\uD83C\uDDEC"),
            Country(971, "AE", "UAE", "\uD83C\uDDE6\uD83C\uDDEA"),
            Country(65, "SG", "Singapore", "\uD83C\uDDF8\uD83C\uDDEC"),
            Country(64, "NZ", "New Zealand", "\uD83C\uDDF3\uD83C\uDDFF"),
            Country(48, "PL", "Poland", "\uD83C\uDDF5\uD83C\uDDF1"),
            Country(30, "GR", "Greece", "\uD83C\uDDEC\uD83C\uDDF7"),
            Country(351, "PT", "Portugal", "\uD83C\uDDF5\uD83C\uDDF9")
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, countries) {
        if (searchQuery.isBlank()) countries
        else countries.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.region.contains(searchQuery, ignoreCase = true) ||
            it.code.toString().contains(searchQuery)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Country") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(filtered) { country ->
                        TextButton(
                            onClick = { onCountrySelected(country) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${country.emoji} ${country.name} (+${country.code})")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
