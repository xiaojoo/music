plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.xiaojoo.lightplayer.videosource"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    testImplementation("junit:junit:4.13.2")
    // android.jar 里的 org.json 是抛异常的桩，测试类路径上要一份真实现（和 app 那边同一个理由）
    testImplementation("org.json:json:20260814")
}
