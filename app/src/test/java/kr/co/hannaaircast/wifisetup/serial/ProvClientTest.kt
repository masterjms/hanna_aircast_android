package kr.co.hannaaircast.wifisetup.serial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvClientTest {
    private fun client(t: FakeTransport) = ProvClient(t, clock = { t.nowMs })

    @Test
    fun exchange_returnsResponse() {
        val t = FakeTransport { listOf("@RESULT=OK\n@MAC=58e6", "c5f2cc74\n@END\n") }
        val result = client(t).exchange(ProvProtocol.getFrame())
        assertEquals("58e6c5f2cc74", (result as ExchangeResult.Ok).response.mac)
        assertEquals(listOf("@GET\n@END\n"), t.written)
    }

    @Test
    fun staleInputIsDiscardedBeforeSending() {
        // 지난번 타임아웃 뒤에 늦게 온 OK 가 이번 전송의 답으로 읽히면 안 된다.
        val t = FakeTransport(stale = listOf("@RESULT=OK\n@SSID=old\n@END\n")) {
            listOf("@RESULT=FAIL\n@ERROR=SSID empty\n@END\n")
        }
        val result = client(t).exchange("@SSID=\n@PASSWORD=x\n@END\n") as ExchangeResult.Ok
        assertEquals("SSID empty", result.response.error)
        assertTrue(t.purged > 0)
    }

    @Test
    fun noReply_timesOutAfterThreeSeconds() {
        val t = FakeTransport()
        assertEquals(ExchangeResult.Timeout, client(t).exchange(ProvProtocol.getFrame()))
        assertTrue("기다린 시간 ${t.nowMs}ms", t.nowMs in 3000..3500)
    }

    @Test
    fun replyWithoutEnd_timesOut() {
        val t = FakeTransport { listOf("@RESULT=OK\n@SSID=a\n") }
        assertEquals(ExchangeResult.Timeout, client(t).exchange(ProvProtocol.getFrame()))
    }

    @Test
    fun unpluggedCable_isIoError_notCrash() {
        val t = FakeTransport().apply { failWrites = true }
        assertTrue(client(t).exchange(ProvProtocol.getFrame()) is ExchangeResult.IoError)
        assertNotNull(client(t).sendOnly(ProvProtocol.rebootFrame()))
    }

    @Test
    fun reboot_doesNotWaitForReply() {
        val t = FakeTransport()
        assertNull(client(t).sendOnly(ProvProtocol.rebootFrame()))
        assertEquals(listOf("@OFF\n@END\n"), t.written)
        assertEquals(0L, t.nowMs)
    }

    @Test
    fun close_closesTransport() {
        val t = FakeTransport()
        client(t).close()
        assertTrue(t.closed)
    }
}
