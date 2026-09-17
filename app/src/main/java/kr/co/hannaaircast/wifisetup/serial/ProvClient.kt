package kr.co.hannaaircast.wifisetup.serial

import java.io.Closeable
import java.io.IOException

/**
 * 바이트가 오가는 선. USB 구현은 [UsbSerialTransport] 에 있고, 테스트와 화면
 * 미리보기는 가짜를 끼운다 — 그래서 이 파일은 안드로이드 API 를 모른다.
 */
interface SerialTransport : Closeable {
    @Throws(IOException::class)
    fun write(data: ByteArray)

    /** 최대 [timeoutMs] 기다려 읽는다. 받은 바이트 수, 시간 안에 없으면 0. */
    @Throws(IOException::class)
    fun read(buffer: ByteArray, timeoutMs: Int): Int

    /** 드라이버·칩에 쌓인 것을 버린다. 지원하지 않으면 아무 일도 하지 않는다. */
    fun purge()
}

sealed interface ExchangeResult {
    data class Ok(val response: ProvResponse) : ExchangeResult

    /** 시간 안에 `@END` 까지 오지 않았다. */
    data object Timeout : ExchangeResult

    /** 케이블이 빠졌거나 포트가 죽었다. */
    data class IoError(val message: String) : ExchangeResult
}

/**
 * 명령 하나를 보내고 응답 하나를 받는다. **블로킹이다** — IO 스레드에서 부른다.
 * 한 번에 하나씩만 불러야 한다(호출하는 쪽이 직렬화한다).
 */
class ProvClient(
    private val transport: SerialTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) : Closeable {

    fun exchange(frame: String, timeoutMs: Long = RESPONSE_TIMEOUT_MS): ExchangeResult {
        val assembler = ResponseAssembler()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        return try {
            discardStaleInput(buffer)
            transport.write(frame.toByteArray(Charsets.UTF_8))
            val deadline = clock() + timeoutMs
            while (clock() < deadline) {
                val n = transport.read(buffer, READ_SLICE_MS)
                if (n <= 0) continue
                assembler.feed(buffer, n)?.let { return ExchangeResult.Ok(it) }
            }
            ExchangeResult.Timeout
        } catch (e: IOException) {
            ExchangeResult.IoError(e.message ?: e.javaClass.simpleName)
        }
    }

    /** 보내기만 한다. `@OFF` 는 단말이 곧 꺼지므로 응답을 기다리지 않는다(앱 사양 §14). */
    fun sendOnly(frame: String): ExchangeResult.IoError? = try {
        transport.write(frame.toByteArray(Charsets.UTF_8))
        null
    } catch (e: IOException) {
        ExchangeResult.IoError(e.message ?: e.javaClass.simpleName)
    }

    /**
     * 보내기 전에 남아 있던 입력을 버린다. 앱을 켜기 전에 단말이 쏜 부트 로그나,
     * 지난번 타임아웃 뒤에 늦게 도착한 응답이 이번 응답으로 읽히면 안 된다.
     */
    private fun discardStaleInput(buffer: ByteArray) {
        transport.purge()
        repeat(MAX_DRAIN_READS) {
            if (transport.read(buffer, DRAIN_SLICE_MS) <= 0) return
        }
    }

    override fun close() = transport.close()

    companion object {
        /** 서버 리포의 웹 등록 화면과 같은 값. */
        const val RESPONSE_TIMEOUT_MS = 3000L
        private const val READ_SLICE_MS = 200
        private const val DRAIN_SLICE_MS = 30
        private const val MAX_DRAIN_READS = 64
        private const val READ_BUFFER_BYTES = 4096
    }
}
