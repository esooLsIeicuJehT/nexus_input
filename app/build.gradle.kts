plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val termuxPrebuiltNative =
    providers.gradleProperty("termuxPrebuiltNative").orNull == "true"

android {
    namespace = "com.inputmapper.platform"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.inputmapper.platform"
        minSdk = 36
        targetSdk = 36
        versionCode = 14
        versionName = "0.6.2-dev-hardening"

        if (!termuxPrebuiltNative) {
            externalNativeBuild {
                cmake {
                    cppFlags += listOf("-std=c++20", "-Wall", "-Wextra", "-Werror")
                }
            }
        }
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

    if (!termuxPrebuiltNative) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    ndkVersion = "27.0.12077973"

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Keep the JNI library available to the libsu RootService process.
            useLegacyPackaging = true
            // Termux builds provide a prebuilt ARM64 .so. AGP's SDK llvm-strip is a
            // desktop-host binary on this phone, so do not invoke it for this library.
            if (termuxPrebuiltNative) {
                keepDebugSymbols += "**/libuinput_jni.so"
            }
        }
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
    implementation("com.github.topjohnwu.libsu:service:6.0.0")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

