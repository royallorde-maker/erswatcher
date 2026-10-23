package com.erswatcher.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.erswatcher.app.ui.theme.Eye
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The house mark: an almond eye with an amber iris and the vertical
 * "Cult of Intellect" bars. It follows a slow figure-eight, blinks now and
 * then, and grows a radar halo while the app is scanning.
 */
@Composable
fun EyeLogo(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    scanning: Boolean = false,
    glow: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "eye")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (scanning) 5200 else 16500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gaze"
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 6400
                1f at 0
                1f at 5350
                0.55f at 5500
                0.035f at 5640
                0.55f at 5790
                1f at 5940
                1f at 6400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (scanning) 2400 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Canvas(modifier) {
        val eyeW = min(size.width * 0.84f, size.height * 2.55f)
        val eyeH = eyeW * 0.40f
        val eyeLeft = (size.width - eyeW) / 2f
        val eyeTop = (size.height - eyeH) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val open = if (animated) blink else 1f
        val gazeX = if (animated) sin(if (scanning) phase * 1.7f else phase) * eyeW * 0.036f else 0f
        val gazeY = if (animated) sin((if (scanning) phase * 2.4f else phase * 2f)) * eyeH * 0.10f else 0f
        val irisCenter = Offset(center.x + gazeX, center.y + gazeY)
        val irisR = eyeH * 0.47f

        if (glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Eye.Violet.copy(alpha = if (scanning) 0.38f else 0.24f),
                        Eye.Cyan.copy(alpha = 0.09f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = eyeW * 0.60f
                ),
                radius = eyeW * 0.60f,
                center = center
            )
        }

        if (animated && scanning) {
            val r = eyeW * 0.62f
            rotate(sweep, center) {
                drawArc(
                    color = Eye.Violet.copy(alpha = 0.8f),
                    startAngle = 18f,
                    sweepAngle = 58f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = Eye.Cyan.copy(alpha = 0.55f),
                    startAngle = 198f,
                    sweepAngle = 32f,
                    useCenter = false,
                    topLeft = Offset(center.x - r, center.y - r),
                    size = Size(r * 2, r * 2),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawCircle(Eye.Violet.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = Offset(center.x, center.y - eyeW * 0.62f))
        }

        withTransform({ scale(1f, open, pivot = center) }) {
            val eyeRect = androidx.compose.ui.geometry.Rect(eyeLeft, eyeTop, eyeLeft + eyeW, eyeTop + eyeH)
            val eye = almond(eyeRect)
            drawPath(
                path = eye,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFFDF6), Eye.Cream, Color(0xFFD9CEBB)),
                    startY = eyeTop,
                    endY = eyeTop + eyeH
                )
            )
            // A soft line of shadow under the upper lid gives the white a lens curve.
            drawPath(
                path = almond(androidx.compose.ui.geometry.Rect(
                    eyeLeft + eyeW * 0.04f,
                    eyeTop + eyeH * 0.02f,
                    eyeLeft + eyeW * 0.96f,
                    eyeTop + eyeH * 0.97f
                )),
                color = Color(0x22302317),
                style = Stroke(width = 1.dp.toPx())
            )
            drawPath(
                path = eye,
                color = Color(0xFF2A1D13),
                style = Stroke(width = 1.5.dp.toPx())
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFCF63), Eye.Orange, Eye.OrangeDeep),
                    center = Offset(irisCenter.x - irisR * 0.2f, irisCenter.y - irisR * 0.3f),
                    radius = irisR * 1.5f
                ),
                radius = irisR,
                center = irisCenter
            )
            drawCircle(
                color = Color(0xFF2A1A06),
                radius = irisR,
                center = irisCenter,
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Thin radial striations, like the iris in the brand mark.
            for (i in 0 until 12) {
                val a = (i / 12.0) * 2 * PI
                val inner = irisR * 0.66f
                val outer = irisR * 0.94f
                drawLine(
                    color = Color(0x223B2407),
                    start = Offset(irisCenter.x + cos(a).toFloat() * inner, irisCenter.y + sin(a).toFloat() * inner),
                    end = Offset(irisCenter.x + cos(a).toFloat() * outer, irisCenter.y + sin(a).toFloat() * outer),
                    strokeWidth = 0.8.dp.toPx()
                )
            }
            // The vertical bars from the Cult of Intellect eye.
            val bar = irisR * 0.13f
            val barTop = irisCenter.y - irisR * 0.62f
            drawRoundRect(
                color = Eye.Ink,
                topLeft = Offset(irisCenter.x - irisR * 0.39f, barTop + irisR * 0.10f),
                size = Size(bar, irisR * 0.95f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bar * 0.28f)
            )
            drawRoundRect(
                color = Eye.Ink,
                topLeft = Offset(irisCenter.x - bar / 2, barTop - irisR * 0.08f),
                size = Size(bar, irisR * 1.36f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bar * 0.28f)
            )
            drawRoundRect(
                color = Eye.Ink,
                topLeft = Offset(irisCenter.x + irisR * 0.28f, barTop + irisR * 0.14f),
                size = Size(bar, irisR * 0.86f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bar * 0.28f)
            )
            drawRoundRect(
                color = Eye.Ink,
                topLeft = Offset(irisCenter.x + irisR * 0.54f, barTop + irisR * 0.27f),
                size = Size(bar * 0.72f, irisR * 0.60f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bar * 0.24f)
            )
            // Specular glint.
            drawArc(
                color = Color.White.copy(alpha = 0.75f),
                startAngle = 200f,
                sweepAngle = 55f,
                useCenter = false,
                topLeft = Offset(irisCenter.x - irisR * 0.66f, irisCenter.y - irisR * 0.72f),
                size = Size(irisR * 0.62f, irisR * 0.48f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

private fun almond(rect: androidx.compose.ui.geometry.Rect): Path = Path().apply {
    val w = rect.width
    val h = rect.height
    moveTo(rect.left, rect.center.y)
    cubicTo(
        rect.left + w * 0.24f, rect.top + h * 0.05f,
        rect.left + w * 0.76f, rect.top + h * 0.05f,
        rect.right, rect.center.y
    )
    cubicTo(
        rect.left + w * 0.76f, rect.bottom - h * 0.05f,
        rect.left + w * 0.24f, rect.bottom - h * 0.05f,
        rect.left, rect.center.y
    )
    close()
}

/** The soft blue/violet ribbons from the Cult of Intellect site. */
@Composable
fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    intensity: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "aurora-x"
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            // Base wash.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF0B0713), Color(0xFF050407))
                )
            )
            val w = size.width
            val h = size.height
            val drift = if (animated) sin(t) * w * 0.035f else 0f
            ribbon(
                y = h * 0.24f,
                amplitude = h * 0.11f,
                phase = if (animated) t else 0f,
                colors = listOf(Eye.Cyan, Eye.Violet, Eye.Magenta),
                alpha = 0.16f * intensity,
                xShift = drift
            )
            ribbon(
                y = h * 0.43f,
                amplitude = h * 0.09f,
                phase = if (animated) t + 1.6f else 1.6f,
                colors = listOf(Eye.Magenta, Eye.Violet, Eye.Cyan),
                alpha = 0.14f * intensity,
                xShift = -drift * 0.6f
            )
            ribbon(
                y = h * 0.60f,
                amplitude = h * 0.07f,
                phase = if (animated) t + 3.2f else 3.2f,
                colors = listOf(Eye.Cyan, Color(0xFF5063FF), Eye.Violet),
                alpha = 0.10f * intensity,
                xShift = drift * 0.4f
            )
            // Fade the bottom into the card colour so content stays readable.
            drawRect(
                brush = Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    0.78f to Color(0xCC060509),
                    1f to Color(0xFF060509)
                )
            )
        }
    }
}

private fun DrawScope.ribbon(
    y: Float,
    amplitude: Float,
    phase: Float,
    colors: List<Color>,
    alpha: Float,
    xShift: Float
) {
    val w = size.width
    val path = Path().apply {
        moveTo(-w * 0.2f + xShift, y)
        cubicTo(
            w * 0.18f + xShift, y - amplitude,
            w * 0.42f + xShift, y + amplitude * 0.9f,
            w * 0.66f + xShift, y
        )
        cubicTo(
            w * 0.82f + xShift, y - amplitude * 0.6f,
            w * 0.95f + xShift, y - amplitude * 0.2f,
            w * 1.2f + xShift, y + amplitude * 0.3f
        )
    }
    // Layered strokes fake a soft gaussian ribbon without needing RenderEffect.
    for (i in 0..4) {
        val widest = size.width * (0.30f - i * 0.052f)
        val a = alpha * (0.24f + i * 0.19f)
        val brush = Brush.linearGradient(
            colors.map { c -> lerp(c, Color.Transparent, 1f - a) },
            start = Offset(0f, y),
            end = Offset(w, y + sin(phase) * amplitude)
        )
        drawPath(
            path = path,
            brush = brush,
            style = Stroke(width = widest, cap = StrokeCap.Round)
        )
    }
}
