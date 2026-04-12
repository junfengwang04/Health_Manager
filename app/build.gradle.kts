import java.util.Properties
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.exists()) {
        localFile.inputStream().use { load(it) }
    }
}
val pbBaseUrl: String = localProperties.getProperty("PB_BASE_URL", "")
val pbAdminEmail: String = localProperties.getProperty("PB_ADMIN_EMAIL", "")
val pbAdminPassword: String = localProperties.getProperty("PB_ADMIN_PASSWORD", "")
val bmobAppId: String = localProperties.getProperty("BMOB_APP_ID", "")
val bmobRestKey: String = localProperties.getProperty("BMOB_REST_KEY", "")
val bmobMasterKey: String = localProperties.getProperty("BMOB_MASTER_KEY", "")
val bmobBaseUrl: String = localProperties.getProperty("BMOB_BASE_URL", "")
val qwenApiKey: String = localProperties.getProperty("QWEN_API_KEY", "")
val qwenChatUrl: String = localProperties.getProperty("QWEN_CHAT_URL", "")
val qwenModel: String = localProperties.getProperty("QWEN_MODEL", "qwen3.5-flash")
val baiduApiKey: String = localProperties.getProperty("BAIDU_API_KEY", "")
val baiduSecretKey: String = localProperties.getProperty("BAIDU_SECRET_KEY", "")
val baiduTokenUrl: String = localProperties.getProperty("BAIDU_TOKEN_URL", "")
val baiduFoodIdentifyUrl: String = localProperties.getProperty("BAIDU_FOOD_IDENTIFY_URL", "")

android {
    namespace = "com.pbz.healthmanager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pbz.healthmanager"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PB_BASE_URL", "\"$pbBaseUrl\"")
        buildConfigField("String", "PB_ADMIN_EMAIL", "\"$pbAdminEmail\"")
        buildConfigField("String", "PB_ADMIN_PASSWORD", "\"$pbAdminPassword\"")
        buildConfigField("String", "BMOB_APP_ID", "\"$bmobAppId\"")
        buildConfigField("String", "BMOB_REST_KEY", "\"$bmobRestKey\"")
        buildConfigField("String", "BMOB_MASTER_KEY", "\"$bmobMasterKey\"")
        buildConfigField("String", "BMOB_BASE_URL", "\"$bmobBaseUrl\"")
        buildConfigField("String", "QWEN_API_KEY", "\"$qwenApiKey\"")
        buildConfigField("String", "QWEN_CHAT_URL", "\"$qwenChatUrl\"")
        buildConfigField("String", "QWEN_MODEL", "\"$qwenModel\"")
        buildConfigField("String", "BAIDU_API_KEY", "\"$baiduApiKey\"")
        buildConfigField("String", "BAIDU_SECRET_KEY", "\"$baiduSecretKey\"")
        buildConfigField("String", "BAIDU_TOKEN_URL", "\"$baiduTokenUrl\"")
        buildConfigField("String", "BAIDU_FOOD_IDENTIFY_URL", "\"$baiduFoodIdentifyUrl\"")
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.mlkit.text.recognition.common)
    implementation(libs.play.services.mlkit.text.recognition.chinese)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.extensions)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx.v284)
    implementation(libs.androidx.work.runtime.ktx.v283)
    implementation(libs.androidx.navigation.compose.v277)

    //OkHttp & Gson
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    //Ktor client for PocketBase
    implementation("io.ktor:ktor-client-okhttp:2.3.11")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")

    //Coil for Image Loading
    implementation("io.coil-kt:coil-compose:2.4.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
