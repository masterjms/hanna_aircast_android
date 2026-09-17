package kr.co.hannaaircast.wifisetup.serial

import java.io.IOException

/**
 * 테스트용 가짜 선. 쓰기가 들어오면 [reply] 가 돌려준 조각들을 읽기 큐에 넣는다.
 * 시계는 읽을 때마다 기다린 만큼 앞으로 간다 — 테스트가 실제로 3초를 기다리지 않는다.
 */
class FakeTransport(
    stale: List<String> = emptyList(),
    private val reply: (String) -> List<String> = { emptyList() },
) : SerialTransport {
    val written = ArrayList<String>()
    var purged = 0
    var closed = false
    var failWrites = false
    var nowMs = 0L

    private val incoming = ArrayDeque(stale.map { it.toByteArray(Charsets.UTF_8) })

    override fun write(data: ByteArray) {
        if (failWrites) throw IOException("케이블이 빠졌다")
        val text = data.toString(Charsets.UTF_8)
        written += text
        reply(text).forEach { incoming.addLast(it.toByteArray(Charsets.UTF_8)) }
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val chunk = incoming.removeFirstOrNull()
        if (chunk == null) {
            nowMs += timeoutMs
            return 0
        }
        chunk.copyInto(buffer)
        return chunk.size
    }

    override fun purge() {
        purged++
    }

    override fun close() {
        closed = true
    }
}
