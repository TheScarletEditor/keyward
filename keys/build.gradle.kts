plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.thescarletcoder.keys"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thescarletcoder.keys"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
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
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources { noCompress += "dict" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    testImplementation("junit:junit:4.13.2")
}

// Same rule as Scarlet Command: a keyboard sees everything typed, so it must never have network access.
tasks.matching { it.name.matches(Regex("process[A-Za-z]+MainManifest")) }.configureEach {
    doLast {
        outputs.files.asFileTree.matching { include("**/AndroidManifest.xml") }.forEach { f ->
            val banned = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")
                .filter { f.readText().contains("\"$it\"") }
            if (banned.isNotEmpty()) {
                throw GradleException("Merged manifest ${f.path} contains $banned. A dependency pulled in network access. Stop and flag it to Sam.")
            }
        }
    }
}
