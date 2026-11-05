plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.abhijith.mindlol"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.abhijith.mindlol"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Runs instrumented tests (app/src/androidTest) on the phone.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Room writes a JSON copy of the database schema here. It's committed to git so
// migrations can be checked against it (MigrationTest reads these files).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    // Constraints only pick a version; they never add a library by themselves.
    // room-testing 2.8.5 (MigrationTest) is built against kotlinx-serialization 1.8.1, but other
    // libraries pull in 1.7.3, and the test APK must use the app's version. Lifting the app to
    // 1.8.1 fixes MigrationTest's AbstractMethodError in FieldBundle$$serializer.
    constraints {
        implementation(libs.kotlinx.serialization.core) {
            because("room-testing 2.8.5 needs kotlinx-serialization 1.8.1 at runtime")
        }
    }
}
