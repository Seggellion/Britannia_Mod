@echo off
setlocal
set "BANNER_SCAFFOLD_ARGS=%*"
call "%~dp0..\gradlew.bat" scaffoldBanners "-PbannerScaffoldArgs=%BANNER_SCAFFOLD_ARGS%" --no-daemon
exit /b %ERRORLEVEL%
