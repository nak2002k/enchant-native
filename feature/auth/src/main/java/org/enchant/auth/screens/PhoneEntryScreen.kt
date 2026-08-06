package org.enchant.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.ui.icons.EnchantIcons

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
    var phoneNumber by remember { mutableStateOf("+1") }
    var selectedCountry by remember { mutableStateOf(Country(1, "US", "United States", "\uD83C\uDDFA\uD83C\uDDF8")) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeatureSpacing.xxl)
        ) {
            Spacer(modifier = Modifier.height(FeatureSpacing.lg))
            FeatureBackButton(onClick = onNavigateBack)
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))
            FeatureTitle(text = "Enter your phone number")
            Spacer(modifier = Modifier.height(FeatureSpacing.sm))
            FeatureSubtitle(text = "You'll receive a verification code")
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))

            val cardShape = RoundedCornerShape(FeatureRadii.card)
            val borderColor = when {
                errorMessage != null -> Red
                focused -> BrandBlue
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, borderColor, cardShape)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showCountryPicker = true }
                        .padding(horizontal = FeatureSpacing.lg, vertical = FeatureSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedCountry.emoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(FeatureSpacing.md))
                    Text(
                        text = "${selectedCountry.name} (+${selectedCountry.code})",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        EnchantIcons.chevronRight,
                        contentDescription = "Change country",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(start = FeatureSpacing.lg)
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FeatureSpacing.lg, vertical = FeatureSpacing.xl)
                ) {
                    BasicTextField(
                        value = phoneNumber,
                        onValueChange = { newValue ->
                            val cleaned = newValue.filter { it.isDigit() || it == '+' }
                            if (cleaned.length <= 16) {
                                phoneNumber = cleaned
                                onPhoneNumberChanged(cleaned)
                            }
                        },
                        textStyle = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        cursorBrush = SolidColor(BrandBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focused = it.isFocused },
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.Center) {
                                if (phoneNumber.isEmpty()) {
                                    Text(
                                        text = "555 123 4567",
                                        fontSize = 22.sp,
                                        color = Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(FeatureSpacing.sm))
                Text(
                    text = errorMessage,
                    color = Red,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(FeatureSpacing.xxl))

            if (isLoading) {
                CircularProgressIndicator(
                    color = BrandBlue,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                EnchantPrimaryButton(
                    text = "Continue",
                    onClick = {
                        val fullNumber = if (phoneNumber.startsWith("+")) phoneNumber
                            else "+${selectedCountry.code}$phoneNumber"
                        onPhoneNumberSubmitted(fullNumber)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = phoneNumber.length > 1
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "By tapping Continue, you agree to our\nTerms of Service and Privacy Policy.",
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(FeatureSpacing.lg))
        }
    }

    if (showCountryPicker) {
        CountryCodePickerScreen(
            onCountrySelected = { country ->
                selectedCountry = country
                onCountrySelected(country)
                phoneNumber = "+${country.code}"
                onPhoneNumberChanged("+${country.code}")
                showCountryPicker = false
            },
            onDismiss = { showCountryPicker = false },
            currentCountry = selectedCountry
        )
    }
}
