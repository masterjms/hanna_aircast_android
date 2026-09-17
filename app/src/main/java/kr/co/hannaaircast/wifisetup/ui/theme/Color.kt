package kr.co.hannaaircast.wifisetup.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 색은 여기 한 곳에만 있다. 화면 코드에 16진수를 직접 쓰지 않는다.
 *
 * 바탕은 회색이고 그 위에 흰 카드가 뜬다 — 카드가 곧 「만질 수 있는 덩어리」다.
 * 파랑은 「지금 누를 것」 한 군데에만 쓴다. 녹·회·빨강은 상태 전용이다(앱 사양 §17).
 */
object Hanna {
    val Ground = Color(0xFFF2F4F6)
    val Surface = Color(0xFFFFFFFF)

    val Ink = Color(0xFF191F28)
    val InkSoft = Color(0xFF4E5968)
    val Sub = Color(0xFF8B95A1)
    val Line = Color(0xFFE5E8EB)
    val Fill = Color(0xFFF2F4F6)
    val FillPressed = Color(0xFFE5E8EB)

    val Blue = Color(0xFF3182F6)
    val BluePressed = Color(0xFF1B64DA)
    val BlueSoft = Color(0xFFE8F3FF)
    val BlueSoftPressed = Color(0xFFC9E2FF)
    val BlueDisabled = Color(0xFFB0D0FB)

    val Green = Color(0xFF03B26C)
    val GreenSoft = Color(0xFFE3F7EE)
    val Red = Color(0xFFF04452)
    val RedSoft = Color(0xFFFFECEE)
}
