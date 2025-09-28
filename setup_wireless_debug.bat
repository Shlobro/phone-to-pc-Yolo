@echo off
setlocal enabledelayedexpansion
echo ========================================
echo Android Wireless Debugging Setup
echo ========================================
echo.

:: Check if platform-tools exists
if not exist "platform-tools\adb.exe" (
    echo ERROR: platform-tools\adb.exe not found!
    echo Please make sure platform-tools is in the current directory.
    pause
    exit /b 1
)

:: Get list of connected devices
echo Scanning for connected devices...
platform-tools\adb.exe devices > temp_devices.txt

:: Count devices (excluding header and empty lines)
set device_count=0
for /f "skip=1 tokens=1,2" %%a in (temp_devices.txt) do (
    if "%%b"=="device" (
        set /a device_count+=1
    )
)

if %device_count%==0 (
    echo No devices found connected via USB.
    echo Please connect your Android device with USB debugging enabled.
    del temp_devices.txt
    pause
    exit /b 1
)

echo.
echo Found %device_count% connected device(s):
echo.

:: Display devices with numbers
set index=0
for /f "skip=1 tokens=1,2" %%a in (temp_devices.txt) do (
    if "%%b"=="device" (
        set /a index+=1
        echo [!index!] %%a
        set device!index!=%%a
    )
)

echo.
if %device_count%==1 (
    echo Only one device found. Setting up wireless debugging...
    set selected_device=!device1!
    goto setup_wireless
)

:: Ask user to select device
set /p choice="Select device number (1-%device_count%): "

:: Validate choice
if %choice% lss 1 goto invalid_choice
if %choice% gtr %device_count% goto invalid_choice

:: Get selected device
call set selected_device=%%device%choice%%%
goto setup_wireless

:invalid_choice
echo Invalid choice. Please run the script again.
del temp_devices.txt
pause
exit /b 1

:setup_wireless
echo.
echo Setting up wireless debugging for device: %selected_device%
echo.

:: Enable TCP/IP mode
echo Step 1: Enabling TCP/IP mode...
platform-tools\adb.exe -s %selected_device% tcpip 5555
if errorlevel 1 (
    echo ERROR: Failed to enable TCP/IP mode
    goto cleanup
)

:: Wait a moment for the device to restart in TCP mode
echo Waiting for device to restart in TCP mode...
timeout /t 3 /nobreak > nul

:: Get device IP address
echo.
echo Step 2: Getting device IP address...
platform-tools\adb.exe -s %selected_device% shell "ip route | grep wlan" > temp_ip.txt 2>nul

:: Extract IP address from route output
for /f "tokens=1,9 delims= " %%a in (temp_ip.txt) do (
    if "%%a"=="192.168." set device_ip=%%i
    if "%%a"=="10." set device_ip=%%i
    if "%%a"=="172." set device_ip=%%i
)

:: Fallback method - try to get IP from ifconfig
if not defined device_ip (
    echo Trying alternative method to get IP...
    platform-tools\adb.exe -s %selected_device% shell "ifconfig wlan0 | grep 'inet addr'" > temp_ip2.txt 2>nul
    for /f "tokens=2 delims=:" %%a in (temp_ip2.txt) do (
        for /f "tokens=1" %%b in ("%%a") do set device_ip=%%b
    )
)

:: Another fallback - try newer Android versions
if not defined device_ip (
    echo Trying newer Android IP method...
    platform-tools\adb.exe -s %selected_device% shell "ifconfig wlan0 | grep 'inet '" > temp_ip3.txt 2>nul
    for /f "tokens=2" %%a in (temp_ip3.txt) do set device_ip=%%a
)

if not defined device_ip (
    echo ERROR: Could not determine device IP address.
    echo Please ensure your device is connected to WiFi.
    echo You can also manually connect using: adb connect [YOUR_DEVICE_IP]:5555
    goto cleanup
)

echo Device IP found: %device_ip%

:: Connect wirelessly
echo.
echo Step 3: Connecting wirelessly to %device_ip%:5555...
timeout /t 2 /nobreak > nul
platform-tools\adb.exe connect %device_ip%:5555

:: Verify connection
echo.
echo Step 4: Verifying wireless connection...
platform-tools\adb.exe devices

echo.
echo ========================================
echo Wireless debugging setup complete!
echo ========================================
echo.
echo Device: %selected_device%
echo Wireless IP: %device_ip%:5555
echo.
echo You can now disconnect the USB cable.
echo To reconnect later, use: adb connect %device_ip%:5555
echo.

:cleanup
del temp_devices.txt 2>nul
del temp_ip.txt 2>nul
del temp_ip2.txt 2>nul
del temp_ip3.txt 2>nul

echo Press any key to exit...
pause > nul