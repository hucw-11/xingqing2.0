# 心晴 2.0

个人使用的安卓与 Windows 生活安排、日记和心情应用。

2026-11-16：已按用户确认路线导入固定版本“上课”的完整客户端。**当前构建入口是 client/**。保留原版课程、日程、时间轴、主题及文件导入。根部旧 shared/androidApp/desktopApp 是迁移前代码，暂留作规则与测试参考，不再用于发布。

来源：[client/UPSTREAM.md](client/UPSTREAM.md)。路线：[纠偏方案](docs/复用路线纠偏方案.md)。

0.3.0已通过双端CI并完成Android固定签名，等待用户体验验收：[下载位置、校验值及测试步骤](docs/验收-0.3.0-上课底座.md)。

## 当前批次

- Android：包名 com.hucw.xinqing2，版本 0.3.0 / code 3，内部源码包名保留上游。
- 默认打开原版日程页，保留月历、日期轴、课程/日程/待办及创建交互。
- Windows 数据：%APPDATA%/Xinqing2/UpstreamBase，与原版上课及旧心晴dev数据隔离。
- 旧dev安卓数据留在原有目录；本批不迁移、不删除，新页面不会自动显示旧dev记录。
- 设置主页隐藏情侣课表、成绩、学业、考证、空教室入口；无关底层代码暂保留。
- 日记、心情统计、DeepSeek、坚果云双向同步和微信云提醒仍待开发。现有 WebDAV 是原版备份恢复。

## 构建与检查

本机不要求安装 Android Studio。GitHub Actions 从 client/ 执行：

```text
./gradlew :shared:jvmTest -PuseMirror=false
./gradlew :androidApp:assembleDebug :androidApp:lintDebug -PuseMirror=false
.\gradlew.bat :desktopApp:createDistributable -PuseMirror=false
```

Windows CI 只生成并检查产物，不启动程序。静态核对：

```powershell
pwsh -File scripts/check-client-source.ps1
```

## APK 签名

CI artifact xinqing2-ci-apk 包含各 ABI 开发 APK 和 apksigner。开发包带运行器临时调试签名，交付前必须用固定密钥重新签名，不直接安装临时签名包。

```powershell
pwsh -File scripts/sign-apk.ps1 -UnsignedApk <所需ABI的APK绝对路径> -ApkSignerJar <apksigner.jar绝对路径>
```

脚本沿用本机固定密钥并校验证书，版本从 client/androidApp/build.gradle.kts 读取。输出 .local/xinqing2-0.3.0-signed.apk；每次只选择一个 ABI，arm64 手机通常使用 arm64-v8a，不要将不同 ABI 当成多个版本轮流覆盖输出。

## 用户验收

1. 安装固定密钥签名包，确认名称“心晴 2.0”。
2. 确认默认原版日程页，日期面板、时间轴与原版交互一致。
3. 在“我的”设置学期、作息和课程，或通过文件导入课表。
4. 添加当天日程、待办，检查课程与日程同屏，完成状态重启保留。
5. Windows 包由用户手动启动；本批没有双向同步，不会自动出现手机记录。

构建以当前提交 CI 为准。源码导入、静态检查、编译和用户体验分别记录。
