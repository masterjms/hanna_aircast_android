package kr.co.hannaaircast.wifisetup.serial

import kr.co.hannaaircast.wifisetup.wifi.Security
import kr.co.hannaaircast.wifisetup.wifi.WifiCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvProtocolTest {
    private fun bytes(s: String) = s.toByteArray(Charsets.UTF_8)

    // ── 프레임 ────────────────────────────────────────────────────────
    @Test
    fun getFrame() {
        assertEquals("@GET\n@END\n", ProvProtocol.getFrame())
    }

    @Test
    fun rebootFrame() {
        assertEquals("@OFF\n@END\n", ProvProtocol.rebootFrame())
    }

    @Test
    fun wifiFrame_matchesAppSpecSection11() {
        assertEquals(
            "@SSID=HOME_WIFI\n@PASSWORD=12345678\n@END\n",
            ProvProtocol.wifiFrame(WifiCredential("HOME_WIFI", "12345678", Security.WPA)),
        )
    }

    @Test
    fun wifiFrame_openNetworkSendsEmptyPassword() {
        // SSID 만 보내면 단말이 거절한다. 개방망은 빈 PASSWORD 와 쌍으로 보낸다.
        assertEquals(
            "@SSID=FREE_WIFI\n@PASSWORD=\n@END\n",
            ProvProtocol.wifiFrame(WifiCredential("FREE_WIFI", "", Security.OPEN)),
        )
    }

    @Test
    fun frames_neverContainCarriageReturn_andEndIsOnItsOwnLine() {
        val frames = listOf(
            ProvProtocol.getFrame(),
            ProvProtocol.rebootFrame(),
            ProvProtocol.wifiFrame(WifiCredential("a", "pw@END", Security.WPA)),
        )
        for (f in frames) {
            assertFalse(f, f.contains('\r'))
            assertTrue(f, f.endsWith("\n@END\n"))
        }
    }

    @Test
    fun wifiFrame_refusesValuesThatWouldBreakTheFrame() {
        assertThrows(IllegalArgumentException::class.java) {
            ProvProtocol.wifiFrame(WifiCredential("home\n@SERVER=evil", "12345678", Security.WPA))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProvProtocol.wifiFrame(WifiCredential("", "12345678", Security.WPA))
        }
    }

    @Test
    fun wifiFrame_koreanSsidIsUtf8() {
        val frame = ProvProtocol.wifiFrame(WifiCredential("우리집", "12345678", Security.WPA))
        assertTrue(bytes(frame).size > frame.length) // 한글은 글자당 3바이트
    }

    // ── 응답 조립 ─────────────────────────────────────────────────────
    private val getResponse = """
        @RESULT=OK
        @MAC=58e6c5f2cc74
        @MODEL=IOT-1000
        @P4=V.260823-1
        @C6MODEL=IOT-1000C6
        @C6=V.260823-1
        @SSID=old_ap
        @PASSWORD=SET
        @SERVER=hanna-aircast.co.kr
        @MQTTID=58e6c5f2cc74
        @MQTTPW=SET
        @ZONE=1
        @PIR SENSOR=0
        @END
    """.trimIndent() + "\n"

    @Test
    fun getResponse_inOneChunk() {
        val r = ResponseAssembler().feed(bytes(getResponse))
        assertNotNull(r)
        r!!
        assertTrue(r.ok)
        assertNull(r.error)
        assertEquals("58e6c5f2cc74", r.mac)
        assertEquals("IOT-1000", r.model)
        assertEquals("V.260823-1", r.firmware)
        assertEquals("old_ap", r.ssid)
        assertEquals("SET", r.fields["PASSWORD"])
        assertEquals("0", r.fields["PIR SENSOR"]) // 키에 공백이 있는 항목
    }

    @Test
    fun response_splitAtEveryByteBoundary() {
        // USB 는 아무 데서나 잘라 준다. 어디서 잘려도 같은 답이어야 한다.
        val all = bytes(getResponse)
        for (cut in 1 until all.size) {
            val a = ResponseAssembler()
            assertNull("cut=$cut", a.feed(all.copyOfRange(0, cut)))
            val r = a.feed(all.copyOfRange(cut, all.size))
            assertEquals("cut=$cut", "58e6c5f2cc74", r?.mac)
        }
    }

    @Test
    fun koreanSsid_splitInsideOneCharacter() {
        val all = bytes("@RESULT=OK\n@SSID=우리집\n@PASSWORD=SET\n@END\n")
        val cut = bytes("@RESULT=OK\n@SSID=").size + 1 // '우' 의 3바이트 중 1바이트 뒤
        val a = ResponseAssembler()
        assertNull(a.feed(all.copyOfRange(0, cut)))
        assertEquals("우리집", a.feed(all.copyOfRange(cut, all.size))?.ssid)
    }

    @Test
    fun feedHonoursLengthArgument() {
        // 읽기 버퍼는 재사용된다 — 이번에 받은 길이까지만 봐야 한다.
        val buffer = ByteArray(64)
        val data = bytes("@RESULT=OK\n@END\n")
        data.copyInto(buffer)
        buffer[data.size] = '@'.code.toByte() // 지난번 읽기의 찌꺼기
        val a = ResponseAssembler()
        assertNotNull(a.feed(buffer, data.size))
    }

    @Test
    fun writeResponse_ok() {
        val r = ResponseAssembler().feed(bytes("@RESULT=OK\n@SSID=village_ap\n@PASSWORD=SET\n@END\n"))
        assertTrue(r!!.ok)
        assertEquals("village_ap", r.ssid)
    }

    @Test
    fun failResponse_carriesReason() {
        val r = ResponseAssembler().feed(bytes("@RESULT=FAIL\n@ERROR=SSID_EMPTY\n@END\n"))
        assertFalse(r!!.ok)
        assertEquals("SSID_EMPTY", r.error)
    }

    @Test
    fun bootLogBeforeResultIsDiscarded() {
        val noise = "I (312) boot: ESP-IDF v5.4\n<<XWIFI-PROV v1 READY>>\n@END\nE (99) x: @SSID=fake\n"
        val r = ResponseAssembler().feed(bytes(noise + "@RESULT=OK\n@SSID=real\n@END\n"))
        assertEquals("real", r?.ssid)
    }

    @Test
    fun resultGluedToTheEndOfALogLine() {
        val r = ResponseAssembler().feed(bytes("I (5) wifi: init@RESULT=OK\n@SSID=a\n@END\n"))
        assertTrue(r!!.ok)
    }

    @Test
    fun crlfFromDeviceIsTolerated() {
        val r = ResponseAssembler().feed(bytes("@RESULT=OK\r\n@SSID=a\r\n@END\r\n"))
        assertTrue(r!!.ok)
        assertEquals("a", r.ssid)
    }

    @Test
    fun withoutEnd_neverCompletes() {
        // 타임아웃은 호출하는 쪽이 건다. 조립기는 @END 없이는 끝났다고 하지 않는다.
        assertNull(ResponseAssembler().feed(bytes("@RESULT=OK\n@SSID=a\n")))
    }

    @Test
    fun twoResponsesBackToBack_doNotMix() {
        val a = ResponseAssembler()
        val first = a.feed(bytes("@RESULT=FAIL\n@ERROR=PAIR\n@END\n"))
        val second = a.feed(bytes("@RESULT=OK\n@SSID=b\n@END\n"))
        assertEquals("PAIR", first?.error)
        assertTrue(second!!.ok)
        assertNull(second.error)
    }

    @Test
    fun binaryGarbageWithoutNewlineDoesNotGrowForever() {
        val a = ResponseAssembler()
        a.feed(ByteArray(100_000) { 0x55 })
        val r = a.feed(bytes("\n@RESULT=OK\n@END\n"))
        assertTrue(r!!.ok)
    }
}
