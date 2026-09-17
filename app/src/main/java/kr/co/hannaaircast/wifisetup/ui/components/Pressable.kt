package kr.co.hannaaircast.wifisetup.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kr.co.hannaaircast.wifisetup.ui.theme.Hanna

/**
 * 누르면 살짝 들어가고, 떼면 튕겨 나온다. 이 앱의 모든 누를 수 있는 것이 이걸 쓴다.
 *
 * 물결(ripple) 대신 **크기**로 답한다. 짧게 톡 쳐도 보이도록, 뗄 때는 일단 끝까지
 * 눌린 다음에 튕겨 나온다 — 상태값만 따라가면 빠른 탭은 눈에 안 보인다.
 */
@Composable
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    source: MutableInteractionSource = remember { MutableInteractionSource() },
    pressedScale: Float = 0.96f,
): Modifier {
    val scale = remember { Animatable(1f) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> launch { scale.animateTo(pressedScale, tween(90)) }
                is PressInteraction.Release, is PressInteraction.Cancel -> launch {
                    scale.animateTo(pressedScale, tween(60))
                    scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            role = Role.Button,
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
            onClick()
        }
}

enum class ButtonTone { PRIMARY, SOFT_BLUE, SOFT_GREY }

@Composable
fun HannaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 56.dp,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    val container by animateColorAsState(
        when (tone) {
            ButtonTone.PRIMARY -> when {
                !enabled && !loading -> Hanna.BlueDisabled
                pressed -> Hanna.BluePressed
                else -> Hanna.Blue
            }
            ButtonTone.SOFT_BLUE -> if (pressed) Hanna.BlueSoftPressed else Hanna.BlueSoft
            ButtonTone.SOFT_GREY -> if (pressed) Hanna.FillPressed else Hanna.Fill
        },
        tween(120),
        label = "container",
    )
    val content = when (tone) {
        ButtonTone.PRIMARY -> Hanna.Surface
        ButtonTone.SOFT_BLUE -> Hanna.Blue
        ButtonTone.SOFT_GREY -> Hanna.InkSoft
    }.let { if (enabled || tone == ButtonTone.PRIMARY) it else it.copy(alpha = 0.35f) }

    Box(
        modifier = modifier
            .height(height)
            .pressable(onClick, enabled = enabled && !loading, source = source)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) LoadingDots(content) else Text(text, color = content, style = MaterialTheme.typography.labelLarge)
    }
}

/** 점 세 개가 차례로 떠오른다. 「보내는 중」. */
@Composable
private fun LoadingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val t by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(520, delayMillis = i * 130, easing = LinearEasing),
                    RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        translationY = -5.dp.toPx() * t
                        alpha = 0.45f + 0.55f * t
                    }
                    .background(color, CircleShape),
            )
        }
    }
}
