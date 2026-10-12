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
}
