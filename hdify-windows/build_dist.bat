@echo off
title HDify Desktop - Build Release Installer
cd /d "%~dp0"
echo ========================================================
echo   HDify Desktop - Build Release Installer ^& Portable
echo   (File setup lama akan otomatis dibersihkan)
echo ========================================================
echo.
call npm.cmd run dist
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo   BUILD SUKSES!
    echo   File installer terbaru siap di folder: dist\
    echo ========================================================
) else (
    echo.
    echo [ERROR] Build gagal. Silakan periksa pesan error di atas.
)
pause
