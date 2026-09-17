import java.util.Properties

// 서명 키 정보는 저장소 밖에 둔다(keystore.properties 는 .gitignore). 파일이 없으면
// release 는 서명 없이 빌드된다 — 그 APK 는 폰에 설치되지 않는다. README 「배포」 참고.
val keystore = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "kr.co.hannaaircast.wifisetup"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "kr.co.hannaaircast.wifisetup"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystore.isNotEmpty()) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // 코드 축소(R8)는 켜지 않는다. APK 가 11MB 라 줄일 이유가 없고, USB 드라이버를
            // 리플렉션으로 찾는 라이브러리라 축소가 조용히 깨뜨릴 수 있다.
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                val shots = providers.gradleProperty("shots").isPresent.toString()
                it.systemProperty("shots", shots)
                it.systemProperty("roborazzi.test.record", shots)
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.usb.serial)
    implementation(libs.zxing.embedded)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 화면을 PC 에서 PNG 로 그려 본다(폰 없이 디자인 확인). `gradlew testDebugUnitTest -Pshots`
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}