@echo off
setlocal

set "ARCHDOX_DIST=%~dp0"
set "JAVA_HOME=%ARCHDOX_DIST%jre"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo.
echo ArchDox Agent removal
echo ---------------------
echo This will remove the Windows auto start task. Local files are left in place.
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%ARCHDOX_DIST%windows\uninstall-archdox-agent-task.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
  echo ArchDox Agent auto start was removed.
) else (
  echo ArchDox Agent removal failed with exit code %EXIT_CODE%.
)
echo.
pause
exit /b %EXIT_CODE%
