import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }

    android {
        namespace = "com.hucw.xinqing2.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.androidx.sqlite)
        }

        androidMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
        }

        jvmMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
        }
    }
}
