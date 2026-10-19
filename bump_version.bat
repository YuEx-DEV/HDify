@echo off
title HDify - Sinkronisasi Versi (Android ^& Windows)
cd /d "%~dp0"

if "%~1"=="" (
    echo ========================================================
    echo   HDify Unified Version Sync (Android ^& Windows)
    echo ========================================================
    echo Masukkan nomor versi baru yang diinginkan (contoh: 2.2.6):
    set /p NEW_VER="Versi baru: "
) else (
    set NEW_VER=%~1
)

if "%NEW_VER%"=="" (
    echo Versi tidak boleh kosong.
    pause
    exit /b 1
)

node sync_version.js %NEW_VER%
pause
