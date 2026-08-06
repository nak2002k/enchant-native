package org.enchant.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.enchant.ui.icons.EnchantIcons

@Composable
fun UsernamePickerScreen(
    onUsernameEntered: (String) -> Unit,
    onSkip: () -> Unit,
    onCheckAvailability: suspend (String) -> Boolean?,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var username by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }
    var isAvailable by remember { mutableStateOf<Boolean?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }

    val statusText = when {
        isChecking -> "Checking availability..."
        isAvailable == true -> "Available!"
        isAvailable == false -> "Username taken"
        isAvailable == null && username.length >= 3 && !isChecking -> "Could not check availability"
        else -> ""
    }
    val hasError = errorMessage != null || isAvailable == false

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = FeatureSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(FeatureSpacing.xxl * 2))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BrandBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = username.take(2).uppercase().ifEmpty { "E" },
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(FeatureSpacing.xxl))
            FeatureTitle(text = "Choose your username")
            Spacer(modifier = Modifier.height(FeatureSpacing.sm))
            FeatureSubtitle(text = "This is your unique @handle")
            Spacer(modifier = Modifier.height(FeatureSpacing.xxxl))

            val fieldShape = RoundedCornerShape(FeatureRadii.card)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .border(
                        width = 1.5.dp,
                        color = when {
                            hasError -> Red
                            focused -> BrandBlue
                            else -> MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = fieldShape
                    )
                    .padding(horizontal = FeatureSpacing.lg, vertical = FeatureSpacing.xl)
            ) {
                BasicTextField(
                    value = username,
                    onValueChange = { newValue ->
                        val cleaned = newValue.lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }
                        if (cleaned.length <= 32) {
                            username = cleaned
                            searchJob?.cancel()
                            if (cleaned.length >= 3) {
                                isChecking = true
                                isAvailable = null
                                searchJob = scope.launch {
                                    delay(300)
                                    val available = onCheckAvailability(cleaned)
                                    isAvailable = available
                                    isChecking = false
                                }
                            } else {
                                isAvailable = null
                                isChecking = false
                            }
                        }
                    },
                    textStyle = TextStyle(
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(BrandBlue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.isFocused },
                    decorationBox = { innerTextField ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(FeatureSpacing.xs))
                            Box(modifier = Modifier.weight(1f)) {
                                if (username.isEmpty()) {
                                    Text(
                                        text = "john_doe",
                                        fontSize = 17.sp,
                                        color = Gray
                                    )
                                }
                                innerTextField()
                            }
                            when {
                                isChecking -> CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = BrandBlue
                                )
                                isAvailable == true -> Icon(
                                    EnchantIcons.check,
                                    contentDescription = "Available",
                                    tint = CallGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                isAvailable == false -> Icon(
                                    EnchantIcons.x,
                                    contentDescription = "Taken",
                                    tint = Red,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(FeatureSpacing.sm))

            when {
                errorMessage != null -> Text(
                    text = errorMessage,
                    color = Red,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                statusText.isNotEmpty() -> Text(
                    text = statusText,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = when {
                        isAvailable == true -> CallGreen
                        isAvailable == false -> Red
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
                    onClick = { onUsernameEntered(username) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = username.length in 3..32 && isAvailable == true
                )
            }

            Spacer(modifier = Modifier.height(FeatureSpacing.sm))

            FeatureTextButton(
                text = "Skip for now",
                onClick = onSkip,
                enabled = !isLoading
            )
            Spacer(modifier = Modifier.height(FeatureSpacing.lg))
        }
    }
}
