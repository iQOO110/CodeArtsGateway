@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem 避免 JDK 21 Unix Domain Socket 因用户名含空格报 Pipe 连接错误
if not exist "C:\Temp" mkdir "C:\Temp"
set "TEMP=C:\Temp"
set "TMP=C:\Temp"

rem 1) 增量构建 jar
call build-desktop.bat || exit /b 1

rem 2) 直接运行
echo.
echo [RUN] 启动 CodeArts Gateway 桌面端...
if not "%JAVA_HOME%"=="" (
  "%JAVA_HOME%\bin\java.exe" -jar build\libs\codearts-gateway-desktop.jar %*
) else (
  java -jar build\libs\codearts-gateway-desktop.jar %*
)
endlocal
