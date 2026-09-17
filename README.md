# xWIFI Wi-Fi 설정 앱 (Android)

현장 설치 기사가 **공유기의 Wi-Fi QR 을 폰으로 찍어 단말에 넣는** 사내 전용 앱이다.
폰 자체는 그 Wi-Fi 에 접속하지 않는다. 서버와도 통신하지 않는다(`INTERNET` 권한이 없다).

```text
폰 ── USB-C OTG ── USB-to-serial 보드 ── 단말 디버그 헤더 (TX/RX/GND, 115200 8N1)
```

- 기준 문서: `Android_WiFi_QR_Setting_App_1st_Spec_v1.1.md` (앱 사양서),
  서버 저장소의 `docs/spec/PRODUCTION_PROVISIONING_SPEC.md` §4·§5 (단말 시리얼 프로토콜)
- 같은 프로토콜을 쓰는 짝: 서버 저장소의 웹 등록 화면(`frontend/src/lib/serial.ts`).
  생산 라인은 웹(MAC·서버·MQTT 계정), 현장은 이 앱(고객 공유기 Wi-Fi 만)

화면은 [docs/shots](docs/shots) 에 있다.

---

## 현장에서 쓰는 순서

1. 단말을 **KEY1 + KEY4** 를 누른 채 켜서 설정 모드로 들어간다 (화면에 바코드·QR 이 뜬다)
2. 보드를 단말 디버그 헤더에 물리고, OTG 젠더로 폰에 꽂는다
3. 「이 앱으로 열까요?」가 뜨면 **확인** (또는 앱을 직접 실행 → USB 접근 **허용**)
4. 위쪽에 `USB 연결 정상` · `단말 응답 정상` 이 녹색이면 준비된 것이다
5. **QR 촬영**(공유기 스티커) 또는 **직접 입력**
6. 화면의 이름·비밀번호가 공유기와 **글자 하나까지** 같은지 확인 → **정보 전송**
7. 「단말에 저장했어요」가 뜨면 **재부팅**. 단말이 새 Wi-Fi 로 붙는다
8. 다음 단말을 물리고 **단말 다시 확인** → 5번부터 반복. 앱을 껐다 켤 필요 없다

| 화면에 뜨는 것 | 원인과 조치 |
|---|---|
| USB Serial 장치가 연결되어 있지 않아요 | 보드를 먼저 꽂고 앱을 다시 실행한다. 충전 전용 젠더는 안 된다 |
| 단말이 답하지 않아요 | 단말이 설정 모드가 아니다(10분 방치하면 일반 부팅으로 돌아간다). TX·RX 가 서로 바뀌었을 수도 있다 |
| 단말이 받지 않았어요 | 아래에 단말이 준 이유가 영어로 나온다. 값을 고쳐 재전송한다 |
| 이 공유기는 WEP 보안을 써요 | 단말은 WEP 에 붙지 않는다. 공유기 설정을 WPA2 로 바꾼다 |
| 지원하지 않는 QR 코드예요 | Wi-Fi QR 이 아니다. 직접 입력을 쓴다 |

비밀번호의 **숫자는 파란색**으로 나온다(0 과 O, 1 과 l 구별). 앞뒤에 붙은 공백은 빨간 `␣` 로 보인다.

---

## 폰에 설치하기

Play 스토어에 없다. APK 파일을 받아서 직접 설치한다.

1. 받은 `app-release.apk` 를 폰에서 연다
2. 「출처를 알 수 없는 앱」 경고가 뜨면 **설정 → 이 출처 허용**
3. 설치. 업데이트도 같은 방법으로 새 APK 를 덮어 설치한다

Android 8.0 이상, USB OTG(호스트)를 지원하는 폰이어야 한다. 삼성 갤럭시는 전부 된다.

---

## 개발

Android Studio 로 이 폴더를 연다. JDK 는 Android Studio 에 들어 있는 것을 쓴다.

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew testDebugUnitTest          # 단위 테스트 (폰 불필요)
.\gradlew assembleDebug              # 디버그 APK
.\gradlew testDebugUnitTest -Pshots  # 화면 7종을 app/build/shots 에 PNG 로 그린다
```

### 구조

```text
app/src/main/java/kr/co/hannaaircast/wifisetup/
  MainActivity.kt              USB 1회 검색 → 권한 → 포트 열기, QR 스캐너 연결
  wifi/WifiQr.kt               WIFI: 문자열 파서                        ┐ 안드로이드 API 를
  serial/ProvProtocol.kt       프레임 생성, 조각난 응답을 @END 까지 조립  │ 쓰지 않는다 —
  serial/ProvClient.kt         명령 하나 → 응답 하나, 3초 타임아웃        ┘ PC 에서 테스트한다
  serial/UsbSerialTransport.kt USB 보드 (115200 8N1)
  ui/SetupViewModel.kt         상태와 버튼 규칙
  ui/SetupScreen.kt            화면 하나. 상태를 받아 그리기만 한다
  ui/components/               누름 효과·버튼, 폰-단말 연결선, 다이얼로그
app/src/debug/   가상 단말 — 디버그 빌드에만 있다
app/src/release/ 그 자리에 들어가는 빈 짝
```

### 지켜야 하는 것

- **줄 끝은 LF 만, `@END` 는 자기 줄에.** `@PASSWORD=<값>@END` 로 붙여 보내면 `@END` 까지
  비밀번호에 들어간다. 서버 쪽에서 실제로 겪은 사고다
- **`@SSID` 와 `@PASSWORD` 는 반드시 쌍.** 개방망은 `@PASSWORD=` 를 빈 값으로 보낸다
- **제어 문자가 든 값은 보내지 않는다.** 프레임이 줄 단위라, SSID 에 개행을 심은 QR 은
  `@SERVER=` 같은 줄을 끼워 넣어 단말의 서버 주소를 바꿀 수 있다. 파서와 프레임 생성
  양쪽에서 막고 테스트로 고정했다
- **`INTERNET` 권한을 넣지 않는다**
- 명령은 한 번에 하나. 보내기 전에 남아 있던 입력을 버린다(늦게 온 지난번 응답이
  이번 답으로 읽히지 않게)

### 보드 없이 눌러 보기 (디버그 빌드)

보드가 없으면 뜨는 팝업에 **[가상 단말]** 버튼이 있다. 직접 입력에서 SSID 를
`fail` 로 넣으면 단말 거절, `timeout` 으로 넣으면 응답 없음 화면이 나온다.
배포본(release)에는 이 버튼도 코드도 없다.

### 사양서보다 관대하게 받는 QR

현장에서 「왜 안 찍히지」를 줄이려고 넣었다. 테스트에 하나씩 고정돼 있다.

- `T:SAE` · `WPA2` · `WPA3` 를 WPA 로 받는다 (안드로이드 「Wi-Fi 공유」가 WPA3 망에 `SAE` 를 찍는다)
- 끝이 `;;` 가 아니라 `;` 하나인 QR
- `T` 가 없는 QR — 비밀번호 유무로 판단한다
- 따옴표로 감싼 SSID (`"1234ABCD"`)

---

## 배포

### 서명 키 (처음 한 번)

Android Studio → **Build → Generate Signed App Bundle or APK → APK → Create new...**
로 `.jks` 파일을 만든다. 그다음 `keystore.properties.example` 을 `keystore.properties` 로
복사해 값을 채운다.

> **키 파일과 비밀번호는 저장소에 넣지 않는다**(`.gitignore` 에 있다). 그리고 **반드시
> 다른 곳에 백업한다.** 키를 잃으면 같은 앱으로 업데이트를 낼 수 없다 — 기사들 폰에서
> 앱을 지우고 다시 깔아야 한다. 키가 새면 남이 「우리 앱의 업데이트」를 만들 수 있다.

### 새 버전 내기

1. `app/build.gradle.kts` 에서 `versionCode` 를 1 올리고 `versionName` 을 바꾼다
   (`versionCode` 가 올라가지 않으면 폰이 업데이트로 받아 주지 않는다)
2. `.\gradlew testDebugUnitTest` 통과 확인
3. `.\gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
   (`app-release-unsigned.apk` 가 나오면 `keystore.properties` 가 없는 것이다 — 설치되지 않는다)
4. 폰 하나에 덮어 설치해서 전체 흐름을 한 번 돌린다. 화면 맨 아래의 버전을 확인한다
5. 파일을 사내로 공유한다
