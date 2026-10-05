import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val termuxPrebuiltNative =
  providers.gradleProperty("termuxPrebuiltNative").orNull == "true"

val nexusVersion = Properties().apply {
  rootProject.file("version.properties").inputStream().use { load(it) }
}
val signingEnvironment = listOf("KEYSTORE_PATH", "STORE_PASSWORD", "KEY_PASSWORD").associateWith { System.getenv(it)?.takeIf(String::isNotBlank) }
val signingSupplied = signingEnvironment.values.all { it != null }
val unsignedRelease = providers.gradleProperty("unsignedRelease").orNull == "true"
require(signingEnvironment.values.none { it != null } || signingSupplied) {
  "Release signing is incomplete: provide KEYSTORE_PATH, STORE_PASSWORD and KEY_PASSWORD together."
}
require(!(unsignedRelease && signingSupplied)) { "unsignedRelease cannot be combined with signing credentials" }
gradle.taskGraph.whenReady {
  val releaseRequested = allTasks.any { it.project == project && it.name.endsWith("Release", ignoreCase = true) }
  if (releaseRequested && !signingSupplied && !unsignedRelease) {
    error("Release signing credentials are missing. For an explicitly UNSIGNED CI validation build, use -PunsignedRelease=true.")
  }
  if (releaseRequested && unsignedRelease) logger.lifecycle("UNSIGNED release validation: this artifact cannot upgrade an installed signed app.")
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.inputmapper.platform"
    minSdk = 24
    targetSdk = 36
    versionCode = nexusVersion.getProperty("versionCode").toInt()
    versionName = nexusVersion.getProperty("versionName")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    if (!termuxPrebuiltNative) {
      externalNativeBuild {
        cmake {
          cppFlags += listOf("-std=c++20", "-Wall", "-Wextra", "-Werror")
        }
      }
    }
  }

  signingConfigs {
    create("release") {
      storeFile = signingEnvironment["KEYSTORE_PATH"]?.let { file(it) }
      storePassword = signingEnvironment["STORE_PASSWORD"]
      keyAlias = "upload"
      keyPassword = signingEnvironment["KEY_PASSWORD"]
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (signingSupplied) signingConfig = signingConfigs.getByName("release")
    }
    debug { }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  buildFeatures {
    compose = true
    buildConfig = true
    aidl = true
  }

  sourceSets {
    getByName("main") {
      // AGP 9.1 exposes source directories through the mutable directories set.
      // The Termux build writes the verified prebuilt .so here before Gradle runs.
      jniLibs.directories.add("src/main/jniLibs")
      assets.directories.add(rootProject.file("kernelsu-module").absolutePath)
    }
  }

  if (!termuxPrebuiltNative) {
    externalNativeBuild {
      cmake {
        path = file("src/main/cpp/CMakeLists.txt")
      }
    }
  }

  ndkVersion = "27.0.12077973"

  packaging {
    jniLibs {
      useLegacyPackaging = true
      if (termuxPrebuiltNative) {
        // Android's NDK package contains desktop-host llvm-strip binaries. They cannot
        // execute inside ARM64 Termux. Preserve debug symbols for every JNI library in
        // this debug-only Termux path so AGP never invokes the incompatible host stripper.
        keepDebugSymbols += "**/*.so"
      }
    }
  }

  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation("dev.rikka.shizuku:api:13.1.5")
  implementation("dev.rikka.shizuku:provider:13.1.5")
  implementation("com.github.topjohnwu.libsu:core:6.0.0")
  implementation("com.github.topjohnwu.libsu:service:6.0.0")
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
