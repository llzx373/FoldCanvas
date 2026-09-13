import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// CI release 签名：配置 FOLDCANVAS_* 环境变量且 keystore 文件存在时启用，
// 否则 release 保持未签名（本地构建不受影响）
val releaseStoreFile = System.getenv("FOLDCANVAS_STORE_FILE")
    ?.takeIf { it.isNotBlank() && File(it).isFile }

android {
    namespace = "com.llzx373.foldcanvas"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.llzx373.foldcanvas"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("FOLDCANVAS_STORE_PASSWORD")
                keyAlias = System.getenv("FOLDCANVAS_KEY_ALIAS")
                keyPassword = System.getenv("FOLDCANVAS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        unitTests.isIncludeAndroidResources = true
    }
}

// AGP 9 移除了 archivesBaseName，打包任务完成后复制一份品牌命名的 APK
//（保留原始 app-*.apk，否则任务声明产物缺失会破坏增量打包）
tasks.matching { it.name == "packageDebug" || it.name == "packageRelease" }.configureEach {
    val buildType = name.removePrefix("package").replaceFirstChar { it.lowercase() }
    val apkDir = layout.buildDirectory.dir("outputs/apk/$buildType")
    val apkBaseName = "FoldCanvas-v${android.defaultConfig.versionName}-$buildType.apk"
    doLast {
        val outDir = apkDir.get().asFile
        outDir.listFiles { f -> f.extension == "apk" && f.name.startsWith("app-") }?.forEach { apk ->
            apk.copyTo(File(outDir, apkBaseName), overwrite = true)
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
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.window)
    implementation(libs.axiom.sdk)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}