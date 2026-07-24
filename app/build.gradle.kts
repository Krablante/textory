plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningEnvironment = mapOf(
    "storeFile" to "TEXTORY_RELEASE_STORE_FILE",
    "storePassword" to "TEXTORY_RELEASE_STORE_PASSWORD",
    "keyAlias" to "TEXTORY_RELEASE_KEY_ALIAS",
    "keyPassword" to "TEXTORY_RELEASE_KEY_PASSWORD",
)
val releaseSigningValues = releaseSigningEnvironment.mapValues { (_, environmentName) ->
    providers.environmentVariable(environmentName).orNull
}
val configuredReleaseSigningValues = releaseSigningValues.filterValues { !it.isNullOrBlank() }

check(configuredReleaseSigningValues.isEmpty() || configuredReleaseSigningValues.size == releaseSigningValues.size) {
    val missing = releaseSigningEnvironment
        .filterKeys { releaseSigningValues[it].isNullOrBlank() }
        .values
        .joinToString()
    "Release signing is partially configured. Missing environment variables: $missing"
}

val releaseSigningEnabled = configuredReleaseSigningValues.size == releaseSigningValues.size

android {
    namespace = "mom.cosmism.textory"
    compileSdk = 37

    defaultConfig {
        applicationId = "mom.cosmism.textory"
        minSdk = 26
        targetSdk = 37
        versionCode = 36
        versionName = "0.11.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (releaseSigningEnabled) {
            create("externalRelease") {
                storeFile = file(releaseSigningValues.getValue("storeFile")!!)
                storePassword = releaseSigningValues.getValue("storePassword")
                keyAlias = releaseSigningValues.getValue("keyAlias")
                keyPassword = releaseSigningValues.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("externalRelease")
            }
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

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
        )
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.github.java-diff-utils:java-diff-utils:4.17")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
