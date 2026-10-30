package com.example.spotapp.ui.theme.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.spotapp.data.LookFilters
import com.example.spotapp.data.MediaLook

/**
 * Instagram-style filter strip: each filter's name over a small preview of the photo drawn through it.
 * [thumbnail] draws the photo with the color filter it's given.
 */
@Composable
fun LookFilterRow(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, dark: Boolean = false,
    thumbnail: @Composable (ColorFilter?) -> Unit) {
    val accent = if (dark) SpotPalette.GoldBright else SpotPalette.GoldDeep
    LazyRow(modifier.fillMaxWidth().testTag("look-filters"), contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(LookFilters, key = { it.key }) { filter ->
            val chosen = filter.key == selected
            Column(Modifier.width(72.dp).clip(RoundedCornerShape(12.dp))
                .selectable(chosen, role = Role.RadioButton) { onSelect(filter.key) }.padding(vertical = 4.dp)
                .testTag("look-filter-${filter.key}"), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(filter.label, color = if (chosen) accent else if (dark) Color.White else SpotPalette.Ink,
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 6.dp).size(64.dp).clip(RoundedCornerShape(10.dp))
                    .border(if (chosen) 2.dp else 0.dp, if (chosen) accent else Color.Transparent, RoundedCornerShape(10.dp))) {
                    thumbnail(MediaLook(filter.key).colorFilter())
                }
            }
        }
    }
}
