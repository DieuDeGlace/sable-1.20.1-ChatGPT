@echo off
setlocal
call "%~dp0build.cmd" release %*
exit /b %ERRORLEVEL%
