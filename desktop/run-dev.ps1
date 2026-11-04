# 快速编译并直接运行（不打包 exe），适合开发调试。
# 用法: pwsh desktop/run-dev.ps1 [-Headless]
param(
    [switch]$headless
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$desktop = $PSScriptRoot
$cp = "$desktop/libs/androidasync-3.1.0.jar;$desktop/libs/json-20240303.jar"
$jar = "$desktop/build/libs/codearts-gateway-desktop.jar"

# 解决 JDK 21 在 Windows 下 PipeImpl 的 Unix Domain Socket 路径空格问题
if (!(Test-Path "C:\Temp")) { New-Item -ItemType Directory -Path "C:\Temp" -Force | Out-Null }
$env:TEMP = "C:\Temp"
$env:TMP = "C:\Temp"

# ---------- 1) 收集源码并编译 ----------
Write-Host "[1/3] javac 编译中..." -ForegroundColor Cyan
$srcs = @(
    Get-ChildItem -Path "$desktop/src/main/java" -Recurse -Filter *.java
    Get-ChildItem -Path "$root/app/src/main/java/com/codearts/gateway/core" -Recurse -Filter *.java
)
$srcsFile = "$desktop/build/srcs.txt"
New-Item -ItemType Directory -Force -Path "$desktop/build/classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$desktop/build/fat"     | Out-Null
New-Item -ItemType Directory -Force -Path "$desktop/build/libs"    | Out-Null
$srcs | ForEach-Object { "`"$($_.FullName.Replace('\','/'))`"" } | Set-Content $srcsFile -Encoding utf8

& "$env:JAVA_HOME/bin/javac" -nowarn -encoding UTF-8 -cp $cp -d "$desktop/build/classes" "@$srcsFile"
if ($LASTEXITCODE -ne 0) { throw "javac 编译失败" }

# ---------- 2) 组装 Jar ----------
Write-Host "[2/3] 打包测试 Jar..." -ForegroundColor Cyan
if (Test-Path "$desktop/build/fat") { Remove-Item "$desktop/build/fat" -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$desktop/build/fat" | Out-Null
Copy-Item -Path "$desktop/build/classes/*" -Destination "$desktop/build/fat" -Recurse -Force
tar -xf "$desktop/libs/androidasync-3.1.0.jar" -C "$desktop/build/fat"
tar -xf "$desktop/libs/json-20240303.jar"      -C "$desktop/build/fat"
Remove-Item "$desktop/build/fat/META-INF/*.SF","$desktop/build/fat/META-INF/*.DSA","$desktop/build/fat/META-INF/*.RSA" -Force -ErrorAction SilentlyContinue
& "$env:JAVA_HOME/bin/jar" --create --file $jar --main-class com.codearts.gateway.desktop.Main -C "$desktop/build/fat" .
if ($LASTEXITCODE -ne 0) { throw "jar 打包失败" }

# ---------- 3) 启动应用 ----------
$args2 = @("-jar", $jar)
if ($headless) { $args2 += "--headless" }
Write-Host "[3/3] 正在启动网关 (java $($args2 -join ' '))..." -ForegroundColor Green
& "$env:JAVA_HOME/bin/java" @args2
$root = Split-Path $PSScriptRoot -Parent   # 项目根目录
$desktop = $PSScriptRoot                   # desktop/
$cp = "$desktop/libs/androidasync-3.1.0.jar;$desktop/libs/json-20240303.jar"
$jar = "$desktop/build/libs/codearts-gateway-desktop.jar"

# ---------- 1) 收集源码 ----------
$srcs = @(
    Get-ChildItem -Path "$desktop/src/main/java" -Recurse -Filter *.java
    Get-ChildItem -Path "$root/app/src/main/java/com/codearts/gateway/core" -Recurse -Filter *.java
)
$srcsFile = "$desktop/build/srcs.txt"
New-Item -ItemType Directory -Force -Path "$desktop/build/classes" | Out-Null
New-Item -ItemType Directory -Force -Path "$desktop/build/fat"     | Out-Null
New-Item -ItemType Directory -Force -Path "$desktop/build/libs"    | Out-Null
$srcs | ForEach-Object { "`"$($_.FullName.Replace('\','/'))`"" } | Set-Content $srcsFile -Encoding utf8

# ---------- 2) 编译 ----------
Write-Host "[1/3] javac ..." -ForegroundColor Cyan
& "$env:JAVA_HOME/bin/javac" -nowarn -encoding UTF-8 -cp $cp -d "$desktop/build/classes" "@$srcsFile"
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# ---------- 3) 打 fat-jar ----------
Write-Host "[2/3] fat-jar ..." -ForegroundColor Cyan
# 清空 fat 目录
if (Test-Path "$desktop/build/fat") { Remove-Item "$desktop/build/fat" -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$desktop/build/fat" | Out-Null
Copy-Item -Path "$desktop/build/classes/*" -Destination "$desktop/build/fat" -Recurse -Force
tar -xf "$desktop/libs/androidasync-3.1.0.jar" -C "$desktop/build/fat"
tar -xf "$desktop/libs/json-20240303.jar"      -C "$desktop/build/fat"
Remove-Item "$desktop/build/fat/META-INF/*.SF","$desktop/build/fat/META-INF/*.DSA","$desktop/build/fat/META-INF/*.RSA" -Force -ErrorAction SilentlyContinue
& "$env:JAVA_HOME/bin/jar" --create --file $jar --main-class com.codearts.gateway.desktop.Main -C "$desktop/build/fat" .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Host "[OK] $jar" -ForegroundColor Green

# ---------- 4) 直接运行 ----------
$args2 = @("-jar", $jar)
if ($headless) { $args2 += "--headless" }
Write-Host "[3/3] java $($args2 -join ' ')" -ForegroundColor Cyan
& "$env:JAVA_HOME/bin/java" @args2
