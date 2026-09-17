package kr.co.hannaaircast.wifisetup.wifi

/**
 * Wi-Fi QR 문자열 파서 (앱 사양서 §7).
 *
 * ```text
 * WIFI:S:<SSID>;T:<WPA|WEP|nopass>;P:<비밀번호>;H:<true|false>;;
 * ```
 *
 * 라이브러리를 쓰지 않고 직접 둔 이유: 우리 규칙(필드 순서 무관, `\` 이스케이프 해제,
 * WEP 차단, 그 외 거절)을 테스트로 고정해야 하기 때문이다. 이 파일은 안드로이드
 * API 를 쓰지 않는다 — PC 에서 `gradlew test` 로 돈다.
 */

/** 단말에 보낼 한 쌍. 개방망이면 [password] 는 빈 문자열이다. */
data class WifiCredential(
    val ssid: String,
    val password: String,
    val security: Security,
)

enum class Security {
    /** WPA · WPA2 · WPA3 공용. */
    WPA,

    /** 개방망(`nopass`). */
    OPEN,
}

sealed interface WifiQrResult {
    data class Ok(val credential: WifiCredential) : WifiQrResult

    /** QR 은 정상이지만 단말이 붙을 수 없는 보안 방식이다 (§7.4). 전송하지 않는다. */
    data object WepBlocked : WifiQrResult

    /** Wi-Fi QR 이 아니거나 필요한 값이 없다 (§7.3). 전송하지 않는다. */
    data object Unsupported : WifiQrResult
}

private const val PREFIX = "WIFI:"

/** 단말의 Wi-Fi 설정 버퍼 크기(ESP32 `wifi_config_t`). 넘으면 잘려서 조용히 실패한다. */
const val SSID_MAX_BYTES = 32
const val PASSWORD_MAX_BYTES = 64

fun parseWifiQr(raw: String): WifiQrResult {
    val text = raw.trim()
    if (!text.startsWith(PREFIX, ignoreCase = true)) return WifiQrResult.Unsupported

    // 필드 순서는 공유기 제조사마다 다르다 — 순서를 가정하지 않고 접두사로 찾는다.
    val fields = HashMap<String, String>()
    for (field in splitUnescaped(text.substring(PREFIX.length), ';')) {
        val colon = field.indexOf(':')
        if (colon <= 0) continue // `;;` 끝의 빈 조각, 또는 키가 없는 조각
        val key = field.substring(0, colon).uppercase()
        // 같은 키가 두 번 나오면 첫 번째를 쓴다.
        fields.putIfAbsent(key, unescape(field.substring(colon + 1)))
    }

    val ssid = fields["S"]?.trim().orEmpty()
    val password = fields["P"].orEmpty()
    if (ssid.isEmpty()) return WifiQrResult.Unsupported

    val security = when (fields["T"]?.trim()?.uppercase().orEmpty()) {
        // SAE 는 안드로이드의 「Wi-Fi 공유」가 WPA3 망에 찍어 주는 값이다.
        "WPA", "WPA2", "WPA3", "SAE" -> Security.WPA
        "NOPASS" -> Security.OPEN
        "WEP" -> return WifiQrResult.WepBlocked
        // T 가 아예 없는 QR 이 있다. 비밀번호 유무로 판단한다.
        "" -> if (password.isEmpty()) Security.OPEN else Security.WPA
        else -> return WifiQrResult.Unsupported
    }

    val credential = when (security) {
        Security.OPEN -> WifiCredential(ssid, "", security)
        Security.WPA -> {
            if (password.isEmpty()) return WifiQrResult.Unsupported
            WifiCredential(ssid, password, security)
        }
    }
    return if (isSendable(credential)) WifiQrResult.Ok(credential) else WifiQrResult.Unsupported
}

/**
 * 시리얼 프레임에 실어도 되는 값인가. QR 과 수동 입력이 같이 쓴다.
 *
 * **제어 문자(개행 포함)는 절대 통과시키지 않는다.** 프레임이 줄 단위라서, SSID 에
 * 개행을 심은 QR 은 `@SERVER=...` 같은 줄을 단말에 끼워 넣을 수 있다.
 */
fun isSendable(credential: WifiCredential): Boolean {
    val (ssid, password) = credential
    if (ssid.isEmpty() || ssid != ssid.trim()) return false
    if (ssid.any { it.isISOControl() } || password.any { it.isISOControl() }) return false
    if (ssid.toByteArray(Charsets.UTF_8).size > SSID_MAX_BYTES) return false
    if (password.toByteArray(Charsets.UTF_8).size > PASSWORD_MAX_BYTES) return false
    return true
}

/** `\` 로 이스케이프되지 않은 [delimiter] 에서만 자른다. 이스케이프는 그대로 남긴다. */
private fun splitUnescaped(text: String, delimiter: Char): List<String> {
    val parts = ArrayList<String>()
    val current = StringBuilder()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '\\' && i + 1 < text.length -> {
                current.append(c).append(text[i + 1])
                i += 2
                continue
            }
            c == delimiter -> {
                parts.add(current.toString())
                current.clear()
            }
            else -> current.append(c)
        }
        i++
    }
    parts.add(current.toString())
    return parts
}

/**
 * `\;` `\:` `\,` `\\` `\"` → 원래 글자.
 *
 * 값 전체를 감싼 큰따옴표는 벗긴다 — 16진수로 오해될 수 있는 SSID(`"1234ABCD"`)를
 * 생성기가 따옴표로 감싸는 규칙이 있다. 값 안의 진짜 따옴표는 `\"` 로 오므로 구별된다.
 */
private fun unescape(value: String): String {
    var v = value
    if (v.length >= 2 && v.first() == '"' && v.last() == '"' && !v.endsWith("\\\"")) {
        v = v.substring(1, v.length - 1)
    }
    val out = StringBuilder(v.length)
    var i = 0
    while (i < v.length) {
        val c = v[i]
        if (c == '\\' && i + 1 < v.length) {
            out.append(v[i + 1])
            i += 2
        } else {
            out.append(c)
            i++
        }
    }
    return out.toString()
}
