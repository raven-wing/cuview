import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

fun requiredConfig(envKey: String, propKey: String): String =
    System.getenv(envKey)
        ?: localProps[propKey] as String?
        ?: rootProject.properties[propKey] as String?
        ?: error("Missing $envKey / $propKey")

android {
    namespace = "io.github.raven_wing.cuview"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.raven_wing.cuview"
        minSdk = 26
        targetSdk = 35
        versionCode = (rootProject.properties["VERSION_CODE"] as String?)?.toInt() ?: 1
        versionName = rootProject.properties["VERSION_NAME"] as String? ?: "0.1.0"
        buildConfigField("String", "CLICKUP_CLIENT_ID",    "\"${requiredConfig("CLICKUP_CLIENT_ID", "clickup.client.id")}\"")
        buildConfigField("String", "CLOUDFLARE_WORKER_URL", "\"${requiredConfig("CLOUDFLARE_WORKER_URL", "cloudflare.worker.url")}\"")
        buildConfigField("Boolean", "USE_MOCK_API", "false")
    }

    signingConfigs {
        create("release") {
            // Keystore resolution order: CI env vars → local.properties.
            // CI: set SIGNING_KEYSTORE_PATH, SIGNING_KEYSTORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD.
            // Local: add keystore.path / keystore.password / keystore.key.alias / keystore.key.password
            //        to local.properties.
            val ksPath = System.getenv("SIGNING_KEYSTORE_PATH")
                ?: localProps["keystore.path"] as String?
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                    ?: localProps["keystore.password"] as String
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                    ?: localProps["keystore.key.alias"] as String
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
                    ?: localProps["keystore.key.password"] as String
            }
        }
    }

    buildTypes {
        debug {
            val useMock = System.getenv("USE_MOCK_API")
                ?: localProps["use.mock.api"] as String?
                ?: "true"
            buildConfigField("Boolean", "USE_MOCK_API", useMock)
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("main") {
            java.srcDir("src/main/kotlin")
        }
        getByName("test") {
            java.srcDir("src/test/kotlin")
        }
        // Release-only unit tests: used for tests that require USE_MOCK_API=false,
        // i.e. tests that exercise real network paths through the repository layer.
        getByName("testRelease") {
            java.srcDir("src/testRelease/kotlin")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    lintChecks(project(":lint-rules"))
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
}
