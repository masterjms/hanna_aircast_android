package kr.co.hannaaircast.wifisetup.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kr.co.hannaaircast.wifisetup.serial.FakeTransport
import kr.co.hannaaircast.wifisetup.serial.ProvClient
import kr.co.hannaaircast.wifisetup.wifi.Security
import kr.co.hannaaircast.wifisetup.wifi.WifiCredential
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** 앱 사양서의 버튼·상태 규칙(§6, §8.3, §9, §13, §14, §18)을 고정한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    private val getOk = "@RESULT=OK\n@MAC=58e6c5f2cc74\n@MODEL=IOT-1000\n@SSID=old_ap\n@END\n"
    private val writeOk = "@RESULT=OK\n@SSID=x\n@PASSWORD=SET\n@END\n"

    /** `@GET` 에는 항상 답하고, 쓰기에는 [onWrite] 가 정한 대로 답하는 단말. */
    private fun device(onWrite: () -> List<String> = { listOf(writeOk) }) = FakeTransport { frame ->
        when {
            frame.startsWith("@GET") -> listOf(getOk)
            frame.startsWith("@SSID") -> onWrite()
            else -> emptyList()
        }
    }

    // 가짜 선의 시계를 쓴다 — 응답 없음 테스트가 실제로 3초를 기다리지 않는다.
    private fun viewModel(transport: FakeTransport? = null) =
        SetupViewModel(io = dispatcher, newClient = { ProvClient(it, clock = { transport?.nowMs ?: 0L }) })

    private fun connected(transport: FakeTransport) = viewModel(transport).apply { connect { transport } }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun connect_sendsGet_andOpensInput() {
        val t = device()
        val s = connected(t).state.value
        assertEquals(listOf("@GET\n@END\n"), t.written)
        assertEquals(Light.OK, s.usb)
        assertEquals(DeviceStatus.OK, s.device)
        assertEquals("58e6c5f2cc74", s.deviceInfo?.mac)
        assertTrue(s.canInput)
        assertFalse("입력 전에는 전송할 수 없다", s.canSend)
        assertTrue("재부팅은 항상 누를 수 있다", s.canReboot)
    }

    @Test
    fun silentDevice_keepsInputClosed_butRebootAndRecheckOpen() {
        val s = connected(FakeTransport()).state.value
        assertEquals(DeviceStatus.NO_RESPONSE, s.device)
        assertFalse(s.canInput)
        assertTrue(s.canReboot)
        assertTrue(s.canRecheck)
    }

    @Test
    fun portOpenFailure_isFatal() {
        val vm = viewModel().apply { connect { throw IOException("busy") } }
        assertEquals(Fatal.USB_OPEN_FAILED, vm.state.value.fatal)
    }

    @Test
    fun qr_thenSend_ok() {
        val t = device()
        val vm = connected(t)
        vm.onQrScanned("WIFI:T:WPA;S:HOME_WIFI;P:12345678;;")
        assertEquals(InputSource.QR, vm.state.value.input?.source)
        vm.send()
        assertEquals("@SSID=HOME_WIFI\n@PASSWORD=12345678\n@END\n", t.written.last())
        assertEquals(SendStatus.Ok, vm.state.value.send)
    }

    @Test
    fun badQr_neverReachesTheDevice() {
        val t = device()
        val vm = connected(t)
        vm.onQrScanned("https://example.com")
        assertEquals(Notice.UNSUPPORTED_QR, vm.state.value.notice)
        vm.dismissNotice()
        vm.onQrScanned("WIFI:S:old;T:WEP;P:abcde;;")
        assertEquals(Notice.WEP_BLOCKED, vm.state.value.notice)
        assertNull(vm.state.value.input)
        vm.send()
        assertEquals("@GET 말고는 나간 것이 없다", 1, t.written.size)
    }

    @Test
    fun badQr_keepsThePreviousGoodInput() {
        val vm = connected(device())
        vm.onQrScanned("WIFI:S:good;T:WPA;P:12345678;;")
        vm.onQrScanned("not a wifi qr")
        assertEquals("good", vm.state.value.input?.credential?.ssid)
    }

    @Test
    fun deviceRejects_showsReason_andResendWorks() {
        var reply = listOf("@RESULT=FAIL\n@ERROR=SSID empty\n@END\n")
        val t = device { reply }
        val vm = connected(t)
        vm.onManualInput(WifiCredential("home", "12345678", Security.WPA))
        vm.send()
        assertEquals(SendStatus.Fail("SSID empty"), vm.state.value.send)

        // 다시 입력하지 않고 같은 값을 그대로 다시 보낸다(§13).
        reply = listOf(writeOk)
        assertTrue(vm.state.value.canSend)
        vm.send()
        assertEquals(SendStatus.Ok, vm.state.value.send)
        assertEquals(t.written[1], t.written[2])
    }

    @Test
    fun noReplyToSend_isNoResponse() {
        val vm = connected(device { emptyList() })
        vm.onManualInput(WifiCredential("home", "12345678", Security.WPA))
        vm.send()
        assertEquals(SendStatus.NoResponse, vm.state.value.send)
        assertTrue(vm.state.value.canSend)
    }

    @Test
    fun newInput_clearsPreviousSendResult() {
        val vm = connected(device())
        vm.onQrScanned("WIFI:S:a;T:WPA;P:12345678;;")
        vm.send()
        assertEquals(SendStatus.Ok, vm.state.value.send)
        vm.onManualInput(WifiCredential("b", "", Security.OPEN))
        assertEquals(SendStatus.Idle, vm.state.value.send)
        assertEquals(InputSource.MANUAL, vm.state.value.input?.source)
    }

    @Test
    fun reboot_sendsOff_forgetsDevice_keepsWorkflowOpen() {
        val t = device()
        val vm = connected(t)
        vm.onQrScanned("WIFI:S:a;T:WPA;P:12345678;;")
        vm.reboot()
        val s = vm.state.value
        assertEquals("@OFF\n@END\n", t.written.last())
        assertEquals(DeviceStatus.REBOOT_SENT, s.device)
        assertNull("다음 단말의 MAC 이 아니다", s.deviceInfo)
        // 앱을 다시 켜지 않고 다음 단말로 넘어간다(§15).
        assertTrue(s.canInput)
        assertTrue(s.canReboot)
        assertTrue(s.canRecheck)
    }

    @Test
    fun cableUnpluggedDuringSend_marksUsbError_withoutCrash() {
        val t = device()
        val vm = connected(t)
        vm.onManualInput(WifiCredential("home", "12345678", Security.WPA))
        t.failWrites = true
        vm.send()
        assertEquals(Light.ERROR, vm.state.value.usb)
        assertEquals(SendStatus.Fail(null), vm.state.value.send)
        assertFalse(vm.state.value.canSend)
    }
}
