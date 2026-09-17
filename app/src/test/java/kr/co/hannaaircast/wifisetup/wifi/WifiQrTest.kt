package kr.co.hannaaircast.wifisetup.wifi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiQrTest {
    private fun ok(raw: String): WifiCredential {
        val result = parseWifiQr(raw)
        assertTrue("Ok 여야 한다: $raw → $result", result is WifiQrResult.Ok)
        return (result as WifiQrResult.Ok).credential
    }

    // ── 사양서 §7.2 의 예시 세 개 ─────────────────────────────────────
    @Test
    fun specExample_ssidFirst() {
        assertEquals(
            WifiCredential("SK_DCC0_2.4G", "BMF00@1399", Security.WPA),
            ok("WIFI:S:SK_DCC0_2.4G;T:WPA;P:BMF00@1399;;"),
        )
    }

    @Test
    fun specExample_typeFirst() {
        // 필드 순서는 고정이 아니다 — 공유기 제조사마다 다르다.
        assertEquals(
            WifiCredential("HOME_WIFI", "12345678", Security.WPA),
            ok("WIFI:T:WPA;S:HOME_WIFI;P:12345678;;"),
        )
    }

    @Test
    fun specExample_openNetwork() {
        assertEquals(
            WifiCredential("FREE_WIFI", "", Security.OPEN),
            ok("WIFI:S:FREE_WIFI;T:nopass;;"),
        )
    }

    // ── 순서·선택 필드 ────────────────────────────────────────────────
    @Test
    fun passwordBeforeSsid_andHiddenFlag() {
        assertEquals(
            WifiCredential("cafe", "abcdefgh", Security.WPA),
            ok("WIFI:P:abcdefgh;H:true;T:WPA;S:cafe;;"),
        )
    }

    @Test
    fun nopassIgnoresStrayPassword() {
        // 개방망이면 P 가 있어도 빈 값으로 보낸다(단말에는 `@PASSWORD=`).
        assertEquals("", ok("WIFI:S:open;T:nopass;P:leftover;;").password)
    }

    @Test
    fun missingType_decidedByPassword() {
        assertEquals(Security.WPA, ok("WIFI:S:a;P:12345678;;").security)
        assertEquals(Security.OPEN, ok("WIFI:S:a;;").security)
    }

    @Test
    fun wpa3FromAndroidShare() {
        // 안드로이드 「Wi-Fi 공유」는 WPA3 망에 T:SAE 를 찍는다.
        assertEquals(Security.WPA, ok("WIFI:S:a;T:SAE;P:12345678;;").security)
    }

    @Test
    fun singleTrailingSemicolonIsAccepted() {
        assertEquals("a", ok("WIFI:S:a;T:WPA;P:12345678;").ssid)
    }

    // ── 이스케이프 (§7.2) ─────────────────────────────────────────────
    @Test
    fun specExample_escapes() {
        assertEquals(
            WifiCredential("My;Home", "pa:ss", Security.WPA),
            ok("""WIFI:S:My\;Home;T:WPA;P:pa\:ss;;"""),
        )
    }

    @Test
    fun allEscapableCharacters() {
        val c = ok("""WIFI:S:a\\b\,c\"d;T:WPA;P:x\;y\:z\\;;""")
        assertEquals("""a\b,c"d""", c.ssid)
        assertEquals("""x;y:z\""", c.password)
    }

    @Test
    fun passwordWithPlainColonIsKept() {
        // 첫 번째 `:` 만 키와 값을 가른다.
        assertEquals("ab:cd:ef", ok("WIFI:S:a;T:WPA;P:ab:cd:ef;;").password)
    }

    @Test
    fun quotedHexLookingSsid() {
        assertEquals("1234ABCD", ok("""WIFI:S:"1234ABCD";T:WPA;P:12345678;;""").ssid)
    }

    @Test
    fun koreanSsid() {
        assertEquals("우리집 와이파이", ok("WIFI:S:우리집 와이파이;T:WPA;P:12345678;;").ssid)
    }

    @Test
    fun passwordSpacesAreKept_ssidSpacesAreTrimmed() {
        // 단말 파서와 같다: SSID 는 다듬고 PASSWORD 는 다듬지 않는다.
        val c = ok("WIFI:S: home ;T:WPA;P: pass word ;;")
        assertEquals("home", c.ssid)
        assertEquals(" pass word ", c.password)
    }

    // ── 거절 (§7.3, §7.4) ─────────────────────────────────────────────
    @Test
    fun wepIsBlocked() {
        assertEquals(WifiQrResult.WepBlocked, parseWifiQr("WIFI:S:old;T:WEP;P:abcde;;"))
        assertEquals(WifiQrResult.WepBlocked, parseWifiQr("WIFI:T:wep;S:old;P:abcde;;"))
    }

    @Test
    fun unsupportedInputs() {
        val cases = listOf(
            "https://example.com",
            "BEGIN:VCARD\nFN:홍길동\nEND:VCARD",
            "",
            "WIFI:",
            "WIFI:T:WPA;P:12345678;;", // SSID 없음
            "WIFI:S:;T:WPA;P:12345678;;", // SSID 빈 값
            "WIFI:S:a;T:WPA;;", // WPA 인데 비밀번호 없음
            "WIFI:S:a;T:WPA2-EAP;P:12345678;;", // 모르는 T
        )
        for (raw in cases) {
            assertEquals(raw, WifiQrResult.Unsupported, parseWifiQr(raw))
        }
    }

    @Test
    fun newlineInValueIsRejected() {
        // 프레임이 줄 단위라, 개행을 심은 QR 은 단말에 다른 명령을 끼워 넣을 수 있다.
        assertEquals(
            WifiQrResult.Unsupported,
            parseWifiQr("WIFI:S:home\n@SERVER=evil.example;T:WPA;P:12345678;;"),
        )
        assertEquals(
            WifiQrResult.Unsupported,
            parseWifiQr("WIFI:S:home;T:WPA;P:1234\r\n@OFF;;"),
        )
    }

    @Test
    fun tooLongForDeviceBuffers() {
        val ssid33 = "a".repeat(33)
        assertEquals(WifiQrResult.Unsupported, parseWifiQr("WIFI:S:$ssid33;T:WPA;P:12345678;;"))
        // 한글은 글자당 3바이트 — 11자면 33바이트다.
        assertFalse(isSendable(WifiCredential("가".repeat(11), "12345678", Security.WPA)))
        assertTrue(isSendable(WifiCredential("가".repeat(10), "12345678", Security.WPA)))
        assertFalse(isSendable(WifiCredential("a", "p".repeat(65), Security.WPA)))
    }

    @Test
    fun manualInputMustBeTrimmedBeforeSending() {
        assertFalse(isSendable(WifiCredential(" home", "12345678", Security.WPA)))
        assertFalse(isSendable(WifiCredential("", "12345678", Security.WPA)))
    }
}
