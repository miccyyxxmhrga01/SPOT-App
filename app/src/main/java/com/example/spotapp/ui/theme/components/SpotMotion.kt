package com.example.spotapp.ui.theme.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun SpotAnimatedIconButton(icon: ImageVector, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, tint: Color = SpotPalette.Ink, selected: Boolean = false, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pulse = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected != previous) {
            previous = selected
            pulse.snapTo(if (selected) 1.18f else .92f)
            pulse.animateTo(1f, spring(dampingRatio = .6f, stiffness = 650f))
        }
    }
    val scale by animateFloatAsState(if (pressed) .9f else 1f, spring(stiffness = 700f), label = "Action press")
    IconButton(onClick, modifier.size(48.dp), enabled = enabled, interactionSource = interaction) {
        Icon(icon, label, Modifier.size(23.dp).graphicsLayer { scaleX = scale * pulse.value; scaleY = scale * pulse.value }, tint = tint)
    }
}

@Composable
fun SpotPageIndicator(count: Int, selected: Int, modifier: Modifier = Modifier, dark: Boolean = false) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == selected) 16.dp else 4.dp, spring(stiffness = 500f), label = "Media page")
            Box(Modifier.width(width).height(4.dp).background(
                if (index == selected) if (dark) Color.White else SpotPalette.GoldDeep
                else if (dark) Color.White.copy(alpha = .4f) else Color(0xFFDCD8CC), CircleShape))
        }
    }
}

@Composable
fun SpotScreenEntrance(modifier: Modifier = Modifier, fullscreen: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(230, easing = FastOutSlowInEasing)) }
    Box(modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * if (fullscreen) 24.dp.toPx() else 12.dp.toPx()
        scaleX = .985f + .015f * progress.value; scaleY = scaleX
    }, content = content)
}
