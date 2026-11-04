@echo off
chcp 65001 >nul
rem 生成内置 JRE 的 Windows GUI exe（需要 JDK 17，带 jpackage）
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem 找 jpackage
set "JPACKAGE="
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\jpackage.exe" set JPACKAGE=%JAVA_HOME%\bin\jpackage.exe
if "%JPACKAGE%"=="" for %%I in (jpackage.exe) do set JPACKAGE=%%~$PATH:I
if "%JPACKAGE%"=="" (
  echo [ERROR] 找不到 jpackage。请安装 JDK 17+（不是 JRE）并设置 JAVA_HOME。
  echo         下载: https://adoptium.net/temurin/releases/?version=17
  exit /b 1
)

rem 1) 准备 jar
if not exist build\libs\codearts-gateway-desktop.jar (
  echo [1/3] 未发现 jar，先构建...
  call build-desktop.bat || exit /b 1
) else (
  echo [1/3] 使用已有 build\libs\codearts-gateway-desktop.jar
)

rem 2) 准备 jpackage 输入目录（只放 jar）
if exist build\exe-input rmdir /s /q build\exe-input
mkdir build\exe-input
copy /y "build\libs\codearts-gateway-desktop.jar" "build\exe-input\" >nul

rem 3) 生成 app-image（会自动带上运行时 JRE）
if exist dist\CodeArtsGateway rmdir /s /q dist\CodeArtsGateway
echo [2/3] jpackage 生成 exe...
"%JPACKAGE%" --type app-image --name CodeArtsGateway ^
  --input build\exe-input ^
  --main-jar codearts-gateway-desktop.jar ^
  --main-class com.codearts.gateway.desktop.Main ^
  --java-options "-Djdk.net.unixdomain.tmpdir=C:\Temp" ^
  --dest dist
if errorlevel 1 exit /b 1

echo [3/3] 完成
echo.
echo [OK] 产物目录: %~dp0dist\CodeArtsGateway\
echo      可执行文件: %~dp0dist\CodeArtsGateway\CodeArtsGateway.exe
echo      整个 CodeArtsGateway 文件夹已内置运行时，可拷到任意 Windows 机器运行。
endlocal
