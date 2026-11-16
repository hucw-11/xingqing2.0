import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.hucw.xinqing2.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "Xinqing2"
            packageVersion = "0.2.0"
            description = "心晴 2.0"
            vendor = "hucw"
        }
    }
}
