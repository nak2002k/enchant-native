package org.enchant.auth.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class Country(val code: Int, val region: String, val name: String, val emoji: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneEntryScreen(
    onCountrySelected: (Country) -> Unit,
    onPhoneNumberChanged: (String) -> Unit,
    onPhoneNumberSubmitted: (String) -> Unit,
    onNavigateBack: () -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var phoneNumber by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf(Country(1, "US", "United States", "\uD83C\uDDFA\uD83C\uDDF8")) }
    var showCountryPicker by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            Text("Enter your phone number", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You'll receive a verification code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = { showCountryPicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${selectedCountry.emoji} ${selectedCountry.name} (+${selectedCountry.code})")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { newValue ->
                    val cleaned = newValue.filter { it.isDigit() || it == '+' }
                    if (cleaned.length <= 16) {
                        phoneNumber = cleaned
                        onPhoneNumberChanged(cleaned)
                    }
                },
                label = { Text("Phone number") },
                placeholder = { Text("+1 555 123 4567") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMessage != null,
                supportingText = errorMessage?.let { { Text(it) } }
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { onPhoneNumberSubmitted(phoneNumber) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = phoneNumber.matches(Regex("""^\+[1-9]\d{1,14}$"""))
                ) {
                    Text("Continue")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TextButton(onClick = onNavigateBack) {
                Text("Back")
            }
        }
    }

    if (showCountryPicker) {
        CountryCodePickerScreen(
            onCountrySelected = { country ->
                selectedCountry = country
                onCountrySelected(country)
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false }
        )
    }
}
