plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.trading.profit"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.trading.profit"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
