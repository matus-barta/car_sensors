import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.anonymus09.carsensors"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.anonymus09.carsensors"
        minSdk = 28
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 28
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }

        /*
         * Declared here rather than in CI so the same command runs in both
         * places. Nothing is downloaded until the task is invoked, so this
         * costs a local checkout nothing - testing against a real handset over
         * adb stays the faster local path.
         *
         * aosp-atd is an Automated Test Device: the pre-installed apps and
         * background services are stripped out and rendering is headless,
         * which is what makes it affordable on a runner. ATD images exist only
         * for API 30, which is newer than this app targets - immaterial for a
         * Room migration, which is SQLite and framework.
         */
        managedDevices {
            localDevices {
                create("api30atd") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    // MigrationTestHelper reads the exported schemas from the test APK's
    // assets, so the directory ksp writes them to has to be packaged into it.
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
    lint {
        // A warning nobody has to act on is a warning nobody reads, and there
        // are currently none to act on.
        warningsAsErrors = true
        abortOnError = true

        // Reports what has been published since, not anything about this code,
        // so it would turn a passing build red without a commit being made.
        disable += "NewerVersionAvailable"
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG gates whether the address field accepts http://.
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    // Exported schemas are what make a future migration writable and testable.
    arg("room.schemaLocation", "$projectDir/schemas")
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    // Adds to the defaults rather than replacing them, so only the rules named
    // in that file differ and everything else stays as detekt ships it.
    buildUponDefaultConfig = true
    baseline = file("$rootDir/config/detekt/baseline.xml")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/*
 * room-testing parses the exported schemas with kotlinx-serialization. The
 * lifecycle libraries pin serialization-core to 1.7.3 while the json artefact
 * resolves to 1.8.1, and that pairing throws AbstractMethodError the moment a
 * schema is read. Aligning them is scoped to the instrumented test classpath so
 * the app itself keeps exactly the versions its own dependencies asked for.
 */
configurations.matching { it.name.contains("AndroidTest") }.configureEach {
    resolutionStrategy {
        force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
        force("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)
}
