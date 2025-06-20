@echo off
setlocal enabledelayedexpansion

:: Configuration
set "EXECUTABLE=migrator.exe"
set "CONFIG_FILE="
set "INTERVAL=3600"
set "SERVICE_NAME=MigrationDaemon"
set "LOG_FILE=%~dp0migration-daemon.log"
set "MAX_LOG_SIZE=10485760"

:log_message
echo %date% %time% - %~1 >> "%LOG_FILE%"
echo %date% %time% - %~1
goto :eof

:rotate_log
if exist "%LOG_FILE%" (
    for %%F in ("%LOG_FILE%") do (
        if %%~zF gtr %MAX_LOG_SIZE% (
            move "%LOG_FILE%" "%LOG_FILE%.old" >nul 2>&1
            call :log_message "Log rotated"
        )
    )
)
goto :eof

:is_running
tasklist /FI "IMAGENAME eq %~1" 2>nul | find /I "%~1" >nul 2>&1
goto :eof

:start_daemon
call :log_message "Starting migration daemon (Interval: %INTERVAL%s)"

:daemon_loop
call :rotate_log
call :log_message "Starting migrate-all command..."

:: Build command with optional config
if defined CONFIG_FILE (
    set "CMD=%EXECUTABLE% --config %CONFIG_FILE% migrate-all"
) else (
    set "CMD=%EXECUTABLE% migrate-all"
)

:: Execute the migration command
!CMD! >> "%LOG_FILE%" 2>&1
if !errorlevel! equ 0 (
    call :log_message "migrate-all completed successfully"
) else (
    call :log_message "migrate-all failed with exit code !errorlevel!"
)

call :log_message "Sleeping for %INTERVAL% seconds..."
timeout /t %INTERVAL% /nobreak >nul 2>&1

goto daemon_loop

:install_service
call :log_message "Installing migration daemon as Windows service..."
sc create "%SERVICE_NAME%" binPath= "\"%~f0\" service" start= auto
if !errorlevel! equ 0 (
    echo Service installed successfully
    sc start "%SERVICE_NAME%"
) else (
    echo Failed to install service
)
goto :eof

:uninstall_service
call :log_message "Uninstalling migration daemon service..."
sc stop "%SERVICE_NAME%" >nul 2>&1
sc delete "%SERVICE_NAME%"
if !errorlevel! equ 0 (
    echo Service uninstalled successfully
) else (
    echo Failed to uninstall service
)
goto :eof

:status_service
sc query "%SERVICE_NAME%" >nul 2>&1
if !errorlevel! equ 0 (
    sc query "%SERVICE_NAME%"
) else (
    echo Service is not installed
)
goto :eof

:usage
echo Usage: %~nx0 [options] {start^|install^|uninstall^|status}
echo.
echo Options:
echo   -e, --executable PATH    Path to migrator executable (default: migrator.exe)
echo   -c, --config PATH        Config file path
echo   -i, --interval SECONDS   Interval between runs (default: 3600)
echo   -l, --log-file PATH      Log file path (default: migration-daemon.log)
echo   -h, --help              Show this help
echo.
echo Commands:
echo   start       Start the daemon in current console
echo   install     Install as Windows service
echo   uninstall   Uninstall Windows service
echo   status      Show service status
echo   service     Internal command for service execution
goto :eof

:parse_args
if "%~1"=="" goto :eof
if /I "%~1"=="-e" set "EXECUTABLE=%~2" && shift && shift && goto parse_args
if /I "%~1"=="--executable" set "EXECUTABLE=%~2" && shift && shift && goto parse_args
if /I "%~1"=="-c" set "CONFIG_FILE=%~2" && shift && shift && goto parse_args
if /I "%~1"=="--config" set "CONFIG_FILE=%~2" && shift && shift && goto parse_args
if /I "%~1"=="-i" set "INTERVAL=%~2" && shift && shift && goto parse_args
if /I "%~1"=="--interval" set "INTERVAL=%~2" && shift && shift && goto parse_args
if /I "%~1"=="-l" set "LOG_FILE=%~2" && shift && shift && goto parse_args
if /I "%~1"=="--log-file" set "LOG_FILE=%~2" && shift && shift && goto parse_args
if /I "%~1"=="-h" call :usage && exit /b 0
if /I "%~1"=="--help" call :usage && exit /b 0
if /I "%~1"=="start" set "COMMAND=start" && shift && goto parse_args
if /I "%~1"=="install" set "COMMAND=install" && shift && goto parse_args
if /I "%~1"=="uninstall" set "COMMAND=uninstall" && shift && goto parse_args
if /I "%~1"=="status" set "COMMAND=status" && shift && goto parse_args
if /I "%~1"=="service" set "COMMAND=service" && shift && goto parse_args
echo Unknown option: %~1
call :usage
exit /b 1

:: Main execution
call :parse_args %*

:: Check if executable exists
if not exist "%EXECUTABLE%" (
    where "%EXECUTABLE%" >nul 2>&1
    if !errorlevel! neq 0 (
        echo Error: Executable '%EXECUTABLE%' not found
        exit /b 1
    )
)

:: Execute command
if /I "%COMMAND%"=="start" (
    call :start_daemon
) else if /I "%COMMAND%"=="install" (
    call :install_service
) else if /I "%COMMAND%"=="uninstall" (
    call :uninstall_service
) else if /I "%COMMAND%"=="status" (
    call :status_service
) else if /I "%COMMAND%"=="service" (
    call :start_daemon
) else (
    echo Error: No command specified
    call :usage
    exit /b 1
)
