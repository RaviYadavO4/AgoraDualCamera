import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.agoradualcamera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.agoradualcamera"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "AGORA_APP_ID", "\"5fb926599aeb4ba391c29247cc3b6f71\"")
        buildConfigField("String", "AGORA_CERTIFICATE", "\"b5065fbfa5ed4d8aba0c25de974502b1\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all{
        outputs.all {
            val formattedDate = getDate()
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl).outputFileName = "agora_dual_cam_${formattedDate}_$versionName.apk"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

fun Date.formatToCustomString(): String {
    val dateFormat = SimpleDateFormat("ddMMMyyyy_HH-mm")
    return dateFormat.format(this)
}
fun getDate(): String {
    val date = Date()
    return date.formatToCustomString()
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation("io.agora.rtc:full-sdk:4.1.1")
    implementation("commons-codec:commons-codec:1.11")

    val camerax_version = "1.3.0"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
}