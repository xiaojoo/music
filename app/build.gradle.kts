import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/* release 签名从 local.properties 读（那三行和 backup/ 都在 .gitignore 里，绝不入库）。
   缺任何一行就直接失败并说清缺哪行 —— 静默出一个未签名的 release 包更难查。 */
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (!f.exists()) {
        error("local.properties 不存在：release 签名需要 keystore.file / keystore.alias / keystore.password 三行")
    }
    f.inputStream().use { load(it) }
}
fun localProp(key: String): String =
    localProps.getProperty(key) ?: error("local.properties 缺 $key（release 签名要用，别提交进仓库）")

android {
    namespace = "com.xiaojoo.lightplayer"
    compileSdk = 37

    signingConfigs {
        create("release") {
            storeFile = file(localProp("keystore.file"))
            storePassword = localProp("keystore.password")
            keyAlias = localProp("keystore.alias")
            keyPassword = localProp("keystore.password")
        }
    }

    defaultConfig {
        applicationId = "com.xiaojoo.lightplayer"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)

    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(project(":ffmpeg-decoder"))
    implementation(project(":video-source"))
    implementation(project(":recognizer"))

    // 磁力那一路的引擎。Java 那一半 + 编好的 arm64 原生库（POM 上零传递依赖，量过）
    implementation(libs.libtorrent4j.core)
    implementation(libs.libtorrent4j.android.arm64)

    testImplementation("junit:junit:4.13.2")
    // android.jar 里的 org.json 是抛异常的 stub，测试类路径上放一份真实现才能验编解码
    testImplementation("org.json:json:20260814")
}
