@echo off
setlocal enabledelayedexpansion

echo ========================================
echo Quick Wireless Debug Setup
echo ========================================
echo.

:: Check if platform-tools exists
if not exist "platform-tools\adb.exe" (
    echo ERROR: platform-tools\adb.exe not found!
    pause
    exit /b 1
)

:: Show connected devices
echo Current connected devices:
platform-tools\adb.exe devices
echo.

:: Get first USB connected device (excluding wireless and emulators)
for /f "skip=1 tokens=1,2" %%a in ('platform-tools\adb.exe devices') do (
    if "%%b"=="device" (
        echo %%a | findstr /v ":" | findstr /v "emulator" > nul
        if !errorlevel!==0 (
            set usb_device=%%a
            goto found_device
        )
    )
)

echo No USB devices found for wireless setup.
pause
exit /b 1

:found_device
echo Setting up wireless debugging for: !usb_device!
echo.

:: Enable TCP/IP mode
echo Enabling TCP/IP mode...
platform-tools\adb.exe -s !usb_device! tcpip 5555

:: Wait for restart
echo Waiting 3 seconds for device restart...
timeout /t 3 /nobreak > nul

:: Get IP using a more reliable method
echo Getting device IP address...
platform-tools\adb.exe -s !usb_device! shell "ip route | head -1" > temp_route.txt 2>nul

:: Extract IP from route (src IP is device IP)
for /f "tokens=9" %%a in (temp_route.txt) do (
    set device_ip=%%a
    goto got_ip
)

:got_ip
if not defined device_ip (
    echo Could not auto-detect IP. Please check manually with:
    echo adb shell ip route
    goto cleanup
)

echo Device IP: !device_ip!

:: Connect wirelessly
echo Connecting wirelessly...
timeout /t 1 /nobreak > nul
platform-tools\adb.exe connect !device_ip!:5555

echo.
echo Final device list:
platform-tools\adb.exe devices

echo.
echo ========================================
echo Setup complete!
echo You can now disconnect USB cable.
echo Wireless IP: !device_ip!:5555
echo ========================================

:cleanup
del temp_route.txt 2>nul
pause