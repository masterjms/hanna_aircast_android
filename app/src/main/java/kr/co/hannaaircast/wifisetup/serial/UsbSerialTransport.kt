package kr.co.hannaaircast.wifisetup.serial

import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

/** USB-to-serial 보드. 115200, N, 8, 1 고정(앱 사양 §5). */
class UsbSerialTransport private constructor(private val port: UsbSerialPort) : SerialTransport {

    override fun write(data: ByteArray) = port.write(data, WRITE_TIMEOUT_MS)

    override fun read(buffer: ByteArray, timeoutMs: Int): Int =
        // 라이브러리에서 0 은 「무한 대기」다. 절대 0 을 넘기지 않는다.
        port.read(buffer, timeoutMs.coerceAtLeast(1))

    override fun purge() {
        try {
            port.purgeHwBuffers(true, true)
        } catch (_: UnsupportedOperationException) {
            // 칩에 따라 없다. ProvClient 가 읽어서 버리는 것으로 충분하다.
        } catch (_: IOException) {
        }
    }

    override fun close() {
        try {
            port.close()
        } catch (_: IOException) {
        }
    }

    companion object {
        private const val WRITE_TIMEOUT_MS = 1000

        /** 꽂혀 있는 USB serial 장치. 앱 시작 시 1회만 찾는다(앱 사양 §3.2). */
        fun findDriver(manager: UsbManager): UsbSerialDriver? =
            UsbSerialProber.getDefaultProber().findAllDrivers(manager).firstOrNull()

        /** 권한을 받은 뒤에 부른다. */
        @Throws(IOException::class)
        fun open(manager: UsbManager, driver: UsbSerialDriver): UsbSerialTransport {
            val connection = manager.openDevice(driver.device)
                ?: throw IOException("USB 장치를 열 수 없습니다")
            val port = driver.ports.first()
            try {
                port.open(connection)
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
                // 단말 디버그 헤더에는 DTR/RTS 가 나오지 않는다(단말 생산 사양 §4.1).
                // 건드리지 않는다 — 포트를 연다고 단말이 리셋되지 않는다.
            } catch (e: IOException) {
                try {
                    port.close()
                } catch (_: IOException) {
                }
                throw e
            }
            return UsbSerialTransport(port)
        }
    }
}
