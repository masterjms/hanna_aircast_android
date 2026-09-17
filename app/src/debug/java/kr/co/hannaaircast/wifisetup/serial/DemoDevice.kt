package kr.co.hannaaircast.wifisetup.serial

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 가상 단말 — **디버그 빌드에만 있다.** 에뮬레이터나 보드 없는 폰에서 화면과 흐름을
 * 눌러 보려고 둔다. 배포본(release)에는 이 파일 대신 null 을 돌려주는 짝이 들어간다
 * (`src/release/.../DemoDevice.kt`).
 *
 * 실패 화면을 보려면 SSID 를 이렇게 넣는다:
 *   `fail`     → 단말이 거절 (@RESULT=FAIL)
 *   `timeout`  → 응답 없음
 */
fun demoTransportOrNull(): SerialTransport? = DemoTransport()

private class DemoTransport : SerialTransport {
    private val incoming = LinkedBlockingQueue<ByteArray>()
    private var ssid = "old_ap"

    override fun write(data: ByteArray) {
        val frame = data.toString(Charsets.UTF_8)
        val reply = when {
            frame.startsWith("@GET") -> getResponse()
            frame.startsWith("@SSID=") -> writeResponse(frame.lineSequence().first().removePrefix("@SSID="))
            else -> null // @OFF — 재부팅하는 단말은 답하지 않는다
        } ?: return
        // 실물처럼 조금 늦게, 두 조각으로 나눠서 보낸다.
        Thread {
            Thread.sleep(250)
            val bytes = reply.toByteArray(Charsets.UTF_8)
            val cut = bytes.size / 2
            incoming.put(bytes.copyOfRange(0, cut))
            Thread.sleep(40)
            incoming.put(bytes.copyOfRange(cut, bytes.size))
        }.start()
    }

    private fun getResponse() =
        "@RESULT=OK\n@MAC=58e6c5f2cc74\n@MODEL=IOT-1000\n@P4=V.DEMO\n@SSID=$ssid\n@PASSWORD=SET\n@END\n"

    private fun writeResponse(newSsid: String): String? = when (newSsid) {
        "timeout" -> null
        "fail" -> "@RESULT=FAIL\n@ERROR=demo: rejected on purpose\n@END\n"
        else -> {
            ssid = newSsid
            "@RESULT=OK\n@SSID=$newSsid\n@PASSWORD=SET\n@END\n"
        }
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val chunk = incoming.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return 0
        chunk.copyInto(buffer)
        return chunk.size
    }

    override fun purge() = incoming.clear()

    override fun close() = incoming.clear()
}
