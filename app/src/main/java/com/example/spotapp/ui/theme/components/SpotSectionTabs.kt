package com.example.spotapp.ui.theme.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal data class SpotSectionTab(val id: String, val label: String, val icon: ImageVector)

/** Compact categories retain accessible names and a visible label on long press.
 * A segmented track with a sliding white thumb marks the active section. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SpotSectionTabs(tabs: List<SpotSectionTab>, selected: String,
    onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val index = tabs.indexOfFirst { it.id == selected }.coerceAtLeast(0)
    val track = RoundedCornerShape(20.dp)
    val thumb = RoundedCornerShape(16.dp)
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        .clip(track).background(SpotPalette.Track).border(1.dp, if (SpotPalette.dark) SpotPalette.Line else Color(0xFFEAE3D2), track).padding(4.dp)) {
        val slot = maxWidth / tabs.size.coerceAtLeast(1)
        val offset by animateDpAsState(slot * index, spring(dampingRatio = .82f, stiffness = 420f), label = "section thumb")
        Box(Modifier.offset(x = offset).width(slot).height(48.dp)
            .shadow(3.dp, thumb, ambientColor = SpotPalette.Shadow, spotColor = SpotPalette.Shadow)
            .background(SpotPalette.Surface, thumb).border(1.dp, SpotPalette.GoldLine.copy(alpha = .7f), thumb))
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            tabs.forEach { tab ->
                val active = tab.id == selected
                val tint by animateColorAsState(if (active) SpotPalette.GoldDeep else SpotPalette.Muted, label = "section icon")
                val scale by animateFloatAsState(if (active) 1.08f else 1f, spring(dampingRatio = .6f, stiffness = 500f), label = "section scale")
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text(tab.label) } },
                        state = rememberTooltipState()
                    ) {
                        Box(Modifier.fillMaxWidth().height(48.dp).clip(thumb).testTag("section-${tab.id}")
                            .selectable(active, role = Role.Tab, onClick = { onSelect(tab.id) })
                            .semantics { contentDescription = tab.label }, contentAlignment = Alignment.Center) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(22.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }, tint = tint)
                        }
                    }
                }
            }
        }
    }
}
