plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "androidx.media3.decoder.ffmpeg"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
        externalNativeBuild {
            cmake {
                arguments("-DFFMPEG_ROOT=${rootDir}/third_party/ffmpeg")
                abiFilters += "arm64-v8a"
            }
        }
        consumerProguardFiles("consumer-rules.pro")
    }

    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // media3 把 androidx.annotation 声明成 runtime scope，不会传到 javac 的编译类路径
    implementation(libs.androidx.annotation)
    // @MonotonicNonNull 只是编译期注解，和 media3 自己一样用 compileOnly
    compileOnly(libs.checker.qual)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
}
