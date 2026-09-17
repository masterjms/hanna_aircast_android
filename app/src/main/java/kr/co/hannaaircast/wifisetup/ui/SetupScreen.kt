package kr.co.hannaaircast.wifisetup.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kr.co.hannaaircast.wifisetup.ui.components.ButtonTone
import kr.co.hannaaircast.wifisetup.ui.components.HannaButton
import kr.co.hannaaircast.wifisetup.ui.components.HannaDialog
import kr.co.hannaaircast.wifisetup.ui.components.HannaField
import kr.co.hannaaircast.wifisetup.ui.components.LinkMode
import kr.co.hannaaircast.wifisetup.ui.components.LinkStrip
import kr.co.hannaaircast.wifisetup.ui.components.MessageDialog
import kr.co.hannaaircast.wifisetup.ui.components.pressable
import kr.co.hannaaircast.wifisetup.ui.theme.CredentialStyle
import kr.co.hannaaircast.wifisetup.ui.theme.Hanna
import kr.co.hannaaircast.wifisetup.ui.theme.HannaaircastTheme
import kr.co.hannaaircast.wifisetup.wifi.PASSWORD_MAX_BYTES
import kr.co.hannaaircast.wifisetup.wifi.SSID_MAX_BYTES
import kr.co.hannaaircast.wifisetup.wifi.Security
import kr.co.hannaaircast.wifisetup.wifi.WifiCredential
import kr.co.hannaaircast.wifisetup.wifi.isSendable

private val CardShape = RoundedCornerShape(24.dp)

/**
 * 앱의 유일한 화면 (앱 사양 §16). 상태를 받아 그리기만 한다 — 시리얼도 USB 도 모른다.
 *
 * 위에서 아래로: 지금 할 일을 말하는 한 문장 → 폰과 단말을 잇는 선 → Wi-Fi 정보 →
 * 바닥에 붙은 두 버튼.
 */
@Composable
fun SetupScreen(
    state: UiState,
    version: String,
    onScan: () -> Unit,
    onManualInput: (WifiCredential) -> Unit,
    onSend: () -> Unit,
    onReboot: () -> Unit,
    onRecheck: () -> Unit,
    onDismissNotice: () -> Unit,
    onCloseApp: () -> Unit,
    /** 디버그 빌드에서만 null 이 아니다. 「USB 없음」 팝업에 가상 단말 버튼이 생긴다. */
    onUseDemo: (() -> Unit)? = null,
) {
    var manualOpen by remember { mutableStateOf(false) }

    // 결과는 손에도 알린다 — 작업자는 화면이 아니라 공유기를 보고 있을 수 있다.
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.send) {
        when (state.send) {
            SendStatus.Ok -> haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            is SendStatus.Fail, SendStatus.NoResponse -> haptic.performHapticFeedback(HapticFeedbackType.Reject)
            else -> Unit
        }
    }

    Scaffold(
        containerColor = Hanna.Ground,
        bottomBar = { BottomBar(state, onSend, onReboot) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Headline(state)
            Spacer(Modifier.height(24.dp))
            LinkCard(state, onRecheck)
            Spacer(Modifier.height(12.dp))
            WifiArea(state, onScan, onManual = { manualOpen = true })
            Spacer(Modifier.height(20.dp))
            Text(
                "v$version",
                style = MaterialTheme.typography.labelSmall,
                color = Hanna.Sub,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (manualOpen) {
        ManualInputDialog(
            initial = state.input?.credential,
            onDismiss = { manualOpen = false },
            onConfirm = {
                manualOpen = false
                onManualInput(it)
            },
        )
    }

    state.notice?.let { NoticeDialog(it, onDismissNotice) }
    // 닫으면 앱이 끝나는 오류가 안내보다 위에 온다.
    state.fatal?.let { FatalDialog(it, onCloseApp, onUseDemo.takeIf { _ -> it == Fatal.NO_USB_DEVICE }) }
}

// ── 한 문장 ────────────────────────────────────────────────────────────────

private data class Copy(val title: String, val sub: String?)

private fun copyFor(state: UiState): Copy = when {
    state.usb == Light.ERROR && state.fatal == null ->
        Copy("USB 연결이 끊겼어요", "USB 장치를 다시 연결한 뒤 앱을 다시 실행해 주세요.")
    state.send == SendStatus.Sending -> Copy("단말에 보내고 있어요", null)
    state.send == SendStatus.Ok -> Copy("단말에 저장했어요", "재부팅하면 새 Wi-Fi로 연결해요.")
    state.send is SendStatus.Fail ->
        Copy("단말이 받지 않았어요", state.send.reason?.let { "단말: $it" } ?: "USB 연결을 확인하고 다시 보내 주세요.")
    state.send == SendStatus.NoResponse ->
        Copy("단말이 답하지 않아요", "설정 모드(KEY1+KEY4)인지 확인하고 다시 보내 주세요.")
    state.device == DeviceStatus.REBOOT_SENT ->
        Copy("재부팅 명령을 보냈어요", "다음 단말을 연결했으면 「단말 다시 확인」을 눌러 주세요.")
    state.device == DeviceStatus.NO_RESPONSE ->
        Copy("단말이 답하지 않아요", "USB 연결과 단말의 설정 모드(KEY1+KEY4)를 확인해 주세요.")
    !state.deviceSeen -> Copy("단말을 찾고 있어요", null)
    state.input == null -> Copy("공유기의 Wi-Fi 정보를\n알려 주세요", "QR을 찍거나 직접 입력할 수 있어요.")
    else -> Copy("이 정보로 보낼까요?", "글자 하나까지 공유기와 같은지 확인해 주세요.")
}

@Composable
private fun Headline(state: UiState) {
    AnimatedContent(
        targetState = copyFor(state),
        transitionSpec = {
            (slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith
                (slideOutVertically(tween(140)) { -it / 4 } + fadeOut(tween(140))) using
                SizeTransform(clip = false)
        },
        label = "headline",
    ) { copy ->
        Column(Modifier.fillMaxWidth()) {
            Text(copy.title, style = MaterialTheme.typography.headlineMedium, color = Hanna.Ink)
            if (copy.sub != null) {
                Spacer(Modifier.height(8.dp))
                Text(copy.sub, style = MaterialTheme.typography.bodyMedium, color = Hanna.InkSoft)
            }
        }
    }
}

// ── 폰 ── 단말 ─────────────────────────────────────────────────────────────

private fun linkModeFor(state: UiState): LinkMode = when {
    state.usb != Light.OK -> LinkMode.OFF
    state.send == SendStatus.Sending -> LinkMode.SENDING
    state.send == SendStatus.Ok -> LinkMode.SUCCESS
    state.send is SendStatus.Fail || state.send == SendStatus.NoResponse -> LinkMode.BROKEN
    state.device == DeviceStatus.NO_RESPONSE -> LinkMode.BROKEN
    state.device == DeviceStatus.OK -> LinkMode.CONNECTED
    else -> LinkMode.SEARCHING
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LinkCard(state: UiState, onRecheck: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Hanna.Surface)
            .padding(20.dp),
    ) {
        LinkStrip(
            mode = linkModeFor(state),
            deviceLabel = state.deviceInfo?.let { info ->
                listOfNotNull(info.model, info.mac?.takeLast(6)).joinToString(" · ").ifEmpty { null }
            } ?: "단말",
        )
        Spacer(Modifier.height(16.dp))

        // 사양서의 상태 세 줄(§16). 글자와 색을 같이 쓴다(§17).
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusChip(
                "USB " + when (state.usb) {
                    Light.OK -> "연결 정상"
                    Light.ERROR -> "연결 오류"
                    Light.WAITING -> "확인 중"
                },
                state.usb,
            )
            StatusChip(
                "단말 " + when (state.device) {
                    DeviceStatus.OK -> "응답 정상"
                    DeviceStatus.NO_RESPONSE -> "응답 없음"
                    DeviceStatus.CHECKING -> "확인 중"
                    DeviceStatus.REBOOT_SENT -> "재부팅 보냄"
                },
                when (state.device) {
                    DeviceStatus.OK -> Light.OK
                    DeviceStatus.NO_RESPONSE -> Light.ERROR
                    else -> Light.WAITING
                },
            )
            if (state.send != SendStatus.Idle) {
                StatusChip(
                    "전송 " + when (state.send) {
                        SendStatus.Ok -> "정상"
                        is SendStatus.Fail -> "실패"
                        SendStatus.NoResponse -> "응답 없음"
                        else -> "중"
                    },
                    when (state.send) {
                        SendStatus.Ok -> Light.OK
                        is SendStatus.Fail, SendStatus.NoResponse -> Light.ERROR
                        else -> Light.WAITING
                    },
                )
            }
        }

        state.deviceInfo?.ssid?.takeIf { it.isNotEmpty() }?.let { current ->
            Spacer(Modifier.height(12.dp))
            Text("단말에 저장된 Wi-Fi  $current", style = MaterialTheme.typography.labelMedium, color = Hanna.Sub)
        }

        AnimatedVisibility(
            visible = state.device == DeviceStatus.NO_RESPONSE || state.device == DeviceStatus.REBOOT_SENT,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            HannaButton(
                "단말 다시 확인",
                onRecheck,
                Modifier.fillMaxWidth().padding(top = 16.dp),
                tone = ButtonTone.SOFT_BLUE,
                enabled = state.canRecheck,
                height = 48.dp,
            )
        }
    }
}

@Composable
private fun StatusChip(text: String, light: Light) {
    val color by animateColorAsState(
        when (light) {
            Light.OK -> Hanna.Green
            Light.ERROR -> Hanna.Red
            Light.WAITING -> Hanna.Sub
        },
        tween(240), label = "chip",
    )
    Row(
        Modifier
            .clip(CircleShape)
            .background(Hanna.Fill)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = Hanna.InkSoft)
    }
}

// ── Wi-Fi 정보 ─────────────────────────────────────────────────────────────

@Composable
private fun WifiArea(state: UiState, onScan: () -> Unit, onManual: () -> Unit) {
    AnimatedContent(
        targetState = state.input,
        transitionSpec = {
            (fadeIn(tween(240)) + scaleIn(tween(240), initialScale = 0.97f)) togetherWith fadeOut(tween(120))
        },
        label = "wifi",
    ) { input ->
        if (input == null) {
            Row(
                Modifier.alpha(if (state.deviceSeen) 1f else 0.45f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InputTile("QR 촬영", "스티커를 찍어요", state.canInput, onScan, Modifier.weight(1f)) { qrGlyph(it) }
                InputTile("직접 입력", "손으로 써요", state.canInput, onManual, Modifier.weight(1f)) {
                    keyboardGlyph(it)
                }
            }
        } else {
            CredentialCard(input, state.canInput, onScan, onManual)
        }
    }
}

@Composable
private fun InputTile(
    title: String,
    sub: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    glyph: DrawScope.(Color) -> Unit,
) {
    Column(
        modifier
            .pressable(onClick, enabled = enabled, pressedScale = 0.95f)
            .clip(CardShape)
            .background(Hanna.Surface)
            .padding(20.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Hanna.BlueSoft)) {
            Canvas(Modifier.fillMaxSize()) { glyph(Hanna.Blue) }
        }
        Spacer(Modifier.height(28.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Hanna.Ink)
        Spacer(Modifier.height(2.dp))
        Text(sub, style = MaterialTheme.typography.labelMedium, color = Hanna.Sub)
    }
}

@Composable
private fun CredentialCard(input: WifiInput, canInput: Boolean, onScan: () -> Unit, onManual: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Hanna.Surface)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (input.source == InputSource.QR) {
                Box(Modifier.size(8.dp).background(Hanna.Green, CircleShape))
                Spacer(Modifier.size(6.dp))
            }
            Text(
                if (input.source == InputSource.QR) "QR로 읽었어요 · 형식 정상" else "직접 입력했어요",
                style = MaterialTheme.typography.labelMedium,
                color = Hanna.InkSoft,
            )
        }

        Spacer(Modifier.height(20.dp))
        Text("Wi-Fi 이름 (SSID)", style = MaterialTheme.typography.labelMedium, color = Hanna.Sub)
        Spacer(Modifier.height(4.dp))
        SelectionContainer { Text(legible(input.credential.ssid), style = CredentialStyle, color = Hanna.Ink) }

        Spacer(Modifier.height(18.dp))
        Text("비밀번호 (PASSWORD)", style = MaterialTheme.typography.labelMedium, color = Hanna.Sub)
        Spacer(Modifier.height(4.dp))
        if (input.credential.password.isEmpty()) {
            Text("없음 — 비밀번호 없는 Wi-Fi", style = MaterialTheme.typography.bodyLarge, color = Hanna.Sub)
        } else {
            // 가리지 않는다(§10). 작업자가 눈으로 확인하는 값이다.
            SelectionContainer { Text(legible(input.credential.password), style = CredentialStyle, color = Hanna.Ink) }
            Spacer(Modifier.height(6.dp))
            Text("숫자는 파란색이에요", style = MaterialTheme.typography.labelSmall, color = Hanna.Sub)
        }

        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HannaButton(
                if (input.source == InputSource.QR) "QR 다시 촬영" else "QR 촬영",
                onScan, Modifier.weight(1f), ButtonTone.SOFT_GREY, enabled = canInput, height = 48.dp,
            )
            HannaButton("직접 입력", onManual, Modifier.weight(1f), ButtonTone.SOFT_GREY, enabled = canInput, height = 48.dp)
        }
    }
}

/**
 * 숫자를 파랗게 칠한다 — 0 과 O, 1 과 l 을 눈으로 가르는 가장 빠른 방법이다.
 * **앞뒤** 공백은 `␣` 로 보여 준다. 비밀번호 끝의 공백은 다듬지 않고 그대로 나가는데
 * 눈에는 안 보인다. 가운데 공백은 고정폭이라 빈칸으로 충분히 보인다.
 */
private fun legible(value: String): AnnotatedString = buildAnnotatedString {
    val first = value.indexOfFirst { it != ' ' }
    val last = value.indexOfLast { it != ' ' }
    value.forEachIndexed { i, c ->
        when {
            c.isDigit() -> withStyle(SpanStyle(color = Hanna.Blue)) { append(c) }
            c == ' ' && (i < first || i > last) -> withStyle(SpanStyle(color = Hanna.Red)) { append('␣') }
            else -> append(c)
        }
    }
}

// ── 바닥의 두 버튼 ─────────────────────────────────────────────────────────

@Composable
private fun BottomBar(state: UiState, onSend: () -> Unit, onReboot: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to Hanna.Ground.copy(alpha = 0f), 0.28f to Hanna.Ground))
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 자리는 바뀌지 않는다(손이 기억한다). 파란색만 「다음에 누를 것」을 따라간다 —
            // 저장에 성공하면 다음은 재부팅이다. 재부팅은 언제든 누를 수 있다(§14).
            val sent = state.send == SendStatus.Ok
            HannaButton(
                "재부팅", onReboot, Modifier.weight(1f),
                tone = if (sent) ButtonTone.PRIMARY else ButtonTone.SOFT_BLUE,
                enabled = state.canReboot,
            )
            HannaButton(
                text = if (state.send == SendStatus.Idle) "정보 전송" else "재전송",
                onClick = onSend,
                modifier = Modifier.weight(2f),
                // 바탕이 회색이라 회색 버튼은 묻힌다. 물러날 때도 연한 파랑으로 남는다.
                tone = if (sent) ButtonTone.SOFT_BLUE else ButtonTone.PRIMARY,
                enabled = state.canSend,
                loading = state.send == SendStatus.Sending,
            )
        }
    }
}

// ── 다이얼로그 ─────────────────────────────────────────────────────────────

/** 수동 입력 (앱 사양 §8). 비밀번호는 가리지 않는다 — 작업자가 확인해야 한다. */
@Composable
private fun ManualInputDialog(
    initial: WifiCredential?,
    onDismiss: () -> Unit,
    onConfirm: (WifiCredential) -> Unit,
) {
    var ssid by remember { mutableStateOf(initial?.ssid.orEmpty()) }
    var password by remember { mutableStateOf(initial?.password.orEmpty()) }

    // 단말도 SSID 앞뒤 공백은 다듬고 PASSWORD 는 그대로 둔다. 같은 규칙으로 맞춘다.
    val candidate = WifiCredential(
        ssid = ssid.trim(),
        password = password,
        security = if (password.isEmpty()) Security.OPEN else Security.WPA,
    )
    val valid = isSendable(candidate)
    val tooLong = candidate.ssid.isNotEmpty() && !valid

    // 자동 교정·첫 글자 대문자가 값을 몰래 바꾸지 못하게 한다.
    val keyboard = KeyboardOptions(
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
        keyboardType = KeyboardType.Password,
    )

    HannaDialog(onDismiss) {
        Text("Wi-Fi 정보를\n입력해 주세요", style = MaterialTheme.typography.titleLarge, color = Hanna.Ink)
        Spacer(Modifier.height(20.dp))
        // 한글 SSID 가 있어서 이름 칸은 일반 자판이다.
        HannaField("Wi-Fi 이름 (SSID)", ssid, { ssid = it }, keyboard.copy(keyboardType = KeyboardType.Text))
        Spacer(Modifier.height(16.dp))
        HannaField("비밀번호 (PASSWORD)", password, { password = it }, keyboard, hint = "없으면 비워 두세요")

        AnimatedVisibility(tooLong) {
            Text(
                "너무 길어요. 이름은 ${SSID_MAX_BYTES}바이트(한글 10자), 비밀번호는 ${PASSWORD_MAX_BYTES}바이트까지예요.",
                style = MaterialTheme.typography.labelMedium,
                color = Hanna.Red,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HannaButton("취소", onDismiss, Modifier.weight(1f), ButtonTone.SOFT_GREY)
            HannaButton("확인", { onConfirm(candidate) }, Modifier.weight(2f), enabled = valid)
        }
    }
}

@Composable
private fun NoticeDialog(notice: Notice, onDismiss: () -> Unit) {
    val (title, body) = when (notice) {
        Notice.UNSUPPORTED_QR ->
            "지원하지 않는 QR 코드예요" to "공유기의 Wi-Fi QR 코드를\n다시 촬영해 주세요."
        Notice.WEP_BLOCKED ->
            "이 공유기는 WEP 보안을 써요" to
                "단말이 연결할 수 없어요.\n공유기 설정에서 보안 방식을 WPA2 로 바꾼 뒤 다시 촬영해 주세요."
        Notice.CAMERA_DENIED ->
            "카메라를 쓸 수 없어요" to "카메라 권한을 허용하거나,\n「직접 입력」으로 넣어 주세요."
    }
    MessageDialog(title, body, confirm = "확인", onConfirm = onDismiss)
}

@Composable
private fun FatalDialog(fatal: Fatal, onCloseApp: () -> Unit, onUseDemo: (() -> Unit)?) {
    val (title, body) = when (fatal) {
        Fatal.NO_USB_DEVICE ->
            "USB Serial 장치가\n연결되어 있지 않아요" to "USB 장치를 먼저 연결한 뒤\n앱을 다시 실행해 주세요."
        Fatal.USB_PERMISSION_DENIED ->
            "USB 장치를 쓸 수 없어요" to "앱을 다시 실행해서\nUSB 접근 권한을 허용해 주세요."
        Fatal.USB_OPEN_FAILED ->
            "USB Serial 포트를\n열 수 없어요" to "USB 장치를 뽑았다 다시 연결한 뒤\n앱을 다시 실행해 주세요."
    }
    // 바깥을 눌러도 닫히지 않는다. 닫기 = 앱 종료(§3.2).
    MessageDialog(
        title, body,
        confirm = "닫기", onConfirm = onCloseApp, dismissable = false,
        secondary = onUseDemo?.let { "가상 단말" }, onSecondary = onUseDemo,
    )
}

// ── 그림 글자 ──────────────────────────────────────────────────────────────

/** QR 의 위치 찾기 무늬 셋과 점 하나. */
private fun DrawScope.qrGlyph(color: Color) {
    val s = 8.dp.toPx()
    val stroke = Stroke(2.2.dp.toPx())
    val pad = 11.dp.toPx()
    val far = size.width - pad - s
    for (corner in listOf(Offset(pad, pad), Offset(far, pad), Offset(pad, far))) {
        drawRoundRect(color, corner, Size(s, s), CornerRadius(2.dp.toPx()), style = stroke)
    }
    drawRoundRect(color, Offset(far + 1.dp.toPx(), far + 1.dp.toPx()), Size(s - 2.dp.toPx(), s - 2.dp.toPx()), CornerRadius(2.dp.toPx()))
}

/** 자판: 테두리, 키 세 개, 스페이스 바. */
private fun DrawScope.keyboardGlyph(color: Color) {
    val w = 26.dp.toPx()
    val h = 18.dp.toPx()
    val left = (size.width - w) / 2
    val top = (size.height - h) / 2
    drawRoundRect(color, Offset(left, top), Size(w, h), CornerRadius(4.dp.toPx()), style = Stroke(2.2.dp.toPx()))
    for (i in 0..2) {
        drawCircle(color, 1.4.dp.toPx(), Offset(left + 7.dp.toPx() + i * 6.dp.toPx(), top + 6.5.dp.toPx()))
    }
    drawLine(
        color, Offset(left + 8.dp.toPx(), top + 12.dp.toPx()), Offset(left + w - 8.dp.toPx(), top + 12.dp.toPx()),
        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
    )
}

// ── 미리보기: 하드웨어 없이 상태별 화면을 본다 ────────────────────────────

@Composable
private fun PreviewScreen(state: UiState) = HannaaircastTheme {
    SetupScreen(state, "1.0.0", {}, {}, {}, {}, {}, {}, {})
}

private val previewDevice = DeviceInfo("58e6c5f2cc74", "IOT-1000", "V.260823-1", "old_ap")
private val previewInput =
    WifiInput(WifiCredential("SK_DCC0_2.4G", "BMF00@1399", Security.WPA), InputSource.QR)
private val ready =
    UiState(usb = Light.OK, device = DeviceStatus.OK, deviceInfo = previewDevice, deviceSeen = true)

@Preview(name = "1 단말 찾는 중", showBackground = true)
@Composable
private fun PreviewChecking() = PreviewScreen(UiState(usb = Light.OK, busy = true))

@Preview(name = "2 준비", showBackground = true)
@Composable
private fun PreviewReady() = PreviewScreen(ready)

@Preview(name = "3 QR 입력됨", showBackground = true)
@Composable
private fun PreviewInput() = PreviewScreen(ready.copy(input = previewInput))

@Preview(name = "4 전송 정상", showBackground = true)
@Composable
private fun PreviewSent() = PreviewScreen(ready.copy(input = previewInput, send = SendStatus.Ok))

@Preview(name = "5 단말 거절", showBackground = true)
@Composable
private fun PreviewFail() = PreviewScreen(
    ready.copy(
        input = WifiInput(WifiCredential("FREE_WIFI", "", Security.OPEN), InputSource.MANUAL),
        send = SendStatus.Fail("SSID and PASSWORD must be sent together"),
    ),
)

@Preview(name = "6 단말 응답 없음", showBackground = true)
@Composable
private fun PreviewNoResponse() =
    PreviewScreen(UiState(usb = Light.OK, device = DeviceStatus.NO_RESPONSE))

@Preview(name = "7 USB 없음", showBackground = true)
@Composable
private fun PreviewNoUsb() =
    PreviewScreen(UiState(usb = Light.ERROR, fatal = Fatal.NO_USB_DEVICE))
