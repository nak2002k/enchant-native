package org.enchant.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.enchant.ui.icons.EnchantIcons

@Composable
fun CountryCodePickerScreen(
    onCountrySelected: (Country) -> Unit,
    onDismiss: () -> Unit,
    currentCountry: Country? = null
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
            Country(93, "AF", "Afghanistan", "\uD83C\uDDE6\uD83C\uDDEB"),
            Country(355, "AL", "Albania", "\uD83C\uDDE6\uD83C\uDDF1"),
            Country(213, "DZ", "Algeria", "\uD83C\uDDE9\uD83C\uDDFF"),
            Country(54, "AR", "Argentina", "\uD83C\uDDE6\uD83C\uDDF7"),
            Country(374, "AM", "Armenia", "\uD83C\uDDE6\uD83C\uDDF2"),
            Country(61, "AU", "Australia", "\uD83C\uDDE6\uD83C\uDDFA"),
            Country(43, "AT", "Austria", "\uD83C\uDDE6\uD83C\uDDF9"),
            Country(994, "AZ", "Azerbaijan", "\uD83C\uDDE6\uD83C\uDDFF"),
            Country(973, "BH", "Bahrain", "\uD83C\uDDE7\uD83C\uDDED"),
            Country(880, "BD", "Bangladesh", "\uD83C\uDDE7\uD83C\uDDE9"),
            Country(375, "BY", "Belarus", "\uD83C\uDDE7\uD83C\uDDFE"),
            Country(32, "BE", "Belgium", "\uD83C\uDDE7\uD83C\uDDEA"),
            Country(591, "BO", "Bolivia", "\uD83C\uDDE7\uD83C\uDDF4"),
            Country(387, "BA", "Bosnia", "\uD83C\uDDE7\uD83C\uDDE6"),
            Country(55, "BR", "Brazil", "\uD83C\uDDE7\uD83C\uDDF7"),
            Country(359, "BG", "Bulgaria", "\uD83C\uDDE7\uD83C\uDDEC"),
            Country(226, "BF", "Burkina Faso", "\uD83C\uDDE7\uD83C\uDDEB"),
            Country(855, "KH", "Cambodia", "\uD83C\uDDF0\uD83C\uDDED"),
            Country(237, "CM", "Cameroon", "\uD83C\uDDE8\uD83C\uDDF2"),
            Country(1, "CA", "Canada", "\uD83C\uDDE8\uD83C\uDDE6"),
            Country(235, "TD", "Chad", "\uD83C\uDDF9\uD83C\uDDE9"),
            Country(56, "CL", "Chile", "\uD83C\uDDE8\uD83C\uDDF1"),
            Country(86, "CN", "China", "\uD83C\uDDE8\uD83C\uDDF3"),
            Country(57, "CO", "Colombia", "\uD83C\uDDE8\uD83C\uDDF4"),
            Country(506, "CR", "Costa Rica", "\uD83C\uDDE8\uD83C\uDDF7"),
            Country(385, "HR", "Croatia", "\uD83C\uDDED\uD83C\uDDF7"),
            Country(53, "CU", "Cuba", "\uD83C\uDDE8\uD83C\uDDFA"),
            Country(357, "CY", "Cyprus", "\uD83C\uDDE8\uD83C\uDDFE"),
            Country(420, "CZ", "Czech Republic", "\uD83C\uDDE8\uD83C\uDDFF"),
            Country(45, "DK", "Denmark", "\uD83C\uDDE9\uD83C\uDDF0"),
            Country(1809, "DO", "Dominican Republic", "\uD83C\uDDE9\uD83C\uDDF4"),
            Country(593, "EC", "Ecuador", "\uD83C\uDDEA\uD83C\uDDE8"),
            Country(20, "EG", "Egypt", "\uD83C\uDDEA\uD83C\uDDEC"),
            Country(503, "SV", "El Salvador", "\uD83C\uDDF8\uD83C\uDDFB"),
            Country(372, "EE", "Estonia", "\uD83C\uDDEA\uD83C\uDDEA"),
            Country(251, "ET", "Ethiopia", "\uD83C\uDDEA\uD83C\uDDF9"),
            Country(679, "FJ", "Fiji", "\uD83C\uDDEB\uD83C\uDDEF"),
            Country(358, "FI", "Finland", "\uD83C\uDDEB\uD83C\uDDEE"),
            Country(33, "FR", "France", "\uD83C\uDDEB\uD83C\uDDF7"),
            Country(241, "GA", "Gabon", "\uD83C\uDDEC\uD83C\uDDE6"),
            Country(995, "GE", "Georgia", "\uD83C\uDDEC\uD83C\uDDEA"),
            Country(49, "DE", "Germany", "\uD83C\uDDE9\uD83C\uDDEA"),
            Country(233, "GH", "Ghana", "\uD83C\uDDEC\uD83C\uDDED"),
            Country(30, "GR", "Greece", "\uD83C\uDDEC\uD83C\uDDF7"),
            Country(502, "GT", "Guatemala", "\uD83C\uDDEC\uD83C\uDDF9"),
            Country(224, "GN", "Guinea", "\uD83C\uDDEC\uD83C\uDDF3"),
            Country(509, "HT", "Haiti", "\uD83C\uDDED\uD83C\uDDF9"),
            Country(504, "HN", "Honduras", "\uD83C\uDDED\uD83C\uDDF3"),
            Country(852, "HK", "Hong Kong", "\uD83C\uDDED\uD83C\uDDF0"),
            Country(36, "HU", "Hungary", "\uD83C\uDDED\uD83C\uDDFA"),
            Country(354, "IS", "Iceland", "\uD83C\uDDEE\uD83C\uDDF8"),
            Country(91, "IN", "India", "\uD83C\uDDEE\uD83C\uDDF3"),
            Country(62, "ID", "Indonesia", "\uD83C\uDDEE\uD83C\uDDE9"),
            Country(98, "IR", "Iran", "\uD83C\uDDEE\uD83C\uDDF7"),
            Country(964, "IQ", "Iraq", "\uD83C\uDDEE\uD83C\uDDF6"),
            Country(353, "IE", "Ireland", "\uD83C\uDDEE\uD83C\uDDEA"),
            Country(972, "IL", "Israel", "\uD83C\uDDEE\uD83C\uDDF1"),
            Country(39, "IT", "Italy", "\uD83C\uDDEE\uD83C\uDDF9"),
            Country(225, "CI", "Ivory Coast", "\uD83C\uDDE8\uD83C\uDDEE"),
            Country(81, "JP", "Japan", "\uD83C\uDDEF\uD83C\uDDF5"),
            Country(962, "JO", "Jordan", "\uD83C\uDDEF\uD83C\uDDF4"),
            Country(7, "KZ", "Kazakhstan", "\uD83C\uDDF0\uD83C\uDDFF"),
            Country(254, "KE", "Kenya", "\uD83C\uDDF0\uD83C\uDDEA"),
            Country(965, "KW", "Kuwait", "\uD83C\uDDF0\uD83C\uDDFC"),
            Country(996, "KG", "Kyrgyzstan", "\uD83C\uDDF0\uD83C\uDDEC"),
            Country(856, "LA", "Laos", "\uD83C\uDDF1\uD83C\uDDE6"),
            Country(371, "LV", "Latvia", "\uD83C\uDDF1\uD83C\uDDFB"),
            Country(961, "LB", "Lebanon", "\uD83C\uDDF1\uD83C\uDDE7"),
            Country(218, "LY", "Libya", "\uD83C\uDDF1\uD83C\uDDFE"),
            Country(423, "LI", "Liechtenstein", "\uD83C\uDDF1\uD83C\uDDEE"),
            Country(370, "LT", "Lithuania", "\uD83C\uDDF1\uD83C\uDDF9"),
            Country(352, "LU", "Luxembourg", "\uD83C\uDDF1\uD83C\uDDFA"),
            Country(389, "MK", "Macedonia", "\uD83C\uDDF2\uD83C\uDDF0"),
            Country(60, "MY", "Malaysia", "\uD83C\uDDF2\uD83C\uDDFE"),
            Country(960, "MV", "Maldives", "\uD83C\uDDF2\uD83C\uDDFB"),
            Country(223, "ML", "Mali", "\uD83C\uDDF2\uD83C\uDDE9"),
            Country(356, "MT", "Malta", "\uD83C\uDDF2\uD83C\uDDF9"),
            Country(230, "MU", "Mauritius", "\uD83C\uDDF2\uD83C\uDDFA"),
            Country(52, "MX", "Mexico", "\uD83C\uDDF2\uD83C\uDDFD"),
            Country(373, "MD", "Moldova", "\uD83C\uDDF2\uD83C\uDDE9"),
            Country(976, "MN", "Mongolia", "\uD83C\uDDF2\uD83C\uDDF3"),
            Country(382, "ME", "Montenegro", "\uD83C\uDDF2\uD83C\uDDEA"),
            Country(212, "MA", "Morocco", "\uD83C\uDDF2\uD83C\uDDE6"),
            Country(258, "MZ", "Mozambique", "\uD83C\uDDF2\uD83C\uDDFF"),
            Country(95, "MM", "Myanmar", "\uD83C\uDDF2\uD83C\uDDF2"),
            Country(264, "NA", "Namibia", "\uD83C\uDDF3\uD83C\uDDE6"),
            Country(977, "NP", "Nepal", "\uD83C\uDDF3\uD83C\uDDF5"),
            Country(31, "NL", "Netherlands", "\uD83C\uDDF3\uD83C\uDDF1"),
            Country(64, "NZ", "New Zealand", "\uD83C\uDDF3\uD83C\uDDFF"),
            Country(505, "NI", "Nicaragua", "\uD83C\uDDF3\uD83C\uDDEE"),
            Country(227, "NE", "Niger", "\uD83C\uDDF3\uD83C\uDDEA"),
            Country(234, "NG", "Nigeria", "\uD83C\uDDF3\uD83C\uDDEC"),
            Country(47, "NO", "Norway", "\uD83C\uDDF3\uD83C\uDDF4"),
            Country(968, "OM", "Oman", "\uD83C\uDDF4\uD83C\uDDF2"),
            Country(92, "PK", "Pakistan", "\uD83C\uDDF5\uD83C\uDDF0"),
            Country(970, "PS", "Palestine", "\uD83C\uDDF5\uD83C\uDDF8"),
            Country(507, "PA", "Panama", "\uD83C\uDDF5\uD83C\uDDE6"),
            Country(595, "PY", "Paraguay", "\uD83C\uDDF5\uD83C\uDDFE"),
            Country(51, "PE", "Peru", "\uD83C\uDDF5\uD83C\uDDEA"),
            Country(63, "PH", "Philippines", "\uD83C\uDDF5\uD83C\uDDED"),
            Country(48, "PL", "Poland", "\uD83C\uDDF5\uD83C\uDDF1"),
            Country(351, "PT", "Portugal", "\uD83C\uDDF5\uD83C\uDDF9"),
            Country(974, "QA", "Qatar", "\uD83C\uDDF6\uD83C\uDDE6"),
            Country(40, "RO", "Romania", "\uD83C\uDDF7\uD83C\uDDF4"),
            Country(7, "RU", "Russia", "\uD83C\uDDF7\uD83C\uDDFA"),
            Country(250, "RW", "Rwanda", "\uD83C\uDDF7\uD83C\uDDFC"),
            Country(966, "SA", "Saudi Arabia", "\uD83C\uDDF8\uD83C\uDDE6"),
            Country(221, "SN", "Senegal", "\uD83C\uDDF8\uD83C\uDDF3"),
            Country(381, "RS", "Serbia", "\uD83C\uDDF7\uD83C\uDDF8"),
            Country(232, "SL", "Sierra Leone", "\uD83C\uDDF8\uD83C\uDDF1"),
            Country(65, "SG", "Singapore", "\uD83C\uDDF8\uD83C\uDDEC"),
            Country(421, "SK", "Slovakia", "\uD83C\uDDF8\uD83C\uDDF0"),
            Country(386, "SI", "Slovenia", "\uD83C\uDDF8\uD83C\uDDEE"),
            Country(252, "SO", "Somalia", "\uD83C\uDDF8\uD83C\uDDF4"),
            Country(27, "ZA", "South Africa", "\uD83C\uDDFF\uD83C\uDDE6"),
            Country(82, "KR", "South Korea", "\uD83C\uDDF0\uD83C\uDDF7"),
            Country(211, "SS", "South Sudan", "\uD83C\uDDF8\uD83C\uDDF8"),
            Country(34, "ES", "Spain", "\uD83C\uDDEA\uD83C\uDDF8"),
            Country(94, "LK", "Sri Lanka", "\uD83C\uDDF1\uD83C\uDDF0"),
            Country(249, "SD", "Sudan", "\uD83C\uDDF8\uD83C\uDDE9"),
            Country(597, "SR", "Suriname", "\uD83C\uDDF8\uD83C\uDDF7"),
            Country(46, "SE", "Sweden", "\uD83C\uDDF8\uD83C\uDDEA"),
            Country(41, "CH", "Switzerland", "\uD83C\uDDE8\uD83C\uDDED"),
            Country(963, "SY", "Syria", "\uD83C\uDDF8\uD83C\uDDFE"),
            Country(886, "TW", "Taiwan", "\uD83C\uDDF9\uD83C\uDDFC"),
            Country(992, "TJ", "Tajikistan", "\uD83C\uDDF9\uD83C\uDDEF"),
            Country(255, "TZ", "Tanzania", "\uD83C\uDDF9\uD83C\uDDFF"),
            Country(66, "TH", "Thailand", "\uD83C\uDDF9\uD83C\uDDED"),
            Country(228, "TG", "Togo", "\uD83C\uDDF9\uD83C\uDDEC"),
            Country(216, "TN", "Tunisia", "\uD83C\uDDF9\uD83C\uDDF3"),
            Country(90, "TR", "Turkey", "\uD83C\uDDF9\uD83C\uDDF7"),
            Country(993, "TM", "Turkmenistan", "\uD83C\uDDF9\uD83C\uDDF2"),
            Country(256, "UG", "Uganda", "\uD83C\uDDFA\uD83C\uDDEC"),
            Country(380, "UA", "Ukraine", "\uD83C\uDDFA\uD83C\uDDE6"),
            Country(971, "AE", "UAE", "\uD83C\uDDE6\uD83C\uDDEA"),
            Country(44, "GB", "United Kingdom", "\uD83C\uDDEC\uD83C\uDDE7"),
            Country(1, "US", "United States", "\uD83C\uDDFA\uD83C\uDDF8"),
            Country(598, "UY", "Uruguay", "\uD83C\uDDFA\uD83C\uDDFE"),
            Country(998, "UZ", "Uzbekistan", "\uD83C\uDDFA\uD83C\uDDFF"),
            Country(58, "VE", "Venezuela", "\uD83C\uDDFB\uD83C\uDDEA"),
            Country(84, "VN", "Vietnam", "\uD83C\uDDFB\uD83C\uDDF3"),
            Country(967, "YE", "Yemen", "\uD83C\uDDFE\uD83C\uDDEA"),
            Country(260, "ZM", "Zambia", "\uD83C\uDDFF\uD83C\uDDF2"),
            Country(263, "ZW", "Zimbabwe", "\uD83C\uDDFF\uD83C\uDDFC")
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = FeatureSpacing.lg, end = FeatureSpacing.sm, top = FeatureSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Country",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = FeatureSpacing.sm)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            EnchantIcons.x,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FeatureSpacing.sm))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FeatureSpacing.lg)
                        .clip(RoundedCornerShape(FeatureRadii.pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = FeatureSpacing.lg, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            EnchantIcons.search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(FeatureSpacing.sm))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(BrandBlue),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search country or code",
                                            fontSize = 16.sp,
                                            color = Gray
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(FeatureSpacing.sm))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(filtered) { country ->
                        val selected = country.region == currentCountry?.region
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onCountrySelected(country) }
                                .padding(horizontal = FeatureSpacing.lg),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(country.emoji, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(FeatureSpacing.lg))
                            Text(
                                text = country.name,
                                fontSize = 16.sp,
                                color = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "+${country.code}",
                                fontSize = 16.sp,
                                color = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            modifier = Modifier
                                .padding(start = FeatureSpacing.lg)
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
            }
        }
    }
}
