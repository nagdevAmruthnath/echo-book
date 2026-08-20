package com.echobooks.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echobooks.app.ui.theme.Cyan
import com.echobooks.app.ui.theme.Ink
import com.echobooks.app.ui.theme.InkDeep
import com.echobooks.app.ui.theme.Magenta
import com.echobooks.app.ui.theme.TextPrimary
import com.echobooks.app.ui.theme.TextSecondary
import com.echobooks.app.ui.theme.Violet
import kotlin.math.abs
import kotlin.math.sin

private val GlassShape = RoundedCornerShape(28.dp)

@Composable
fun GlassBackground(content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "blobs")
    val ax by transition.animateFloat(
        -0.2f, 1.2f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "ax"
    )
    val ay by transition.animateFloat(
        -0.3f, 1.3f,
        infiniteRepeatable(tween(17000, easing = LinearEasing), RepeatMode.Reverse),
        label = "ay"
    )
    val bx by transition.animateFloat(
        1.1f, -0.1f,
        infiniteRepeatable(tween(15000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bx"
    )
    val by by transition.animateFloat(
        0.1f, 1.2f,
        infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse),
        label = "by"
    )
    val cx by transition.animateFloat(
        0.4f, 0.9f,
        infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse),
        label = "cx"
    )
    val cy by transition.animateFloat(
        1.2f, -0.1f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "cy"
    )
    val da by transition.animateFloat(
        0.18f, 0.32f,
        infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse),
        label = "da"
    )
    val db by transition.animateFloat(
        0.14f, 0.26f,
        infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse),
        label = "db"
    )
    val dc by transition.animateFloat(
        0.12f, 0.24f,
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "dc"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(InkDeep, Ink, com.echobooks.app.ui.theme.SurfaceGlass, Ink)))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawBlob(
                Offset(size.width * ax, size.height * ay),
                size.minDimension * 0.55f,
                Violet.copy(alpha = da)
            )
            drawBlob(
                Offset(size.width * bx, size.height * by),
                size.minDimension * 0.5f,
                Cyan.copy(alpha = db)
            )
            drawBlob(
                Offset(size.width * cx, size.height * cy),
                size.minDimension * 0.5f,
                Magenta.copy(alpha = dc)
            )
        }
        content()
    }
}

private fun DrawScope.drawBlob(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = Modifier
        .background(
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.04f))),
            GlassShape
        )
        .border(1.dp, Color.White.copy(alpha = 0.18f), GlassShape)
        .shadow(20.dp, GlassShape, spotColor = Violet.copy(alpha = 0.25f))
        .clip(GlassShape)
    if (onClick != null) {
        Box(base.then(modifier).clickable(onClick = onClick)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) { content() }
        }
    } else {
        Box(base.then(modifier)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) { content() }
        }
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null
) {
    val shape = RoundedCornerShape(18.dp)
    val colors = if (enabled) listOf(Violet, Magenta) else listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.06f))
    Box(
        modifier
            .background(Brush.linearGradient(colors), shape)
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .then(if (!enabled) Modifier.alpha(0.5f) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            if (leading != null) {
                Icon(leading, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    tint: Color = Color.White,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    Box(
        modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f))),
                CircleShape
            )
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

@Composable
fun GlassActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    badge: String? = null,
    activeTint: Color = Violet
) {
    Box(
        modifier
            .size(size)
            .background(
                if (active) Brush.linearGradient(listOf(Violet, Magenta))
                else Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f))),
                CircleShape
            )
            .border(
                1.dp,
                if (active) Color.White.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.24f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = null,
                tint = when {
                    !enabled -> TextSecondary.copy(alpha = 0.4f)
                    active -> Color.White
                    else -> activeTint
                },
                modifier = Modifier.size(size * 0.40f)
            )
            if (badge != null) {
                Text(
                    badge,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else TextPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    label: String? = null,
    singleLine: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    maxLines: Int = 5,
    minHeight: Dp = 54.dp
) {
    val shape = RoundedCornerShape(18.dp)
    val textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary)
    Column(modifier) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (singleLine) minHeight else Dp.Unspecified)
                .background(Color.White.copy(alpha = 0.07f), shape)
                .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            textStyle = textStyle,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else maxLines,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(Violet),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = textStyle, color = Color.White.copy(alpha = 0.35f))
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: () -> Unit = {}
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Violet,
            inactiveTrackColor = Color.White.copy(alpha = 0.14f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent
        )
    )
}

@Composable
fun GlassChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val bg = if (selected) {
        Brush.linearGradient(listOf(Violet, Magenta))
    } else {
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.05f)))
    }
    Box(
        modifier
            .background(bg, shape)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else TextPrimary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun GradientCover(
    hue: Float,
    title: String,
    modifier: Modifier = Modifier,
    height: Dp = 130.dp
) {
    val c1 = hslColor(hue, 0.55f, 0.55f)
    val c2 = hslColor((hue + 60f) % 360f, 0.6f, 0.45f)
    val c3 = hslColor((hue + 150f) % 360f, 0.5f, 0.34f)
    Box(
        modifier
            .height(height)
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(c1, c2, c3)), RoundedCornerShape(24.dp))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 10) {
                val wave = 0.5f + 0.45f * abs(sin(i * 1.7f + hue * 0.02f)).toFloat()
                val h = size.height * (0.2f + 0.5f * wave)
                val w = size.width / 10f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.16f),
                    topLeft = Offset(size.width * i / 10f + w * 0.2f, size.height - h),
                    size = Size(w * 0.6f, h),
                    cornerRadius = CornerRadius(8f)
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .size(58.dp)
                .background(Color.White.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                title.trim().take(1).ifBlank { "?" }.uppercase(),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GlassProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    fill: Color = Violet
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .height(8.dp)
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.14f), shape)
            .clip(shape)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Brush.horizontalGradient(listOf(fill, Cyan)), shape)
        )
    }
}

fun hslColor(hue: Float, sat: Float, value: Float): Color {
    val hsv = floatArrayOf(hue, sat, value)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextSecondary,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp)
    )
}