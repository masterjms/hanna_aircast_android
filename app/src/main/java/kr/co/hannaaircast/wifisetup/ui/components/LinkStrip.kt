package kr.co.hannaaircast.wifisetup.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kr.co.hannaaircast.wifisetup.ui.theme.Hanna
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 폰과 단말 사이의 선이 지금 어떤 상태인가. */
enum class LinkMode {
    /** USB 가 아직 없다. */
    OFF,

    /** 포트는 열렸고 단말을 찾는 중이다. */
    SEARCHING,
    CONNECTED,
    SENDING,
    SUCCESS,

    /** 단말이 답하지 않았거나 거절했다. */
    BROKEN,
}

/**
 * 이 앱이 하는 일 그 자체 — **폰에서 단말로, 선을 타고 값이 건너간다.**
 *
 * 찾는 중에는 선 위를 빛이 오가고, 보내는 중에는 점들이 단말 쪽으로 흐르고,
 * 성공하면 선이 녹색으로 차오르며 단말에 체크가 그려지고, 실패하면 선이 끊기며
 * 한 번 흔들린다. 장식이 아니라 상태 표시다.
 */
@Composable
fun LinkStrip(mode: LinkMode, deviceLabel: String, modifier: Modifier = Modifier) {
    val loop = rememberInfiniteTransition(label = "link")
    val phase by loop.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "phase",
    )

    val arrive = remember { Animatable(0f) } // 성공: 선이 차오르고 체크가 그려진다
    val ring = remember { Animatable(0f) } // 성공: 단말에서 고리가 한 번 퍼진다
    val shake = remember { Animatable(0f) } // 실패: 좌우로 한 번 흔들린다

    LaunchedEffect(mode) {
        arrive.snapTo(0f)
        ring.snapTo(0f)
        when (mode) {
            LinkMode.SUCCESS -> {
                arrive.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
                ring.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
            }
            LinkMode.BROKEN -> for (x in listOf(-9f, 9f, -6f, 6f, -3f, 0f)) shake.animateTo(x, tween(52))
            else -> Unit
        }
    }

    val accent by animateColorAsState(
        when (mode) {
            LinkMode.SUCCESS -> Hanna.Green
            LinkMode.BROKEN -> Hanna.Red
            LinkMode.OFF -> Hanna.Sub
            else -> Hanna.Blue
        },
        tween(280), label = "accent",
    )
    val phoneFill by animateColorAsState(
        if (mode == LinkMode.OFF) Hanna.Fill else Hanna.BlueSoft, tween(280), label = "phoneFill",
    )
    val deviceFill by animateColorAsState(
        when (mode) {
            LinkMode.OFF, LinkMode.SEARCHING -> Hanna.Fill
            LinkMode.SUCCESS -> Hanna.GreenSoft
            LinkMode.BROKEN -> Hanna.RedSoft
            else -> Hanna.BlueSoft
        },
        tween(280), label = "deviceFill",
    )
    val deviceInk by animateColorAsState(
        if (mode == LinkMode.OFF || mode == LinkMode.SEARCHING) Hanna.Sub else accent,
        tween(280), label = "deviceInk",
    )

    Column(modifier.graphicsLayer { translationX = shake.value.dp.toPx() }) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val r = 28.dp.toPx()
            val cy = size.height / 2
            val phone = Offset(r, cy)
            val device = Offset(size.width - r, cy)
            val x1 = phone.x + r + 10.dp.toPx()
            val x2 = device.x - r - 10.dp.toPx()
            val w = 4.dp.toPx()

            fun line(from: Float, to: Float, color: Color) =
                drawLine(color, Offset(from, cy), Offset(to, cy), strokeWidth = w, cap = StrokeCap.Round)

            // ── 선 ──
            line(x1, x2, Hanna.Line)
            when (mode) {
                LinkMode.OFF -> Unit
                LinkMode.SEARCHING -> {
                    // 빛 한 토막이 선 위를 오간다.
                    val span = (x2 - x1) * 0.26f
                    val t = 1f - abs(1f - 2f * phase) // 0→1→0
                    val from = x1 + (x2 - x1 - span) * t
                    line(from, from + span, accent)
                }
                LinkMode.CONNECTED -> line(x1, x2, accent)
                LinkMode.SENDING -> {
                    line(x1, x2, accent.copy(alpha = 0.22f))
                    repeat(3) { i ->
                        val t = (phase + i / 3f) % 1f
                        drawCircle(
                            color = accent.copy(alpha = sin(PI * t).toFloat()),
                            radius = 5.dp.toPx(),
                            center = Offset(x1 + (x2 - x1) * t, cy),
                        )
                    }
                }
                LinkMode.SUCCESS -> line(x1, x1 + (x2 - x1) * (arrive.value * 1.7f).coerceAtMost(1f), accent)
                LinkMode.BROKEN -> {
                    val mid = (x1 + x2) / 2
                    val gap = 15.dp.toPx()
                    line(x1, mid - gap, accent)
                    line(mid + gap, x2, accent)
                    val k = 5.dp.toPx()
                    val stroke = 2.5.dp.toPx()
                    drawLine(accent, Offset(mid - k, cy - k), Offset(mid + k, cy + k), stroke, StrokeCap.Round)
                    drawLine(accent, Offset(mid - k, cy + k), Offset(mid + k, cy - k), stroke, StrokeCap.Round)
                }
            }

            // ── 성공의 고리 ──
            if (ring.value > 0f && ring.value < 1f) {
                drawCircle(
                    color = accent.copy(alpha = 0.35f * (1f - ring.value)),
                    radius = r + 16.dp.toPx() * ring.value,
                    center = device,
                    style = Stroke(2.dp.toPx()),
                )
            }

            // ── 양 끝 ──
            drawCircle(phoneFill, r, phone)
            drawCircle(deviceFill, r, device)
            phoneGlyph(phone, if (mode == LinkMode.OFF) Hanna.Sub else Hanna.Blue)

            val check = ((arrive.value - 0.45f) / 0.55f).coerceIn(0f, 1f)
            if (check < 1f) receiverGlyph(device, deviceInk.copy(alpha = deviceInk.alpha * (1f - check)))
            if (check > 0f) checkGlyph(device, accent, check)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "휴대폰",
                style = MaterialTheme.typography.labelSmall,
                color = Hanna.Sub,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 56.dp),
            )
            Text(
                deviceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Hanna.Sub,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(min = 56.dp),
            )
        }
    }
}

private fun DrawScope.phoneGlyph(c: Offset, color: Color) {
    val w = 17.dp.toPx()
    val h = 28.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(c.x - w / 2, c.y - h / 2),
        size = Size(w, h),
        cornerRadius = CornerRadius(4.5.dp.toPx()),
        style = Stroke(2.4.dp.toPx()),
    )
    val bar = 3.dp.toPx()
    drawLine(
        color, Offset(c.x - bar, c.y + h / 2 - 5.dp.toPx()), Offset(c.x + bar, c.y + h / 2 - 5.dp.toPx()),
        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
    )
}

/** 방송 수신기: 상자, 스피커, 그릴 두 줄. */
private fun DrawScope.receiverGlyph(c: Offset, color: Color) {
    val w = 30.dp.toPx()
    val h = 22.dp.toPx()
    val stroke = 2.4.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(c.x - w / 2, c.y - h / 2),
        size = Size(w, h),
        cornerRadius = CornerRadius(5.dp.toPx()),
        style = Stroke(stroke),
    )
    drawCircle(color, 4.5.dp.toPx(), Offset(c.x - 6.dp.toPx(), c.y), style = Stroke(stroke))
    for (dy in listOf(-3.5f, 3.5f)) {
        drawLine(
            color,
            Offset(c.x + 3.dp.toPx(), c.y + dy.dp.toPx()),
            Offset(c.x + 9.dp.toPx(), c.y + dy.dp.toPx()),
            strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.checkGlyph(c: Offset, color: Color, progress: Float) {
    val full = Path().apply {
        moveTo(c.x - 9.dp.toPx(), c.y + 0.5.dp.toPx())
        lineTo(c.x - 2.5.dp.toPx(), c.y + 7.dp.toPx())
        lineTo(c.x + 9.5.dp.toPx(), c.y - 6.5.dp.toPx())
    }
    val measure = PathMeasure().apply { setPath(full, false) }
    val drawn = Path()
    measure.getSegment(0f, measure.length * progress, drawn, true)
    drawPath(drawn, color, style = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
