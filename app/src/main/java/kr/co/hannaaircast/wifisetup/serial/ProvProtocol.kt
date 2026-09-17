package kr.co.hannaaircast.wifisetup.serial

import kr.co.hannaaircast.wifisetup.wifi.WifiCredential
import kr.co.hannaaircast.wifisetup.wifi.isSendable

/**
 * 단말 생산/현장 모드의 시리얼 프로토콜 (단말 생산 사양 §4·§5).
 *
 * 서버 리포의 `frontend/src/lib/serial.ts` 와 같은 형식이다 — 프레임을 만드는 곳을
 * 한 파일에 모아 둔 이유도 같다. 이 파일은 안드로이드 API 를 쓰지 않는다.
 *
 * **모든 줄은 LF(0x0A) 로 끝난다. `@END` 앞에도 개행이 있어야 한다.** 단말 파서는
 * 줄을 먼저 자르고 그다음 `@KEY=VALUE` 를 읽는다. `@PASSWORD=<값>@END` 처럼 붙여
 * 보내면 `@END` 까지 비밀번호에 들어간다(서버 쪽에서 실제로 겪은 사고). CRLF 로
 * 보내면 `\r` 이 값에 붙는다.
 */
object ProvProtocol {
    private const val LF = "\n"

    /** 현재 값을 읽는다 (§4.5). 앱 시작 시 단말 통신 확인에 쓴다. */
    fun getFrame(): String = frame("@GET")

    /**
     * Wi-Fi 한 쌍을 쓴다. **SSID 와 PASSWORD 는 반드시 같이 보낸다** — 하나만 보내면
     * 단말이 거절한다. 개방망은 `@PASSWORD=` 를 빈 값으로 보낸다.
     */
    fun wifiFrame(credential: WifiCredential): String {
        require(isSendable(credential)) { "시리얼로 보낼 수 없는 값" }
        return frame("@SSID=${credential.ssid}", "@PASSWORD=${credential.password}")
    }

    /** 저장한 값을 적용하려고 재부팅한다 (§4.7). 완료 여부는 확인하지 않는다. */
    fun rebootFrame(): String = frame("@OFF")

    private fun frame(vararg lines: String): String =
        (lines.asList() + "@END").joinToString(separator = "") { it + LF }
}

/** `@RESULT=` 부터 `@END` 까지의 응답 하나. */
data class ProvResponse(
    val ok: Boolean,
    /** `@RESULT=FAIL` 일 때 단말이 알려 준 이유(`@ERROR=`). */
    val error: String?,
    /** `@` 를 뗀 키 → 값. `@PASSWORD`·`@MQTTPW` 는 값이 아니라 `SET`/`NONE` 이다. */
    val fields: Map<String, String>,
) {
    val mac: String? get() = fields["MAC"]
    val model: String? get() = fields["MODEL"]
    val firmware: String? get() = fields["P4"]
    val ssid: String? get() = fields["SSID"]
}

/**
 * 조각나서 오는 바이트를 응답 하나로 조립한다.
 *
 * - USB 는 응답을 아무 데서나 잘라 준다. 한글 SSID 의 한 글자가 두 조각에 걸칠 수
 *   있어서 **줄이 완성된 뒤에** UTF-8 로 푼다.
 * - `@RESULT=` 앞에 온 것은 전부 버린다. 앱을 켜기 전에 단말이 쏜 부트 로그가
 *   버퍼에 남아 있을 수 있다.
 * - `@END` 를 봐야 끝이다. 거기까지 읽어야 다음 명령의 응답과 섞이지 않는다.
 */
class ResponseAssembler {
    private val pending = java.io.ByteArrayOutputStream()
    private var started = false
    private var ok = false
    private var error: String? = null
    private val fields = LinkedHashMap<String, String>()

    /** 받은 조각을 넣는다. 응답이 완성되면 돌려주고, 아직이면 null. */
    fun feed(chunk: ByteArray, length: Int = chunk.size): ProvResponse? {
        for (i in 0 until length) {
            val b = chunk[i]
            if (b != LF_BYTE) {
                // 개행 없는 쓰레기(바이너리 로그)가 끝없이 쌓이지 않게 한다.
                if (pending.size() < MAX_LINE_BYTES) pending.write(b.toInt())
                continue
            }
            val line = pending.toByteArray().toString(Charsets.UTF_8).trimEnd('\r')
            pending.reset()
            onLine(line)?.let { return it }
        }
        return null
    }

    private fun onLine(line: String): ProvResponse? {
        if (!started) {
            // 로그 줄 끝에 붙어 올 수도 있어서 startsWith 가 아니라 위치로 찾는다.
            val at = line.indexOf(RESULT)
            if (at < 0) return null
            started = true
            ok = line.substring(at + RESULT.length).trim() == "OK"
            return null
        }
        if (line.trim() == "@END") {
            val done = ProvResponse(ok, error, fields.toMap())
            reset()
            return done
        }
        val eq = line.indexOf('=')
        if (line.startsWith("@") && eq > 1) {
            val key = line.substring(1, eq).trim()
            val value = line.substring(eq + 1)
            if (key == "ERROR") error = value.trim() else fields[key] = value
        }
        return null
    }

    fun reset() {
        pending.reset()
        started = false
        ok = false
        error = null
        fields.clear()
    }

    private companion object {
        const val RESULT = "@RESULT="
        const val LF_BYTE = '\n'.code.toByte()
        const val MAX_LINE_BYTES = 1024
    }
}
