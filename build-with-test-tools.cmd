@echo off
setlocal
call "%~dp0build.cmd" test %*
exit /b %ERRORLEVEL%
