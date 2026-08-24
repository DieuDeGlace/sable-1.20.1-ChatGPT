@echo off
setlocal

set "SABLE_BUILD_MODE=%~1"
if "%SABLE_BUILD_MODE%"=="" set "SABLE_BUILD_MODE=release"
if not "%~1"=="" shift

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\build.ps1" -Mode "%SABLE_BUILD_MODE%" %1 %2 %3 %4 %5 %6 %7 %8 %9
exit /b %ERRORLEVEL%
