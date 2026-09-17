package kr.co.hannaaircast.wifisetup.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kr.co.hannaaircast.wifisetup.ui.theme.HannaaircastTheme
import kr.co.hannaaircast.wifisetup.wifi.Security
import kr.co.hannaaircast.wifisetup.wifi.WifiCredential
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 화면을 PC 에서 PNG 로 그린다 — 폰 없이 디자인을 본다. 검증이 아니라 도구라서
 * 평소 테스트에서는 건너뛴다.
 *
 *     gradlew testDebugUnitTest -Pshots      →  app/build/shots 폴더에 PNG
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xxhdpi")
class ScreenShots {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val device = DeviceInfo("58e6c5f2cc74", "IOT-1000", "V.260823-1", "old_ap")
    private val qr = WifiInput(WifiCredential("SK_DCC0_2.4G", "BMF00@1399", Security.WPA), InputSource.QR)
    private val ready = UiState(usb = Light.OK, device = DeviceStatus.OK, deviceInfo = device, deviceSeen = true)

    @Before
    fun onlyOnRequest() = assumeTrue(System.getProperty("shots") == "true")

    private fun shot(name: String, state: UiState, settleMs: Long = 1500) {
        // 무한 애니메이션(선 위의 빛)이 있어서 시계를 직접 돌린다.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            HannaaircastTheme { SetupScreen(state, "1.0.0", {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.mainClock.advanceTimeBy(settleMs)
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Test fun s1_searching() = shot("1_searching", UiState(usb = Light.OK, busy = true), settleMs = 400)

    @Test fun s2_ready() = shot("2_ready", ready)

    @Test fun s3_input() = shot("3_input", ready.copy(input = qr))

    @Test fun s4_sending() = shot("4_sending", ready.copy(input = qr, send = SendStatus.Sending, busy = true), 700)

    @Test fun s5_sent() = shot("5_sent", ready.copy(input = qr, send = SendStatus.Ok))

    @Test fun s6_rejected() = shot(
        "6_rejected",
        ready.copy(
            input = WifiInput(WifiCredential("우리집 와이파이", "", Security.OPEN), InputSource.MANUAL),
            send = SendStatus.Fail("SSID and PASSWORD must be sent together"),
        ),
    )

    @Test fun s7_silent() = shot("7_silent", UiState(usb = Light.OK, device = DeviceStatus.NO_RESPONSE))
}
