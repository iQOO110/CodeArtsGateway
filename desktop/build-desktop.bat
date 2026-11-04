@echo off
chcp 65001 >nul
rem 构建 desktop 可运行 jar（javac + tar 合并依赖，不需要 Gradle / Android SDK）
setlocal enabledelayedexpansion
cd /d "%~dp0"

if "%JAVA_HOME%"=="" (
  echo [ERROR] 请先设置 JAVA_HOME 指向 JDK 17+（带 javac / jar）
  exit /b 1
)

set "CP=libs\androidasync-3.1.0.jar;libs\json-20240303.jar"
if exist build rmdir /s /q build
mkdir build\classes
mkdir build\fat
mkdir build\libs

rem 1) 收集源码：desktop 自身 + app 的 core（替换为正斜杠并带引号，兼容 javac 参数文件规范及空格路径）
if exist build\srcs.txt del /q build\srcs.txt
for /f "delims=" %%i in ('dir /s /b src\main\java\*.java') do (
  set "f=%%i"
  echo "!f:\=/!">> build\srcs.txt
)
for /f "delims=" %%i in ('dir /s /b ..\app\src\main\java\com\codearts\gateway\core\*.java') do (
  set "f=%%i"
  echo "!f:\=/!">> build\srcs.txt
)

rem 2) 编译
"%JAVA_HOME%\bin\javac" -nowarn -encoding UTF-8 -cp "%CP%" -d build\classes @build\srcs.txt
if errorlevel 1 exit /b 1

rem 3) 合并依赖到 classes
xcopy /e /i /y build\classes build\fat >nul
tar -xf libs\androidasync-3.1.0.jar -C build\fat
tar -xf libs\json-20240303.jar -C build\fat
del /q build\fat\META-INF\*.SF build\fat\META-INF\*.DSA build\fat\META-INF\*.RSA 2>nul

rem 4) 打包
"%JAVA_HOME%\bin\jar" --create --file build\libs\codearts-gateway-desktop.jar --main-class com.codearts.gateway.desktop.Main -C build\fat .

echo.
echo [OK] build\libs\codearts-gateway-desktop.jar
endlocal
