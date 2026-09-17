package kr.co.hannaaircast.wifisetup.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * 밝은 테마 하나뿐이다. 현장(실외, 공유기 앞)에서 쓰는 도구라 폰이 다크 모드여도
 * 화면은 밝게 유지한다 — 햇빛 아래에서 비밀번호를 읽어야 한다.
 */
private val Colors = lightColorScheme(
    primary = Hanna.Blue,
    onPrimary = Hanna.Surface,
    background = Hanna.Ground,
    onBackground = Hanna.Ink,
    surface = Hanna.Surface,
    onSurface = Hanna.Ink,
    onSurfaceVariant = Hanna.Sub,
    outline = Hanna.Line,
    error = Hanna.Red,
)

@Composable
fun HannaaircastTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography, content = content)
}
