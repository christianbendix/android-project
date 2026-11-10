package com.example.splendit.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splendit.domain.Wheel
import com.example.splendit.ui.theme.Sw
import kotlin.math.cos
import kotlin.math.sin

enum class SpinPhase { IDLE, BUSY, DONE }

/**
 * The lucky wheel. Stateless: the ViewModel decides [rotation] and [winner]; this composable
 * only animates towards the rotation it is given.
 */
@Composable
fun SpinWheel(
    ids: List<String>,
    labels: List<String>,
    weights: List<Double>,
    rotation: Float,
    phase: SpinPhase,
    winner: String?,
    hubText: String,
    modifier: Modifier = Modifier,
    size: Dp = 310.dp,
    durationMillis: Int = 4600
) {
    val colors = Sw.colors
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val segments = Wheel.segments(ids, weights)
    val fills = if (ids.size % 2 == 1) {
        listOf(surface, colors.accent800, colors.accent700)
    } else {
        listOf(surface, colors.accent800)
    }
    val turned by animateFloatAsState(
        targetValue = rotation,
        animationSpec = tween(durationMillis, easing = CubicBezierEasing(0.12f, 0.7f, 0.06f, 1f)),
        label = "wheel"
    )
    val transition = rememberInfiniteTransition(label = "bulbs")
    val blinkMs = when (phase) {
        SpinPhase.BUSY -> 450
        SpinPhase.DONE -> 300
        SpinPhase.IDLE -> 2400
    }
    val blink by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(blinkMs), RepeatMode.Reverse),
        label = "blink"
    )
    val wobble by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (phase == SpinPhase.BUSY) -16f else 0f,
        animationSpec = infiniteRepeatable(tween(130), RepeatMode.Reverse),
        label = "wobble"
    )
    val labelSize = if (size > 290.dp) 15.sp else 13.sp

    Box(modifier.size(size, size + 14.dp)) {
        Canvas(
            Modifier
                .offset(y = 14.dp)
                .size(size)
        ) {
            val c = this.size.minDimension / 2
            val center = Offset(c, c)
            val r = c - 18.dp.toPx()
            fun point(angle: Float, radius: Float): Offset {
                val rad = Math.toRadians(angle.toDouble())
                return Offset(c + radius * sin(rad).toFloat(), c - radius * cos(rad).toFloat())
            }

            // Rim and bulbs stay still.
            drawCircle(
                Brush.verticalGradient(listOf(colors.accent500, colors.accent200)),
                radius = c - 1
            )
            for (i in 0 until 24) {
                val alpha = if (i % 2 == 0) blink else 1.35f - blink
                drawCircle(
                    colors.accent900.copy(alpha = alpha.coerceIn(0.35f, 1f)),
                    radius = 3.4.dp.toPx(),
                    center = point(i * 15f, c - 9.dp.toPx())
                )
            }

            rotate(turned, pivot = center) {
                drawCircle(surface, radius = r + 1)
                val arcTopLeft = Offset(c - r, c - r)
                val arcSize = Size(r * 2, r * 2)
                segments.forEachIndexed { i, sg ->
                    val win = phase == SpinPhase.DONE && sg.id == winner
                    // Compose arcs start at 3 o'clock; the wheel model starts at 12.
                    drawArc(
                        color = if (win) colors.accent400 else fills[i % fills.size],
                        startAngle = sg.startAngle - 90,
                        sweepAngle = sg.sweep,
                        useCenter = true,
                        topLeft = arcTopLeft,
                        size = arcSize
                    )
                    drawArc(
                        color = colors.accent600,
                        startAngle = sg.startAngle - 90,
                        sweepAngle = sg.sweep,
                        useCenter = true,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(1.dp.toPx())
                    )
                    if (sg.sweep >= 22) {
                        val mid = sg.startAngle + sg.sweep / 2
                        val at = point(mid, r * 0.64f)
                        val layout = measurer.measure(
                            labels[i],
                            TextStyle(
                                color = if (win) surface else colors.accent100,
                                fontSize = labelSize,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        rotate(mid, pivot = at) {
                            drawText(
                                layout,
                                topLeft = Offset(
                                    at.x - layout.size.width / 2f,
                                    at.y - layout.size.height / 2f
                                )
                            )
                        }
                    }
                }
                segments.forEach { sg ->
                    drawCircle(
                        colors.accent300,
                        radius = 3.2.dp.toPx(),
                        center = point(sg.startAngle, r - 4.dp.toPx())
                    )
                }
            }
        }

        // Hub
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 7.dp)
                .size(68.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(listOf(colors.accent500, colors.accent300))
                )
                .border(4.dp, surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                hubText,
                color = surface,
                fontSize = if (phase == SpinPhase.DONE) 18.sp else 22.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Pointer at 12 o'clock
        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .size(32.dp, 42.dp)
        ) {
            val k = this.size.width / 32f
            rotate(wobble, pivot = Offset(16 * k, 14 * k)) {
                scale(k, pivot = Offset.Zero) {
                    val path = Path().apply {
                        moveTo(16f, 41f)
                        lineTo(4f, 16f)
                        arcTo(
                            Rect(3f, 1f, 29f, 27f),
                            startAngleDegrees = 170.5f,
                            sweepAngleDegrees = 199f,
                            forceMoveTo = false
                        )
                        close()
                    }
                    drawPath(path, colors.accent200)
                    drawPath(path, surface, style = Stroke(2.5f))
                    drawCircle(surface, radius = 4.5f, center = Offset(16f, 14f))
                }
            }
        }
    }
}
