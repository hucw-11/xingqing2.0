import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.koin.compiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

// 预览与诊断工具独立编译；正式 JAR 和安装包只包含 main 源集。
val preview = sourceSets.create("preview") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
configurations[preview.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[preview.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    implementation(project(":shared"))
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.annotations)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutines.swing)
    add(preview.implementationConfigurationName, libs.compose.ui.tooling.preview)
    add(preview.implementationConfigurationName, libs.compose.material3)
    add(preview.implementationConfigurationName, libs.kotlinx.datetime)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.androidx.room3.runtime)
}

tasks.withType<Test>().configureEach {
    // 无窗口测试真实启动依赖图，不访问用户的 APPDATA。
    systemProperty("java.awt.headless", "true")
    systemProperty("xinqing.dataDir", layout.buildDirectory.dir("startup-test-data").get().asFile.absolutePath)
}


// 版本单源：desktopApp 的安装包版本从 androidApp/build.gradle.kts 的 versionName 派生，
// 不再手写副本（历史上曾停在 2.12.0 / 3.70.5，落后 androidApp 一个主版本；
// tools/publish_new_version.py 只回写 androidApp 的 versionCode/versionName 那一行，
// 派生之后 desktopApp 自动跟随，无需再手工同步）。
// providers.fileContents(...).asText 是配置缓存安全的读取方式：它会把该文件登记为构建
// 输入，androidApp 版本变化时配置缓存正确失效（裸 File.readText 不会登记输入）。
val appVersionName: String = providers.fileContents(
    layout.projectDirectory.file("../androidApp/build.gradle.kts")
).asText.map { text ->
    val raw = Regex("^\\s*versionName\\s*=\\s*\"([^\"]+)\"", RegexOption.MULTILINE)
        .find(text)?.groupValues?.get(1)
        ?: error("desktopApp: 无法从 androidApp/build.gradle.kts 解析 versionName")
    // Compose Desktop 只接受数字型 MAJOR.MINOR.BUILD（MINOR ≤ 255、BUILD ≤ 65535）；
    // 带后缀的版本号要到打包阶段才报错，这里提前给出可定位的信息。
    require(Regex("^\\d+\\.\\d+\\.\\d+$").matches(raw)) {
        "desktopApp: androidApp versionName \"$raw\" 不是数字型 MAJOR.MINOR.BUILD，桌面安装包无法承载"
    }
    raw
}.get()

// 桌面端没有 Android PackageManager；依赖容器所需版本由同一构建配置生成。
val appVersionCode = providers.fileContents(
    layout.projectDirectory.file("../androidApp/build.gradle.kts")
).asText.map { text ->
    Regex("^\\s*versionCode\\s*=\\s*(\\d+)", RegexOption.MULTILINE)
        .find(text)?.groupValues?.get(1)?.toInt()
        ?: error("desktopApp: 无法解析 versionCode")
}.get()
val versionSourceDirectory = layout.buildDirectory.dir("generated/xinqingVersion/kotlin")
val generateDesktopBuildInfo = tasks.register("generateDesktopBuildInfo") {
    val releaseName = appVersionName
    val releaseCode = appVersionCode
    val outputDirectory = versionSourceDirectory
    inputs.property("versionName", releaseName)
    inputs.property("versionCode", releaseCode)
    outputs.dir(outputDirectory)
    doLast {
        val output = outputDirectory.get().file("com/shangkeschedule/DesktopBuildInfo.kt").asFile
        output.parentFile.mkdirs()
        output.writeText(
            "package com.shangkeschedule\n\n" +
                "internal object DesktopBuildInfo {\n" +
                "    const val VERSION_NAME = \"$releaseName\"\n" +
                "    const val VERSION_CODE = $releaseCode\n" +
                "}\n"
        )
    }
}
kotlin.sourceSets.named("main") { kotlin.srcDir(versionSourceDirectory) }
tasks.named("compileKotlin") { dependsOn(generateDesktopBuildInfo) }

// 保留既有诊断命令的入口别名；打包入口始终为正式 App。
val previewMainClass = providers.gradleProperty("previewMainClass").map { requested ->
    val name = requested.removePrefix("com.shangkeschedule.")
    when (name) {
        "ClaudePreviewKt" -> "com.shangkeschedule.preview.BookPreviewKt"
        "IosPreviewKt" -> "com.shangkeschedule.preview.ClearPreviewKt"
        "SoftPreviewKt", "ContrastProbeKt", "GlassProbeKt", "PaletteProbeKt" ->
            "com.shangkeschedule.preview.$name"
        else -> requested
    }
}

tasks.register<JavaExec>("runPreview") {
    group = "application"
    description = "运行独立预览/诊断源集，默认预览柔绘主题。"
    dependsOn(preview.classesTaskName)
    classpath = preview.runtimeClasspath
    mainClass.set(previewMainClass.orElse("com.shangkeschedule.preview.SoftPreviewKt"))
    workingDir = projectDir
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("shangkeschedule-desktop")
    archiveVersion.set(appVersionName)
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

compose.desktop {
    application {
        mainClass = "com.shangkeschedule.MainKt"

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Xinqing2"
            linux {
                packageName = "xinqing2"
            }
            // 版本单源：取自上方 appVersionName，见文件顶部派生逻辑。
            packageVersion = appVersionName
        }
    }
}

// Compose 在配置收尾时设置 run 任务，随后再应用兼容入口，防止被正式入口覆盖。
afterEvaluate {
    if (previewMainClass.isPresent) {
        tasks.named<JavaExec>("run") {
            dependsOn(preview.classesTaskName)
            classpath = preview.runtimeClasspath
            mainClass.set(previewMainClass)
        }
    }
}
