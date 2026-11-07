plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "wraphex.slighte"
    compileSdk = 37
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "wraphex.slighte"
        minSdk = 26
        targetSdk = 37
        val versionX = 0
        val versionY = 0
        val versionZ = 1
        versionCode = versionX * 10000 + versionY * 100 + versionZ
        versionName = "$versionX.$versionY.$versionZ"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug") // TODO: replace sign
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    // YukiHook: core + compiler + one base module (libxposed API).
    implementation(platform(libs.yukihook.bom))
    implementation(libs.yukihook.core)
    implementation(libs.yukihook.runtime.libxposed)
    implementation(libs.yukihook.extension.kavaref)
    compileOnly(libs.libxposed.api)
    ksp(libs.yukihook.compiler)

    implementation(platform(libs.kavaref.bom))
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.android)
    implementation(libs.kavaref.extension)

    testImplementation(libs.junit)
}
