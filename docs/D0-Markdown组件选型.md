# D0：Markdown 组件选型与接入点

日期：2026-11-16。范围仅限本批图文日记，不重做全项目选型。

## 1. 候选比较

| 项目 | `org.jetbrains:markdown:0.7.18` | `com.mikepenz:multiplatform-markdown-renderer:0.46.0` |
|---|---|---|
| 许可 | Apache-2.0 | Apache-2.0 |
| 平台 | 纯 Kotlin，JVM / Android / iOS / JS（无 Compose 依赖） | Compose Multiplatform，Android / JVM / iOS 等 |
| 能力 | 官方 CommonMark 解析器（IntelliJ 同源），输出 AST；不提供 Compose 渲染与编辑器 | 现成的 Compose 渲染组件（标题、列表、代码、链接、图片），不提供编辑能力 |
| 与本项目工具链 | Kotlin stdlib 2.0.0 起，Kotlin 2.4.0 / Compose 1.11.1 可用 | 0.46.0 以 Kotlin 2.4.21 编译，高于本仓库 Kotlin 2.4.0；渲染器还带 Compose / Coil 传递依赖与版本耦合 |
| 可控性 | 可直接对 AST 决定 `attachment://` 本地附图如何解析 | 图片变换接口随版本变化，无法用本机编译器核对；本地附件协议需要绕行 |

## 2. 决定

采用 **`org.jetbrains:markdown:0.7.18`（Apache-2.0）解析 + 本仓库自绘 Compose 渲染**。

- 不自行编写 Markdown 解析器：块级/行内语法全部由 JetBrains 官方解析器产出 AST。
- 渲染层只按 AST 映射 Compose 文本与图片，支持标题、段落、粗体/斜体、删除线、列表、引用、链接、代码块/行内代码、分隔线与本地图片。
- 编辑器采用 **Markdown 源码区 + 选区格式工具栏 + 预览切换**，原文是唯一长期数据；不做富文本往返转换，因此中文输入组合不会被格式化打断。
- 版本固定在 `gradle/libs.versions.toml`，来源为 Maven Central；许可证进入现有开源许可页（AboutLibraries 自动汇总）。

不选 mikepenz 渲染器的原因：其最新版以更高 Kotlin 版本编译，与本仓库固定的 Kotlin 2.4.0 存在元数据兼容风险；且本批必须支持 `attachment://<id>` 这种应用私有附图协议，自绘渲染更可控。若后续升级 Kotlin 后需要更完整样式，可再评估替换渲染层，解析层可保留。

## 3. 复用点

- 页面骨架：`AppTopAppBar`、`AdaptiveNavigationScaffold`、`AppCard / SectionCard`、`AppTextField`、`AppSegmentedControl`、`AppGlassBottomSheet`、`AppDialogActions`、`AppAlertDialog`、`ToastManager`、`NativeNumberPicker`、`DatePickerModal`、`DateRangePickerModal`。
- 主题：`appColors / appSpacing / appType / appShapes`，不新增配色体系。
- 图片：`rememberFileManager().pickImage()` + `cropImageBitmapNative()`（三端统一 JPEG）+ Coil `AsyncImage`；附件文件放应用私有目录，不复用 `CourseNote` 的绝对路径方案。
- 数据：现有 Room 3 主库、`DatabaseMigrations.ALL_MIGRATIONS`、Koin 注解扫描。
- 导航：`Navigation.kt` 的序列化 `Destination` 与 `App.kt` 的 `when`；新增第五个主入口“日记”。

## 4. 新增文件

- `data/db/main/Diary*.kt`：实体、DAO、关系投影。
- `data/repository/DiaryRepository.kt`、`DiaryTagRepository.kt`、`MoodRepository.kt`。
- `ui/diary/`：列表、编辑、详情、快速心情、标签管理、Markdown 渲染与格式工具栏。
- `shared/src/jvmTest/.../Diary*.kt`：迁移保数据、事务与筛选、草稿顺序、附件路径测试。
- `docs/D0-Markdown组件选型.md`（本文件）。

## 5. 本机限制

本机没有 JDK / Gradle / Android SDK，无法本地编译。实际编译、Room schema 导出、双端打包与迁移验证由现有 GitHub Actions 执行；界面手感与中文输入由用户真机/桌面验收。
