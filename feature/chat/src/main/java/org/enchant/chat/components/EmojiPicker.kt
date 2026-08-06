package org.enchant.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.enchant.ui.icons.EnchantIcons

private val BrandPrimaryLight = Color(0xFF3A0D6E)
private val BrandPrimaryDark = Color(0xFFB388E3)
private val BrandTintLight = Color(0xFF7B1FA2)
private val BrandTintDark = Color(0xFFAB47BC)

@Composable
private fun brandPrimary(): Color = if (isSystemInDarkTheme()) BrandPrimaryDark else BrandPrimaryLight

@Composable
private fun brandTint(): Color = if (isSystemInDarkTheme()) BrandTintDark else BrandTintLight

data class EmojiCategory(val name: String, val icon: @Composable () -> Unit, val emojis: List<String>)

object EmojiData {
    val quickReactions = listOf("\u2764\uFE0F", "\uD83D\uDE0D", "\uD83D\uDE02", "\uD83D\uDE0A", "\uD83D\uDE4F", "\uD83D\uDC4D")

    val categories = listOf(
        EmojiCategory("Smileys", { Icon(EnchantIcons.smile, "Smileys") }, listOf(
            "\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE03", "\uD83D\uDE04", "\uD83D\uDE05",
            "\uD83D\uDE06", "\uD83D\uDE07", "\uD83D\uDE09", "\uD83D\uDE0A", "\uD83D\uDE0B", "\uD83D\uDE0D",
            "\uD83D\uDE0E", "\uD83D\uDE0F", "\uD83D\uDE10", "\uD83D\uDE11", "\uD83D\uDE12", "\uD83D\uDE13",
            "\uD83D\uDE14", "\uD83D\uDE15", "\uD83D\uDE16", "\uD83D\uDE17", "\uD83D\uDE18", "\uD83D\uDE19",
            "\uD83D\uDE1A", "\uD83D\uDE1B", "\uD83D\uDE1C", "\uD83D\uDE1D", "\uD83D\uDE1E", "\uD83D\uDE1F",
            "\uD83D\uDE20", "\uD83D\uDE21", "\uD83D\uDE22", "\uD83D\uDE23", "\uD83D\uDE24", "\uD83D\uDE25",
            "\uD83D\uDE26", "\uD83D\uDE27", "\uD83D\uDE28", "\uD83D\uDE29", "\uD83D\uDE2A", "\uD83D\uDE2B",
            "\uD83D\uDE2C", "\uD83D\uDE2D", "\uD83D\uDE2E", "\uD83D\uDE2F", "\uD83D\uDE30", "\uD83D\uDE31",
            "\uD83D\uDE32", "\uD83D\uDE33", "\uD83D\uDE34", "\uD83D\uDE35", "\u2764\uFE0F", "\uD83E\uDD0D",
            "\uD83D\uDE36", "\uD83D\uDE37", "\uD83E\uDD12", "\uD83E\uDD14", "\uD83E\uDD17"
        )),
        EmojiCategory("Gestures", { Icon(EnchantIcons.thumbsUp, "Gestures") }, listOf(
            "\uD83D\uDC4D", "\uD83D\uDC4E", "\uD83D\uDC4F", "\uD83D\uDC4C", "\u270A", "\u270B",
            "\uD83E\uDD1A", "\uD83E\uDD1B", "\uD83E\uDD1C", "\uD83E\uDD1D", "\uD83E\uDD1E",
            "\uD83E\uDD1F", "\uD83D\uDC4A", "\u270C\uFE0F", "\uD83E\uDD18",
            "\uD83D\uDC4B", "\uD83D\uDE4F", "\uD83D\uDE4C", "\uD83D\uDE4D", "\uD83D\uDE4E"
        )),
        EmojiCategory("Animals", { Icon(EnchantIcons.pawPrint, "Animals") }, listOf(
            "\uD83D\uDC36", "\uD83D\uDC31", "\uD83D\uDC2D", "\uD83D\uDC39", "\uD83D\uDC30",
            "\uD83D\uDC3B", "\uD83D\uDC3C", "\uD83D\uDC28", "\uD83D\uDC2F", "\uD83D\uDC27",
            "\uD83D\uDC26", "\uD83D\uDC24", "\uD83D\uDC22", "\uD83D\uDC2E", "\uD83D\uDC37",
            "\uD83D\uDC3D", "\uD83D\uDC38", "\uD83D\uDC35", "\uD83D\uDE48", "\uD83D\uDE49",
            "\uD83D\uDE4A", "\uD83D\uDC14", "\uD83D\uDC17", "\uD83D\uDC34", "\uD83D\uDC0E"
        )),
        EmojiCategory("Food", { Icon(EnchantIcons.utensils, "Food") }, listOf(
            "\uD83C\uDF4E", "\uD83C\uDF4F", "\uD83C\uDF50", "\uD83C\uDF51", "\uD83C\uDF52",
            "\uD83C\uDF53", "\uD83C\uDF54", "\uD83C\uDF55", "\uD83C\uDF56", "\uD83C\uDF57",
            "\uD83C\uDF58", "\uD83C\uDF59", "\uD83C\uDF5A", "\uD83C\uDF5B", "\uD83C\uDF5C",
            "\uD83C\uDF5D", "\uD83C\uDF5E", "\uD83C\uDF5F", "\uD83C\uDF60", "\uD83C\uDF61",
            "\uD83C\uDF62", "\uD83C\uDF63", "\uD83C\uDF64", "\uD83C\uDF65", "\uD83C\uDF66",
            "\uD83C\uDF67", "\uD83C\uDF68", "\uD83C\uDF69", "\u2615", "\uD83C\uDF75",
            "\uD83C\uDF76", "\uD83C\uDF7A", "\uD83C\uDF7B"
        )),
        EmojiCategory("Travel", { Icon(EnchantIcons.plane, "Travel") }, listOf(
            "\uD83C\uDF0D", "\uD83C\uDF0E", "\uD83C\uDF0F", "\uD83C\uDF10", "\uD83D\uDE80",
            "\uD83D\uDE81", "\uD83D\uDE82", "\uD83D\uDE83", "\uD83D\uDE84", "\uD83D\uDE85",
            "\uD83D\uDE86", "\uD83D\uDE87", "\uD83D\uDE88", "\uD83D\uDE89", "\uD83D\uDE8A",
            "\uD83D\uDE8B", "\uD83D\uDE8C", "\uD83D\uDE8D", "\uD83D\uDE8E", "\uD83D\uDE8F",
            "\uD83D\uDE90", "\uD83D\uDE91", "\uD83D\uDE92", "\u26F5", "\uD83D\uDEA2",
            "\uD83C\uDFD6\uFE0F", "\u26FA", "\uD83C\uDFD7\uFE0F"
        )),
        EmojiCategory("Symbols", { Icon(EnchantIcons.tag, "Symbols") }, listOf(
            "\u2764\uFE0F", "\uD83E\uDD0D", "\uD83D\uDC9B", "\uD83D\uDC9A", "\uD83D\uDC99",
            "\uD83D\uDC9C", "\uD83D\uDC9E", "\uD83D\uDC93", "\uD83D\uDC97", "\uD83D\uDC95",
            "\uD83E\uDD0E", "\u2728", "\u2B50", "\uD83D\uDD25", "\uD83C\uDF20",
            "\uD83C\uDF1F", "\uD83C\uDF0D", "\uD83C\uDF1E", "\u2600\uFE0F", "\uD83C\uDF26\uFE0F",
            "\u2601\uFE0F", "\u26C5", "\uD83C\uDF27\uFE0F", "\uD83C\uDF2B\uFE0F"
        ))
    )

    private val emojiNameMap = mapOf(
        "\uD83D\uDE00" to "grinning face", "\uD83D\uDE01" to "grinning face with smiling eyes",
        "\uD83D\uDE02" to "tears of joy laughing cry", "\uD83D\uDE03" to "big smile happy open mouth",
        "\uD83D\uDE04" to "smile happy open mouth smiling eyes", "\uD83D\uDE05" to "sweat smile cold",
        "\uD83D\uDE06" to "laugh happy tears cry", "\uD83D\uDE07" to "angel halo innocent",
        "\uD83D\uDE08" to "devil evil smile horn", "\uD83D\uDE09" to "wink tongue joke",
        "\uD83D\uDE0A" to "smiling face happy blush", "\uD83D\uDE0B" to "yum tasty delicious tongue",
        "\uD83D\uDE0C" to "relieved sigh calm", "\uD83D\uDE0D" to "heart eyes love crush",
        "\uD83D\uDE0E" to "sunglasses cool", "\uD83D\uDE0F" to "smirk confident",
        "\u2764\uFE0F" to "heart love red", "\uD83E\uDD0D" to "heart love",
        "\uD83D\uDC9B" to "yellow heart love", "\uD83D\uDC9A" to "purple heart love",
        "\uD83D\uDC99" to "blue heart love", "\uD83D\uDC9C" to "purple heart love",
        "\uD83D\uDC9E" to "revolving heart love", "\uD83D\uDC93" to "beating heart love",
        "\uD83D\uDC97" to "sparkling heart love", "\uD83D\uDC95" to "two hearts love",
        "\uD83E\uDD0E" to "sparkling star heart", "\u2728" to "sparkle star magic",
        "\uD83D\uDC4D" to "thumbs up like", "\uD83D\uDC4E" to "thumbs down dislike",
        "\uD83D\uDC4C" to "ok hand", "\u270B" to "raised hand stop",
        "\uD83E\uDD1E" to "fingers crossed luck", "\u270C\uFE0F" to "victory peace v",
        "\uD83D\uDC4A" to "fist punch", "\uD83D\uDC4F" to "clap applause",
        "\uD83D\uDE4C" to "pray thank you please", "\uD83D\uDE4F" to "folded hands pray",
        "\uD83D\uDE2D" to "cry sad tear", "\uD83D\uDE2E" to "shocked surprised open mouth",
        "\uD83D\uDE31" to "scream fear shocked", "\uD83D\uDE20" to "angry mad",
        "\uD83D\uDE21" to "pout angry mad", "\uD83D\uDE22" to "cry sad tearful",
        "\uD83D\uDE23" to "persevere struggle", "\uD83D\uDE24" to "triumph frustrated steam",
        "\uD83D\uDE25" to "disappointed sad", "\uD83D\uDE26" to "frowning sad",
        "\uD83D\uDE27" to "anguish distressed", "\uD83D\uDE28" to "fearful scared",
        "\u2600\uFE0F" to "sun sunny weather", "\u2601\uFE0F" to "cloud cloudy weather",
        "\u26C5" to "sun behind cloud", "\uD83C\uDF27\uFE0F" to "rain cloudy weather",
        "\u26A1" to "lightning zap electric", "\u2744\uFE0F" to "snowflake snow cold",
        "\uD83C\uDF2B\uFE0F" to "foggy mist weather", "\uD83C\uDF0D" to "earth globe world",
        "\uD83C\uDF0E" to "americas globe world", "\uD83C\uDF0F" to "asia australia globe world",
        "\uD83D\uDE80" to "rocket space", "\uD83D\uDE81" to "helicopter",
        "\uD83D\uDE82" to "police car emergency", "\uD83D\uDE83" to "police car emergency",
        "\uD83D\uDE84" to "taxi cab", "\uD83D\uDE85" to "car automobile",
        "\uD83D\uDE86" to "suv car", "\uD83D\uDE87" to "bus vehicle",
        "\uD83D\uDE88" to "trolleybus bus", "\uD83D\uDE89" to "train railway",
        "\uD83D\uDE8A" to "subway metro", "\uD83D\uDE8B" to "light rail train",
        "\uD83D\uDE8C" to "train station", "\uD83D\uDE8D" to "fire truck emergency",
        "\uD83D\uDE8E" to "ambulance emergency", "\uD83D\uDE8F" to "bus busstop",
        "\uD83D\uDE90" to "minibus bus", "\uD83D\uDE91" to "jeep suv",
        "\uD83C\uDFB5" to "musical note music", "\uD83C\uDFB6" to "notes music",
        "\uD83C\uDFA7" to "headphone music", "\uD83C\uDFB8" to "guitar musical instrument",
        "\uD83C\uDFB9" to "musical keyboard piano", "\uD83C\uDFBA" to "trumpet musical instrument",
        "\uD83C\uDFBB" to "violin musical instrument", "\uD83C\uDFAC" to "clapper board movie film",
        "\uD83D\uDCF7" to "camera photo", "\uD83D\uDCF8" to "camera photo flash",
        "\uD83D\uDCF9" to "video camera movie", "\uD83D\uDCFA" to "projector video film",
        "\uD83C\uDF7A" to "beer drink alcohol", "\uD83C\uDF7B" to "clinking beer glasses drink",
        "\uD83C\uDF77" to "wine glass drink", "\uD83C\uDF78" to "cocktail drink",
        "\u2615" to "coffee tea drink hot", "\uD83C\uDF75" to "tea beverage drink",
        "\uD83C\uDF76" to "sake drink japanese", "\uD83C\uDF7E" to "champagne bottle drink",
        "\uD83E\uDD42" to "clinking glasses toast", "\uD83C\uDF54" to "hamburger burger fast food",
        "\uD83C\uDF5F" to "fries french fast food", "\uD83C\uDF55" to "pizza italian food",
        "\uD83C\uDF2D" to "hot dog food", "\uD83C\uDF2E" to "taco mexican food",
        "\uD83C\uDF2F" to "burrito mexican food", "\uD83C\uDF57" to "poultry chicken leg food",
        "\uD83C\uDF56" to "meat food", "\uD83E\uDDC0" to "cheese food",
        "\uD83C\uDF5B" to "rice curry food", "\uD83C\uDF63" to "sushi japanese food",
        "\uD83C\uDF6F" to "honey pot sweet", "\uD83C\uDF6E" to "cake dessert birthday",
        "\uD83C\uDF70" to "birthday cake dessert", "\uD83C\uDF82" to "birthday cake celebration",
        "\uD83C\uDF89" to "party celebration confetti", "\uD83C\uDF8A" to "confetti party celebration",
        "\uD83C\uDF88" to "balloon party celebration", "\uD83C\uDF86" to "fireworks celebration",
        "\uD83C\uDF87" to "sparkler fireworks celebration", "\uD83C\uDF81" to "gift present wrapped",
        "\uD83C\uDF92" to "graduation cap school", "\uD83C\uDF93" to "briefcase work business",
        "\uD83D\uDCB0" to "money cash dollar", "\uD83D\uDCB5" to "dollar money cash",
        "\uD83D\uDCB2" to "coin money cash", "\uD83D\uDCB3" to "credit card payment",
        "\uD83D\uDCB6" to "euro money cash", "\uD83D\uDCB7" to "yen money cash",
        "\uD83D\uDCB8" to "money bag cash", "\uD83D\uDCB1" to "currency exchange money",
        "\uD83D\uDCB4" to "pound money cash", "\uD83D\uDC49" to "backhand index pointing right",
        "\uD83D\uDC48" to "backhand index pointing left", "\uD83D\uDC46" to "index pointing up",
        "\uD83D\uDC47" to "index pointing down", "\u261D\uFE0F" to "index pointing up",
        "\u270A" to "raised fist", "\uD83E\uDD1B" to "raised fist",
        "\uD83E\uDD1C" to "raised fist", "\uD83E\uDD1F" to "wave hello goodbye",
        "\u270D\uFE0F" to "writing hand", "\uD83D\uDC4B" to "wave hello goodbye hand",
        "\uD83E\uDD1A" to "raised back of hand", "\uD83E\uDD1D" to "handshake agreement deal",
        "\uD83D\uDC4C" to "ok hand sign", "\uD83E\uDD1E" to "crossed fingers luck"
    )

    fun searchEmoji(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        return categories.flatMap { it.emojis }.filter { emoji ->
            val name = emojiNameMap[emoji] ?: ""
            name.contains(query, ignoreCase = true)
        }.take(20)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiPickerSheet(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var selectedCategory by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val brand = brandPrimary()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .padding(horizontal = 12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search emoji", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                leadingIcon = { Icon(EnchantIcons.search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(EnchantIcons.x, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = brand,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    cursorColor = brand,
                    focusedLeadingIconColor = brand,
                    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Quick reactions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(EmojiData.quickReactions) { emoji ->
                    Surface(
                        onClick = { onEmojiSelected(emoji) },
                        shape = CircleShape,
                        color = brand.copy(alpha = 0.12f)
                    ) {
                        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 22.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (searchQuery.isNotBlank()) {
                val results = remember(searchQuery) { EmojiData.searchEmoji(searchQuery) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(results) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { onEmojiSelected(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EmojiData.categories.size) { idx ->
                        val selected = idx == selectedCategory
                        Surface(
                            onClick = { selectedCategory = idx },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) brand.copy(alpha = 0.12f) else Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CompositionLocalProvider(
                                    LocalContentColor provides
                                        (if (selected) brand else MaterialTheme.colorScheme.onSurfaceVariant)
                                ) {
                                    EmojiData.categories[idx].icon()
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(EmojiData.categories[selectedCategory].emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { onEmojiSelected(emoji) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji, fontSize = 22.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
