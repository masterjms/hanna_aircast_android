package kr.co.hannaaircast.wifisetup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.co.hannaaircast.wifisetup.ui.theme.HannaaircastTheme
import kr.co.hannaaircast.wifisetup.wifi.PASSWORD_MAX_BYTES
import kr.co.hannaaircast.wifisetup.wifi.SSID_MAX_BYTES
import kr.co.hannaaircast.wifisetup.wifi.Security
import kr.co.hannaaircast.wifisetup.wifi.WifiCredential
import kr.co.hannaaircast.wifisetup.wifi.isSendable

private val Green = Color(0xFF2E7D32)
private val Grey = Color(0xFF9E9E9E)
private val Red = Color(0xFFC62828)

/** 현장에서 장갑 낀 손으로 누른다. 버튼은 크게. */
private val ButtonHeight = 56.dp

/**
 * 앱의 유일한 화면 (앱 사양 §16). 상태를 받아 그리기만 한다 — 시리얼도 USB 도 모른다.
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

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Wi-Fi 설정", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

            ConnectionCard(state, onRecheck)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onScan,
                    enabled = state.canInput,
                    modifier = Modifier.weight(1f).height(ButtonHeight),
                ) { Text(if (state.input?.source == InputSource.QR) "QR 다시 촬영" else "QR 촬영") }
                OutlinedButton(
                    onClick = { manualOpen = true },
                    enabled = state.canInput,
                    modifier = Modifier.weight(1f).height(ButtonHeight),
                ) { Text("수동 입력") }
            }

            WifiCard(state.input)

            Button(
                onClick = onSend,
                enabled = state.canSend,
                modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            ) {
                Text(
                    when (state.send) {
                        SendStatus.Sending -> "전송 중…"
                        SendStatus.Idle -> "정보 전송"
                        else -> "재전송"
                    },
                )
            }

            SendResultRow(state.send)

            FilledTonalButton(
                onClick = onReboot,
                enabled = state.canReboot,
                modifier = Modifier.fillMaxWidth().height(ButtonHeight),
            ) { Text("재부팅") }

            Text(
                "v$version",
                style = MaterialTheme.typography.labelSmall,
                color = Grey,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
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
    state.fatal?.let { fatal ->
        FatalDialog(fatal, onCloseApp, onUseDemo.takeIf { fatal == Fatal.NO_USB_DEVICE })
    }
}

@Composable
private fun ConnectionCard(state: UiState, onRecheck: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusRow(
                "USB Serial",
                state.usb,
                when (state.usb) {
                    Light.OK -> "연결 정상"
                    Light.ERROR -> "연결 오류"
                    Light.WAITING -> "확인 중"
                },
            )
            StatusRow(
                "단말 응답",
                when (state.device) {
                    DeviceStatus.OK -> Light.OK
                    DeviceStatus.NO_RESPONSE -> Light.ERROR
                    else -> Light.WAITING
                },
                when (state.device) {
                    DeviceStatus.OK -> "정상"
                    DeviceStatus.NO_RESPONSE -> "응답 없음"
                    DeviceStatus.CHECKING -> "확인 중"
                    DeviceStatus.REBOOT_SENT -> "재부팅 명령 보냄"
                },
            )

            state.deviceInfo?.let { info ->
                val line = listOfNotNull(
                    info.model,
                    info.mac,
                    info.ssid?.takeIf { it.isNotEmpty() }?.let { "현재 Wi-Fi: $it" },
                ).joinToString("  ·  ")
                if (line.isNotEmpty()) {
                    Text(line, style = MaterialTheme.typography.bodySmall, color = Grey)
                }
            }

            if (state.device == DeviceStatus.NO_RESPONSE) {
                Text(
                    "단말기 응답이 없습니다.\nUSB 연결을 확인하고, 단말이 설정 모드(KEY1+KEY4)인지 확인해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Red,
                )
            }
            if (state.device == DeviceStatus.REBOOT_SENT) {
                Text(
                    "다음 단말을 연결했으면 「단말 다시 확인」을 눌러 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.device == DeviceStatus.NO_RESPONSE || state.device == DeviceStatus.REBOOT_SENT) {
                OutlinedButton(onClick = onRecheck, enabled = state.canRecheck) { Text("단말 다시 확인") }
            }
        }
    }
}

@Composable
private fun WifiCard(input: WifiInput?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (input == null) {
                Text(
                    "공유기의 Wi-Fi QR 코드를 촬영하거나\nSSID와 PASSWORD를 직접 입력해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grey,
                )
                return@Column
            }
            LabelValue("입력 방식", if (input.source == InputSource.QR) "QR" else "수동")
            if (input.source == InputSource.QR) StatusRow("QR 형식", Light.OK, "정상")
            HorizontalDivider()
            // 작업자가 눈으로 확인하는 값이다. 가리지 않고(§10), 0/O·1/l 이 구별되는 글꼴로.
            CredentialValue("SSID", input.credential.ssid)
            CredentialValue(
                "PASSWORD",
                input.credential.password.ifEmpty { null },
                emptyText = "(없음 — 비밀번호 없는 Wi-Fi)",
            )
        }
    }
}

@Composable
private fun CredentialValue(label: String, value: String?, emptyText: String = "") {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Grey)
        if (value == null) {
            Text(emptyText, style = MaterialTheme.typography.bodyLarge, color = Grey)
        } else {
            SelectionContainer {
                Text(value, fontFamily = FontFamily.Monospace, fontSize = 22.sp, lineHeight = 28.sp)
            }
        }
    }
}

@Composable
private fun SendResultRow(send: SendStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusRow(
            "전송 결과",
            when (send) {
                SendStatus.Ok -> Light.OK
                is SendStatus.Fail, SendStatus.NoResponse -> Light.ERROR
                else -> Light.WAITING
            },
            when (send) {
                SendStatus.Idle -> "대기"
                SendStatus.Sending -> "전송 중"
                SendStatus.Ok -> "정상"
                is SendStatus.Fail -> "실패"
                SendStatus.NoResponse -> "응답 없음"
            },
        )
        val detail = when (send) {
            is SendStatus.Fail -> send.reason?.let { "단말: $it" } ?: "전송에 실패했습니다. USB 연결을 확인해 주세요."
            SendStatus.NoResponse -> "단말이 설정 모드(KEY1+KEY4)인지 확인한 뒤 재전송해 주세요."
            else -> null
        }
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = Red)
    }
}

@Composable
private fun StatusRow(label: String, light: Light, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val color = when (light) {
            Light.OK -> Green
            Light.ERROR -> Red
            Light.WAITING -> Grey
        }
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun LabelValue(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("수동 입력") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("SSID") },
                    singleLine = true,
                    // 한글 SSID 가 있어서 SSID 칸은 일반 자판이다.
                    keyboardOptions = keyboard.copy(keyboardType = KeyboardType.Text),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("PASSWORD") },
                    supportingText = { Text("비밀번호 없는 Wi-Fi 는 비워 둡니다") },
                    singleLine = true,
                    keyboardOptions = keyboard,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (tooLong) {
                    Text(
                        "너무 깁니다. SSID 는 ${SSID_MAX_BYTES}바이트(한글 10자), " +
                            "PASSWORD 는 ${PASSWORD_MAX_BYTES}바이트까지입니다.",
                        color = Red,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(candidate) }, enabled = valid) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun NoticeDialog(notice: Notice, onDismiss: () -> Unit) {
    val (title, body) = when (notice) {
        Notice.UNSUPPORTED_QR ->
            "지원하지 않는 QR 코드입니다." to "공유기의 Wi-Fi QR 코드를\n다시 촬영해 주세요."
        Notice.WEP_BLOCKED ->
            "이 공유기는 WEP 보안을 사용합니다." to
                "단말이 연결할 수 없습니다.\n공유기 설정에서 보안 방식을\nWPA2 로 변경한 뒤 다시 촬영해 주세요."
        Notice.CAMERA_DENIED ->
            "카메라를 사용할 수 없습니다." to
                "카메라 권한을 허용하거나,\n「수동 입력」으로 SSID와 PASSWORD를 입력해 주세요."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
    )
}

@Composable
private fun FatalDialog(fatal: Fatal, onCloseApp: () -> Unit, onUseDemo: (() -> Unit)?) {
    val body = when (fatal) {
        Fatal.NO_USB_DEVICE ->
            "USB Serial 장치가 연결되어 있지 않습니다.\n\nUSB 장치를 먼저 연결한 후\n앱을 다시 실행해 주세요."
        Fatal.USB_PERMISSION_DENIED ->
            "USB 장치를 사용할 수 없습니다.\nUSB 접근 권한을 허용해 주세요."
        Fatal.USB_OPEN_FAILED ->
            "USB Serial 포트를 열 수 없습니다.\n\nUSB 장치를 뽑았다 다시 연결한 후\n앱을 다시 실행해 주세요."
    }
    AlertDialog(
        // 바깥을 눌러도 닫히지 않는다. 닫기 = 앱 종료(§3.2).
        onDismissRequest = {},
        text = { Text(body, modifier = Modifier.heightIn(min = 48.dp)) },
        confirmButton = { TextButton(onClick = onCloseApp) { Text("닫기") } },
        dismissButton = onUseDemo?.let { { TextButton(onClick = it) { Text("가상 단말 (디버그)") } } },
    )
}

// ── 미리보기: 하드웨어 없이 상태별 화면을 본다 ────────────────────────────

@Composable
private fun PreviewScreen(state: UiState) = HannaaircastTheme(dynamicColor = false) {
    SetupScreen(state, "1.0.0", {}, {}, {}, {}, {}, {}, {})
}

private val previewDevice = DeviceInfo("58e6c5f2cc74", "IOT-1000", "V.260823-1", "old_ap")
private val previewInput =
    WifiInput(WifiCredential("SK_DCC0_2.4G", "BMF00@1399", Security.WPA), InputSource.QR)
private val ready =
    UiState(usb = Light.OK, device = DeviceStatus.OK, deviceInfo = previewDevice, deviceSeen = true)

@Preview(name = "1 단말 확인 중", showBackground = true)
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
