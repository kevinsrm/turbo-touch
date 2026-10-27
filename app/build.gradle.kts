import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
//  Configuracao de anuncios: ads_config.properties (+ ads_config.local.properties)
//  vira app/src/main/assets/ads_config.properties dentro do APK.
// ---------------------------------------------------------------------------
val adsBaseFile = rootProject.file("ads_config.properties")
val adsLocalFile = rootProject.file("ads_config.local.properties")

fun loadProps(file: java.io.File): Properties {
    val p = Properties()
    if (file.exists()) file.inputStream().use { p.load(it) }
    return p
}

val adsProperties: Properties = Properties().apply {
    putAll(loadProps(adsBaseFile))
    putAll(loadProps(adsLocalFile))
}

fun adsValue(key: String, fallback: String): String =
    (adsProperties.getProperty(key) ?: fallback).trim()

val generateAdsConfig by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/ads")
    val values = mapOf(
        "ads.enabled" to adsValue("ads.enabled", "false"),
        "ads.provider" to adsValue("ads.provider", "none"),
        "ads.smartlink.url" to adsValue("ads.smartlink.url", ""),
        "ads.admob.app_id" to adsValue("ads.admob.app_id", "ca-app-pub-3940256099942544~3347511713"),
        "ads.admob.banner_unit" to adsValue("ads.admob.banner_unit", ""),
        "ads.admob.interstitial_unit" to adsValue("ads.admob.interstitial_unit", ""),
        "ads.background.interval_sec" to adsValue("ads.background.interval_sec", "45"),
        "ads.debug" to adsValue("ads.debug", "false"),
        "ads.reflective.init_class" to adsValue("ads.reflective.init_class", ""),
        "ads.reflective.init_method" to adsValue("ads.reflective.init_method", "initialize"),
        "ads.reflective.banner_class" to adsValue("ads.reflective.banner_class", ""),
        "ads.reflective.interstitial_class" to adsValue("ads.reflective.interstitial_class", "")
    )
    inputs.properties(values)
    outputs.dir(outputDir)
    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        val body = values.entries.joinToString("\n") { "${it.key}=${it.value}" }
        dir.resolve("ads_config.properties").writeText(
            "# gerado pelo build a partir de ads_config.properties — nao editar\n" + body + "\n"
        )
    }
}

android {
    namespace = "com.kevinsrm.turbotouch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kevinsrm.turbotouch"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // app id do AdMob vem do arquivo de configuracao; o padrao e o app id
        // de teste oficial do Google, valido mesmo sem conta AdMob.
        manifestPlaceholders["admobAppId"] =
            adsValue("ads.admob.app_id", "ca-app-pub-3940256099942544~3347511713")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/ads"))

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

tasks.named("preBuild").configure { dependsOn(generateAdsConfig) }
tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach { dependsOn(generateAdsConfig) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.ads)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
