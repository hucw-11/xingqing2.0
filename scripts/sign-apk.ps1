param(
    [Parameter(Mandatory = $true)][string]$UnsignedApk,
    [Parameter(Mandatory = $true)][string]$ApkSignerJar,
    [string]$KeyPath = '',
    [string]$OutputApk = '',
    [string]$Java = ''
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent

if (-not $KeyPath) {
    # 复用旧版心晴的本机固定密钥；密钥不在本仓库、不复制、不上传。
    $KeyPath = Join-Path (Split-Path $repoRoot -Parent) 'xinqing/.local/xinqing-signing.p12'
}
if (-not (Test-Path -LiteralPath $KeyPath)) {
    throw "固定签名文件缺失：$KeyPath。请恢复本机密钥备份，不要生成新密钥，否则无法保证后续覆盖升级。"
}

if (-not $Java) {
    $candidate = 'D:/MATLAB/sys/java/jre/win64/jre/bin/java.exe'
    $Java = if (Test-Path -LiteralPath $candidate) { $candidate } else { 'java' }
}

if (-not $OutputApk) {
    $localDir = Join-Path $repoRoot '.local'
    New-Item -ItemType Directory -Force -Path $localDir | Out-Null
    $OutputApk = Join-Path $localDir 'xinqing2-signed.apk'
}

$certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($KeyPath, 'android')
try {
    $fingerprint = $certificate.GetCertHashString([System.Security.Cryptography.HashAlgorithmName]::SHA256)
    if ($fingerprint -ne '8E4256DD9BF214E20BB11EDEFED60BF6AEAD78E294DFB0173263C444487B789E') {
        throw '签名指纹与固定版本不一致，已停止，避免交付无法覆盖升级的 APK。'
    }
} finally {
    $certificate.Dispose()
}

& $Java -jar $ApkSignerJar sign --ks $KeyPath --ks-type PKCS12 --ks-key-alias xinqing --ks-pass pass:android --key-pass pass:android --out $OutputApk $UnsignedApk
if ($LASTEXITCODE -ne 0) { throw 'APK 签名失败' }

& $Java -jar $ApkSignerJar verify --verbose --print-certs $OutputApk
if ($LASTEXITCODE -ne 0) { throw 'APK 签名验证失败' }

Write-Output "已签名并验证：$OutputApk"
