package kr.co.hannaaircast.wifisetup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kr.co.hannaaircast.wifisetup.serial.ExchangeResult
import kr.co.hannaaircast.wifisetup.serial.ProvClient
import kr.co.hannaaircast.wifisetup.serial.ProvProtocol
import kr.co.hannaaircast.wifisetup.serial.SerialTransport
import kr.co.hannaaircast.wifisetup.wifi.WifiCredential
import kr.co.hannaaircast.wifisetup.wifi.WifiQrResult
import kr.co.hannaaircast.wifisetup.wifi.parseWifiQr
import java.io.IOException

/** 상태 표시등 (앱 사양 §17): 정상 녹색 · 대기 회색 · 오류 빨강. */
enum class Light { WAITING, OK, ERROR }

enum class InputSource { QR, MANUAL }

data class WifiInput(val credential: WifiCredential, val source: InputSource)

/** `@GET` 응답에서 꺼낸, 지금 물려 있는 단말. */
data class DeviceInfo(val mac: String?, val model: String?, val firmware: String?, val ssid: String?)

enum class DeviceStatus { CHECKING, OK, NO_RESPONSE, REBOOT_SENT }

sealed interface SendStatus {
    data object Idle : SendStatus
    data object Sending : SendStatus
    data object Ok : SendStatus

    /** 단말이 `@RESULT=FAIL` 로 거절했다. [reason] 은 단말이 준 `@ERROR`. */
    data class Fail(val reason: String?) : SendStatus
    data object NoResponse : SendStatus
}

/** 닫으면 앱이 끝나는 오류 (앱 사양 §3.2, §4). */
enum class Fatal { NO_USB_DEVICE, USB_PERMISSION_DENIED, USB_OPEN_FAILED }

/** 닫으면 화면으로 돌아오는 안내. */
enum class Notice { UNSUPPORTED_QR, WEP_BLOCKED, CAMERA_DENIED }

data class UiState(
    val usb: Light = Light.WAITING,
    val device: DeviceStatus = DeviceStatus.CHECKING,
    val deviceInfo: DeviceInfo? = null,
    /** 단말이 한 번이라도 응답했는가. QR·수동 입력은 그 뒤에 열린다(§18). */
    val deviceSeen: Boolean = false,
    val input: WifiInput? = null,
    val send: SendStatus = SendStatus.Idle,
    /** 시리얼 명령이 나가 있는 동안. 명령은 한 번에 하나다. */
    val busy: Boolean = false,
    val fatal: Fatal? = null,
    val notice: Notice? = null,
) {
    val canInput: Boolean get() = deviceSeen && !busy
    val canSend: Boolean get() = input != null && usb == Light.OK && !busy

    /** 재부팅은 상태와 관계없이 항상 누를 수 있다(§14). 포트가 열려 있기만 하면 된다. */
    val canReboot: Boolean get() = usb == Light.OK && !busy
    val canRecheck: Boolean get() = usb == Light.OK && !busy && device != DeviceStatus.OK
}

class SetupViewModel(
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** 테스트가 가짜 시계를 끼운다. */
    private val newClient: (SerialTransport) -> ProvClient = { ProvClient(it) },
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var client: ProvClient? = null
    private val serial = Mutex()

    /** USB 검색·권한은 앱 시작 시 1회만 한다. 화면이 다시 만들어져도 반복하지 않는다. */
    var started = false
        private set

    fun markStarted() {
        started = true
    }

    fun fail(fatal: Fatal) = _state.update { it.copy(usb = Light.ERROR, fatal = fatal) }

    /** 디버그 빌드의 가상 단말로 계속한다. 「USB 없음」 팝업을 걷고 평소처럼 연결한다. */
    fun connectDemo(transport: SerialTransport) {
        _state.update { it.copy(fatal = null, usb = Light.WAITING) }
        connect { transport }
    }

    /** 권한을 받은 뒤. 포트를 열고 곧바로 `@GET` 으로 단말을 확인한다(§6). */
    fun connect(open: () -> SerialTransport) {
        viewModelScope.launch {
            val transport = try {
                withContext(io) { open() }
            } catch (_: IOException) {
                fail(Fatal.USB_OPEN_FAILED)
                return@launch
            }
            client = newClient(transport)
            _state.update { it.copy(usb = Light.OK) }
            checkDevice()
        }
    }

    fun checkDevice() = command(
        before = { it.copy(device = DeviceStatus.CHECKING) },
        run = { exchange(ProvProtocol.getFrame()) },
    ) { state, result ->
        when (result) {
            is ExchangeResult.Ok -> if (result.response.ok) {
                val r = result.response
                state.copy(
                    device = DeviceStatus.OK,
                    deviceSeen = true,
                    deviceInfo = DeviceInfo(r.mac, r.model, r.firmware, r.ssid),
                )
            } else {
                state.copy(device = DeviceStatus.NO_RESPONSE, deviceInfo = null)
            }
            ExchangeResult.Timeout -> state.copy(device = DeviceStatus.NO_RESPONSE, deviceInfo = null)
            is ExchangeResult.IoError ->
                state.copy(usb = Light.ERROR, device = DeviceStatus.NO_RESPONSE, deviceInfo = null)
        }
    }

    /** QR 에서 읽은 문자열. 잘못된 QR 은 단말에 보내지 않는다(§7.3, §7.4). */
    fun onQrScanned(raw: String) {
        when (val parsed = parseWifiQr(raw)) {
            is WifiQrResult.Ok -> setInput(WifiInput(parsed.credential, InputSource.QR))
            WifiQrResult.WepBlocked -> notify(Notice.WEP_BLOCKED)
            WifiQrResult.Unsupported -> notify(Notice.UNSUPPORTED_QR)
        }
    }

    fun onManualInput(credential: WifiCredential) =
        setInput(WifiInput(credential, InputSource.MANUAL))

    /** 새 입력이 확정되면 이전 전송 결과는 지운다(§8.3, §9). */
    private fun setInput(input: WifiInput) =
        _state.update { it.copy(input = input, send = SendStatus.Idle) }

    fun notify(notice: Notice) = _state.update { it.copy(notice = notice) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /** 전송·재전송. 화면에 있는 값을 그대로 다시 보낸다(§13). */
    fun send() {
        val credential = _state.value.input?.credential ?: return
        command(
            before = { it.copy(send = SendStatus.Sending) },
            run = { exchange(ProvProtocol.wifiFrame(credential)) },
        ) { state, result ->
            when (result) {
                is ExchangeResult.Ok ->
                    if (result.response.ok) {
                        state.copy(send = SendStatus.Ok)
                    } else {
                        state.copy(send = SendStatus.Fail(result.response.error))
                    }
                ExchangeResult.Timeout -> state.copy(send = SendStatus.NoResponse)
                is ExchangeResult.IoError ->
                    state.copy(usb = Light.ERROR, send = SendStatus.Fail(null))
            }
        }
    }

    /** `@OFF`. 재부팅이 끝났는지는 확인하지 않는다(§14). */
    fun reboot() = command(
        before = { it },
        run = { sendOnly(ProvProtocol.rebootFrame()) },
    ) { state, error ->
        if (error == null) {
            // 지금 보이는 MAC 은 곧 다른 단말 것이 될 수 있다. 지운다.
            state.copy(device = DeviceStatus.REBOOT_SENT, deviceInfo = null)
        } else {
            state.copy(usb = Light.ERROR)
        }
    }

    private fun <T> command(
        before: (UiState) -> UiState,
        run: ProvClient.() -> T,
        after: (UiState, T) -> UiState,
    ) {
        val client = client ?: return
        if (_state.value.busy) return
        _state.update { before(it).copy(busy = true) }
        viewModelScope.launch {
            val result = serial.withLock { withContext(io) { client.run() } }
            _state.update { after(it, result).copy(busy = false) }
        }
    }

    override fun onCleared() {
        client?.close()
        client = null
    }
}
