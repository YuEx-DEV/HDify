@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

title HDify - Installer Otomatis APK Release ke HP Android

echo ========================================================
echo       HDIFY - AUTOMATIC APK INSTALLER (RELEASE)
echo ========================================================
echo.

:: 1. Cari lokasi adb.exe
set "ADB_BIN="

where adb >nul 2>nul
if %errorlevel% equ 0 (
    set "ADB_BIN=adb"
    goto :found_adb
)

if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_BIN=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    goto :found_adb
)

if defined ANDROID_HOME (
    if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
        set "ADB_BIN=%ANDROID_HOME%\platform-tools\adb.exe"
        goto :found_adb
    )
)

if defined ANDROID_SDK_ROOT (
    if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" (
        set "ADB_BIN=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
        goto :found_adb
    )
)

if exist "C:\Android\sdk\platform-tools\adb.exe" (
    set "ADB_BIN=C:\Android\sdk\platform-tools\adb.exe"
    goto :found_adb
)

if exist "D:\Android\sdk\platform-tools\adb.exe" (
    set "ADB_BIN=D:\Android\sdk\platform-tools\adb.exe"
    goto :found_adb
)

echo [ERROR] adb.exe tidak ditemukan di PATH maupun Android SDK standard!
pause
exit /b 1

:found_adb
echo [1/4] Menggunakan ADB: "%ADB_BIN%"
echo.

:: 2. Cek koneksi HP Android
:check_device
echo [2/4] Memeriksa koneksi perangkat HP Android...
"%ADB_BIN%" devices > "%TEMP%\adb_devices.txt"

set "DEVICE_FOUND=0"
set "DEVICE_ID="
for /f "tokens=1,2" %%A in ('type "%TEMP%\adb_devices.txt"') do (
    if "%%B"=="device" (
        set "DEVICE_FOUND=1"
        set "DEVICE_ID=%%A"
        echo  - Perangkat terdeteksi: %%A
    )
    if "%%B"=="unauthorized" (
        echo [PERINGATAN] Perangkat %%A belum diizinkan!
        echo Silakan periksa layar HP Anda dan klik "Izinkan / Allow USB Debugging".
    )
)
del "%TEMP%\adb_devices.txt" 2>nul

if "!DEVICE_FOUND!"=="1" goto :device_ready

echo.
echo [PERINGATAN] Tidak ada HP Android yang terhubung atau diizinkan!
echo.
echo Panduan Menghubungkan HP:
echo  1. Hubungkan HP ke PC menggunakan kabel data USB.
echo  2. Buka Pengaturan HP - Opsi Pengembang [Developer Options].
echo  3. Aktifkan "USB Debugging" dan "Install via USB".
echo  4. Jika muncul pop-up di layar HP, pilih "Selalu izinkan dari komputer ini" lalu tekan OK.
echo.
echo Tekan sembarang tombol untuk mencoba mendeteksi ulang HP Anda...
pause >nul
echo.
goto :check_device

:device_ready
echo.
echo [3/4] Memeriksa file APK Release...
set "APK_PATH=%~dp0app\build\outputs\apk\release\app-release-unsigned.apk"

if exist "!APK_PATH!" goto :apk_ready

echo File APK release belum ada. Memulai build APK secara otomatis...
if defined JAVA_HOME (
    echo Menggunakan JAVA_HOME: !JAVA_HOME!
) else if exist "C:\Program Files\Android\Android Studio\jbr" (
    set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    echo Menyetel JAVA_HOME: !JAVA_HOME!
) else if exist "C:\Users\YuHideng\.jdk\jdk-21.0.9+10" (
    set "JAVA_HOME=C:\Users\YuHideng\.jdk\jdk-21.0.9+10"
    echo Menyetel JAVA_HOME: !JAVA_HOME!
)

call "%~dp0gradlew.bat" assembleRelease
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Gagal mem-build APK Release. Periksa log error di atas.
    pause
    exit /b 1
)

:apk_ready
echo File APK siap: "!APK_PATH!"
echo.

:: 4. Menginstal APK ke HP
echo [4/4] Menginstal HDify (Release) ke HP Anda (!DEVICE_ID!)...
echo Sedang mentransfer dan memasang aplikasi, harap tunggu...
"%ADB_BIN%" -s !DEVICE_ID! install -r -d "!APK_PATH!"

if %errorlevel% equ 0 goto :install_success

echo.
echo [ERROR] Instalasi gagal! Catatan: APK Release unsigned mungkin ditolak oleh sistem Android jika verifikasi signature diaktifkan di HP.
echo Disarankan menggunakan install.bat (Debug APK) untuk pengujian langsung.
echo.
pause
exit /b 1

:install_success
echo.
echo ========================================================
echo          SUKSES! APLIKASI BERHASIL DIINSTAL
echo ========================================================
echo.
echo Membuka aplikasi HDify di layar HP Anda...
"%ADB_BIN%" -s !DEVICE_ID! shell am start -n com.example.hdify/com.example.hdify.ui.MainActivity >nul 2>nul

echo Aplikasi sekarang sudah terbuka di HP Anda!
echo.
pause
