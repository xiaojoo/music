plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xiaojoo.lightplayer"
    compileSdk = 37

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
