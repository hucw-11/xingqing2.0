# 心晴 2.0

个人使用的安卓 + Windows 桌面生活安排与记录应用。

当前处于 **P1：跨平台骨架与数据基础**。本阶段只交付可构建的骨架、数据库迁移机制、导航与主题、凭据存储适配和 CI；课程、时间轴、日记、同步、AI、微信提醒均未实现，界面中的占位页已明确标注。

## 结构

```text
shared/       Kotlin Multiplatform：领域模型、数据库、共享 Compose UI
androidApp/   Android 壳与清单
desktopApp/   Compose Desktop 入口与打包
docs/         需求调查、技术方案、进度与交接记录
scripts/      APK 签名脚本
```

- 包名：`com.hucw.xinqing2`
- 桌面数据目录：`%APPDATA%/Xinqing2`
- 安卓数据目录：应用私有 `files/xinqing`
- 数据库：SQLite（`androidx.sqlite` bundled 驱动），手写迁移，当前 schema 版本 1
- 凭据：安卓 Android Keystore；Windows DPAPI

## 构建

本机不要求安装 Android Studio。GitHub Actions 会执行：

- Android：`./gradlew :shared:jvmTest :androidApp:lintDebug :androidApp:assembleDebug`
- Windows：`.\gradlew.bat :shared:jvmTest :desktopApp:createDistributable`

CI 产出的 APK 是 **未签名** 的，不能直接安装。下载 artifact 后在本机签名：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\sign-apk.ps1 -UnsignedApk <APK绝对路径> -ApkSignerJar <apksigner.jar绝对路径>
```

脚本默认复用相邻旧版仓库的固定密钥 `../xinqing/.local/xinqing-signing.p12`，并校验证书指纹；密钥不在本仓库、不复制、不上传。签名结果默认输出到 `.local/xinqing2-<versionName>-signed.apk`，例如 `.local/xinqing2-0.1.0-p1-signed.apk`。升版本后文件名会跟着变，避免新旧安装包混淆。

## P1 自检路径

1. 安装已签名 APK，或运行桌面产物。
2. 在“今天”页点击“写入一条自检记录”，数字加一。
3. 关闭应用再打开，数字应保留。
4. 点击“写入并读回测试值”，应显示“读回成功”。
5. 其余页面显示“占位页”，并标注计划阶段。

## 已知限制

- 未提交 CI 结果前，不能宣称构建通过。
- 尚未实现通知权限、精确闹钟、同步、AI 与微信提醒。
- 桌面端当前仅支持 Windows。
