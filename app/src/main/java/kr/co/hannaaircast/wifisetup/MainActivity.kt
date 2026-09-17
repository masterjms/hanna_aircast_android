package kr.co.hannaaircast.wifisetup

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.google.zxing.client.android.Intents
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kr.co.hannaaircast.wifisetup.serial.UsbSerialTransport
import kr.co.hannaaircast.wifisetup.serial.demoTransportOrNull
import kr.co.hannaaircast.wifisetup.ui.Fatal
import kr.co.hannaaircast.wifisetup.ui.Notice
import kr.co.hannaaircast.wifisetup.ui.SetupScreen
import kr.co.hannaaircast.wifisetup.ui.SetupViewModel
import kr.co.hannaaircast.wifisetup.ui.theme.HannaaircastTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SetupViewModel by viewModels()
    private val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }

    /** 권한 창이 떠 있는 동안 기다리는 장치. */
    private var pendingDriver: UsbSerialDriver? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val driver = pendingDriver ?: return
            pendingDriver = null
            // 인텐트의 EXTRA_PERMISSION_GRANTED 는 안드로이드 버전에 따라 비어 온다.
            // 시스템에 직접 묻는 것이 확실하다.
            if (usbManager.hasPermission(driver.device)) {
                openPort(driver)
            } else {
                viewModel.fail(Fatal.USB_PERMISSION_DENIED)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 화면은 항상 밝다. 폰이 다크 모드여도 상태 표시줄 아이콘은 어둡게 둔다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        ContextCompat.registerReceiver(
            this,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        // USB 검색은 앱 시작 시 1회다(앱 사양 §3.2). 실행 중에 꽂아도 따라가지 않는다.
        if (!viewModel.started) {
            viewModel.markStarted()
            findUsbDevice()
        }

        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

        setContent {
            HannaaircastTheme {
                val state by viewModel.state.collectAsState()

                val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
                    val contents = result.contents
                    when {
                        contents != null -> viewModel.onQrScanned(contents)
                        result.originalIntent?.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION) == true ->
                            viewModel.notify(Notice.CAMERA_DENIED)
                        // 그 외는 사용자가 뒤로 가기로 나온 것이다. 아무 일도 하지 않는다.
                    }
                }

                SetupScreen(
                    state = state,
                    version = version,
                    onScan = { scanner.launch(scanOptions()) },
                    onManualInput = viewModel::onManualInput,
                    onSend = viewModel::send,
                    onReboot = viewModel::reboot,
                    onRecheck = viewModel::checkDevice,
                    onDismissNotice = viewModel::dismissNotice,
                    onCloseApp = { finishAndRemoveTask() },
                    onUseDemo = demoTransportOrNull()?.let { demo -> { viewModel.connectDemo(demo) } },
                )
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(permissionReceiver)
        super.onDestroy()
    }

    private fun findUsbDevice() {
        val driver = UsbSerialTransport.findDriver(usbManager)
        when {
            driver == null -> viewModel.fail(Fatal.NO_USB_DEVICE)
            // 보드를 꽂을 때 뜬 「이 앱으로 열기」로 들어왔으면 권한이 이미 있다.
            usbManager.hasPermission(driver.device) -> openPort(driver)
            else -> requestPermission(driver)
        }
    }

    private fun requestPermission(driver: UsbSerialDriver) {
        pendingDriver = driver
        // 시스템이 결과를 채워 넣으므로 MUTABLE 이어야 하고, 안드로이드 14 부터는
        // MUTABLE 인텐트에 받는 쪽 패키지를 명시해야 한다.
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(packageName)
        usbManager.requestPermission(driver.device, PendingIntent.getBroadcast(this, 0, intent, flags))
    }

    private fun openPort(driver: UsbSerialDriver) =
        viewModel.connect { UsbSerialTransport.open(usbManager, driver) }

    private fun scanOptions() = ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt("공유기의 Wi-Fi QR 코드를 비춰 주세요")
        setBeepEnabled(false)
        setOrientationLocked(true)
    }

    private companion object {
        const val ACTION_USB_PERMISSION = "kr.co.hannaaircast.wifisetup.USB_PERMISSION"
    }
}
