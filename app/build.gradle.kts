plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.goroyattemiyo.wallpaperfitslideshow"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.goroyattemiyo.wallpaperfitslideshow"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
}

dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
    testImplementation("junit:junit:4.13.2")
}
