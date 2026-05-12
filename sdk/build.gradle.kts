import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "app.prompthelm.sdk"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
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

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Strict explicit-API only on production sources — tests are free
// to use default visibility for clarity.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (!name.contains("Test")) {
        compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
    }
}

dependencies {
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    coordinates("app.prompthelm", "sdk-android", "0.1.0")

    pom {
        name.set("PromptHelm Android SDK")
        description.set("Official Android SDK for PromptHelm — versioned prompts, encrypted provider keys, multi-provider LLM gateway with cost & latency telemetry.")
        inceptionYear.set("2026")
        url.set("https://prompthelm.app")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("runivox")
                name.set("Runivox")
                url.set("https://github.com/Runivox")
            }
        }

        scm {
            url.set("https://github.com/Runivox/prompt-helm-sdk-android")
            connection.set("scm:git:git://github.com/Runivox/prompt-helm-sdk-android.git")
            developerConnection.set("scm:git:ssh://git@github.com/Runivox/prompt-helm-sdk-android.git")
        }
    }

    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}
