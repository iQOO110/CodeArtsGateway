@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist "C:\Temp" mkdir "C:\Temp"
set "TEMP=C:\Temp"
set "TMP=C:\Temp"
if exist "dist\CodeArtsGateway\CodeArtsGateway.exe" (
  start "" "dist\CodeArtsGateway\CodeArtsGateway.exe" %*
) else (
  call run-dev.bat %*
)
