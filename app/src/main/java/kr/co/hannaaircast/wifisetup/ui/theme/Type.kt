package kr.co.hannaaircast.wifisetup.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 글꼴은 폰의 기본 한글 글꼴을 그대로 쓴다(삼성은 One UI Sans). 성격은 굵기와
 * 자간에서 나온다 — 큰 글씨는 굵고 좁게, 작은 글씨는 가볍게.
 */
private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = -0.01) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val Typography = Typography(
    // 화면을 이끄는 한 문장
    headlineMedium = style(26, 36, FontWeight.Bold, -0.025),
    // 다이얼로그 제목
    titleLarge = style(21, 30, FontWeight.Bold, -0.02),
    // 카드 안의 제목, 타일 이름
    titleMedium = style(17, 24, FontWeight.SemiBold, -0.015),
    // 버튼
    labelLarge = style(17, 24, FontWeight.SemiBold, -0.01),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(15, 22, FontWeight.Normal),
    // 값 위의 작은 이름표
    labelMedium = style(13, 18, FontWeight.Medium, 0.0),
    labelSmall = style(12, 16, FontWeight.Normal, 0.0),
)

/** SSID·비밀번호. 0/O, 1/l/I 가 구별되는 고정폭으로 크게. */
val CredentialStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 24.sp,
    lineHeight = 32.sp,
)
