import com.android.build.api.dsl.ApplicationExtension

plugins {
    // 플러그인 선언 시 id("com.android.application")은 필수입니다.
    // alias만으로 해결되지 않는 내부 스코프 문제를 방지합니다.
    id("com.android.application")
    // Kotlin 플러그인
    kotlin("android")
    //hilt&di
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.blueprint)
}
// 2. AGP 9.x 스타일의 신규 DSL 설정
extensions.configure<ApplicationExtension> {
    namespace = "com.mytutor.touchwhere"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mytutor.touchwhere"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/*.kotlin_module"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    //Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // AndroidX & UI (TOML 참조)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    // DI (Hilt & KSP)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Network (Retrofit & OkHttp)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // Image & AI
    implementation(libs.glide.core)
    implementation(libs.generativeai)
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
}