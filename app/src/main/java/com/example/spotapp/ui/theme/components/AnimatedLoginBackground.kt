package com.example.spotapp.ui.theme.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class LoginParticle(
    val x: Float,
    val y: Float,
    val phase: Float,
    val radius: Float
)

@Composable
fun AnimatedLoginBackground(modifier: Modifier = Modifier) {
    val phase = spotAmbientPhase()
    Canvas(modifier = modifier.fillMaxSize()) { drawSpotAmbient(phase.value, SpotPalette.Page, 1f) }
}

private val particles = run {
        val random = Random(42)
        List(18) {
            LoginParticle(
                x = random.nextFloat(),
                y = random.nextFloat(),
                phase = random.nextFloat() * (2.0 * PI).toFloat(),
                radius = 1.2f + random.nextFloat() * 1.6f
            )
        }
    }

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpotAmbient(time: Float, base: Color, intensity: Float) {
        val gold = Color(0xFFD4AF37)
        val shortSide = size.minDimension
        drawRect(base)

        val firstGlow = Offset(
            size.width * (0.12f + 0.12f * sin(time)),
            size.height * (0.15f + 0.06f * cos(time))
        )
        val secondGlow = Offset(
            size.width * (0.88f + 0.1f * cos(time)),
            size.height * (0.83f + 0.06f * sin(time))
        )

        listOf(firstGlow, secondGlow).forEach { center ->
            val radius = shortSide * 0.95f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFE7A0).copy(alpha = 0.48f * intensity),
                        gold.copy(alpha = 0.08f * intensity),
                        gold.copy(alpha = 0f)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        repeat(3) { index ->
            val center = if (index % 2 == 0) firstGlow else secondGlow
            drawCircle(
                color = gold.copy(alpha = 0.09f * intensity),
                radius = shortSide * (0.36f + index * 0.2f),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        val points = particles.map { particle ->
            Offset(
                x = size.width * particle.x + sin(time + particle.phase) * 22.dp.toPx(),
                y = size.height * particle.y + cos(time + particle.phase) * 30.dp.toPx()
            )
        }
        val connectionDistance = shortSide * 0.3f
        points.forEachIndexed { index, point ->
            for (next in index + 1 until points.size) {
                val distance = (point - points[next]).getDistance()
                if (distance < connectionDistance) {
                    drawLine(
                        color = gold.copy(alpha = intensity * 0.12f * (1f - distance / connectionDistance)),
                        start = point,
                        end = points[next],
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            drawCircle(
                color = gold.copy(alpha = 0.3f * intensity),
                radius = particles[index].radius.dp.toPx(),
                center = point
            )
        }

        val wave = Path().apply {
            moveTo(-size.width * 0.1f, size.height * 0.78f)
            cubicTo(
                size.width * 0.3f, size.height * (0.65f + 0.03f * sin(time)),
                size.width * 0.7f, size.height * (0.97f + 0.03f * cos(time)),
                size.width * 1.1f, size.height * 0.81f
            )
        }
        drawPath(wave, gold.copy(alpha = 0.12f * intensity), style = Stroke(1.dp.toPx()))
}
