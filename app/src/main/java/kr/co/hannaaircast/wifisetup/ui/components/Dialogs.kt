package kr.co.hannaaircast.wifisetup.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kr.co.hannaaircast.wifisetup.ui.theme.CredentialStyle
import kr.co.hannaaircast.wifisetup.ui.theme.Hanna

/** 흰 카드가 살짝 커지며 나타난다. 버튼은 아래에 크게. */
@Composable
fun HannaDialog(
    onDismiss: () -> Unit,
    dismissable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    Dialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val s = 0.9f + 0.1f * enter.value
                    scaleX = s
                    scaleY = s
                    alpha = enter.value.coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Hanna.Surface)
                .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
            content = content,
        )
    }
}

@Composable
fun MessageDialog(
    title: String?,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismissable: Boolean = true,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
) = HannaDialog(onDismiss = onConfirm, dismissable = dismissable) {
    if (title != null) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Hanna.Ink)
        Spacer(Modifier.height(10.dp))
    }
    Text(body, style = MaterialTheme.typography.bodyLarge, color = Hanna.InkSoft)
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (secondary != null && onSecondary != null) {
            HannaButton(secondary, onSecondary, Modifier.weight(1f), ButtonTone.SOFT_GREY)
        }
        HannaButton(confirm, onConfirm, Modifier.weight(1f))
    }
}

/** 이름표는 위에, 값은 크게. 잡으면 테두리가 파랗게 켜진다. */
@Composable
fun HannaField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Hanna.Blue else Hanna.Fill, tween(160), label = "border")
    val labelColor by animateColorAsState(if (focused) Hanna.Blue else Hanna.Sub, tween(160), label = "label")

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelColor)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = CredentialStyle.copy(color = Hanna.Ink, fontSize = CredentialStyle.fontSize * 0.83f),
            cursorBrush = SolidColor(Hanna.Blue),
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Hanna.Fill)
                        .border(2.dp, border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    if (value.isEmpty() && hint != null) {
                        Text(hint, style = MaterialTheme.typography.bodyLarge, color = Hanna.Sub)
                    }
                    inner()
                }
            },
        )
    }
}
