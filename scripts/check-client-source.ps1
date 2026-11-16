$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$client = Join-Path $repoRoot 'client'
$manifest = Get-Content -LiteralPath (Join-Path $repoRoot 'docs/upstream-import-manifest.json') -Raw | ConvertFrom-Json

# 核对用户最重视的原版页面、组件和导入实现没有被简化重写。
$preserved = @($manifest.files | Where-Object {
    $_.path -match '^shared/src/commonMain/kotlin/com/shangkeschedule/(ui/(agenda|schedule|components|theme)/|data/parser/)'
})

# 本批唯一允许改动的原版组件：主导航要新增“日记”第五入口（任务书 §4 明确要求）。
# 例外只针对这一个文件；日程、课表、解析器、主题及其他组件仍按原哈希保护。
# 用下面的导航回归检查代替该文件的哈希校验，确认原四个入口没有被删改。
$allowedEdits = @{
    'shared/src/commonMain/kotlin/com/shangkeschedule/ui/components/NavigationComponents.kt' = '新增日记入口'
}
$unchangedCount = 0
foreach ($entry in $preserved) {
    $path = Join-Path $client $entry.path
    if (-not (Test-Path -LiteralPath $path)) { throw "缺少原版文件：$($entry.path)" }
    if ($allowedEdits.ContainsKey($entry.path)) { continue }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
        throw "原版保留区域已改变，需要复核：$($entry.path)"
    }
    $unchangedCount++
}

$navPath = Join-Path $client 'shared/src/commonMain/kotlin/com/shangkeschedule/ui/components/NavigationComponents.kt'
$navSource = Get-Content -LiteralPath $navPath -Raw
foreach ($required in @(
    'Destination.TodaySchedule',
    'Destination.CourseSchedule',
    'Destination.Schedule',
    'Destination.Settings',
    'Destination.Diary'
)) {
    if ($navSource -notmatch [regex]::Escape($required)) {
        throw "主导航回归失败：缺少 $required"
    }
}

$xmlFiles = @(Get-ChildItem -LiteralPath (Join-Path $client 'androidApp/src'), (Join-Path $client 'shared/src/commonMain/composeResources') -Recurse -Filter '*.xml')
foreach ($file in $xmlFiles) {
    try { $null = [xml][IO.File]::ReadAllText($file.FullName) }
    catch { throw "XML 格式错误：$($file.FullName)：$_" }
}

$androidBuild = Get-Content -LiteralPath (Join-Path $client 'androidApp/build.gradle.kts') -Raw
if ($androidBuild -notmatch 'applicationId = "com.hucw.xinqing2"') { throw 'Android 包名未隔离' }
$calendarXml = [xml](Get-Content -LiteralPath (Join-Path $client 'androidApp/src/main/res/values/calendar_account.xml') -Raw)
if ($calendarXml.resources.string.InnerText -ne 'com.hucw.xinqing2.calendar') { throw '系统日历账户类型未隔离' }
$desktopDirs = Get-Content -LiteralPath (Join-Path $client 'shared/src/jvmMain/kotlin/com/shangkeschedule/tool/DesktopAppDirs.kt') -Raw
if ($desktopDirs -notmatch 'FOLDER_NAME = "Xinqing2/UpstreamBase"') { throw '桌面数据目录未隔离' }

foreach ($relative in @('scripts/sign-apk.ps1', 'scripts/check-client-source.ps1')) {
    $parseErrors = $null
    $null = [Management.Automation.Language.Parser]::ParseFile((Join-Path $repoRoot $relative), [ref]$null, [ref]$parseErrors)
    if ($parseErrors.Count) { throw "PowerShell 语法错误：$relative" }
}
Write-Output "通过：$unchangedCount 个原版页面/组件/解析器保持原样；主导航例外 1 个（已通过四入口回归）；$($xmlFiles.Count) 个 XML 可解析；包名、日历账户、桌面目录及脚本语法通过。"
Write-Output '这不是 Kotlin 编译或 UI 验收；二者需要 CI 和用户测试。'
