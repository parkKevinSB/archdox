@echo off
setlocal

set "ARCHDOX_DIST=%~dp0"
set "JAVA_HOME=%ARCHDOX_DIST%jre"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo.
echo ArchDox Agent installer
echo -----------------------
echo This will install/update the local Agent runtime and register Windows auto start.
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%ARCHDOX_DIST%windows\install-archdox-agent-task.ps1" -RunNow
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if "%EXIT_CODE%"=="0" (
  echo ArchDox Agent setup completed.
) else (
  echo ArchDox Agent setup failed with exit code %EXIT_CODE%.
)
echo.
pause
exit /b %EXIT_CODE%
