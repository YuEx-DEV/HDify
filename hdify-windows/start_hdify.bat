@echo off
title HDify Desktop Launcher
cd /d "%~dp0"
echo ========================================================
echo   HDify - Ultra HD Engine (Native Windows Edition)
echo   Accelerated with Vulkan NCNN & FFmpeg
echo ========================================================
echo Memulai HDify Desktop...
call npm.cmd start
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Gagal menjalankan aplikasi. Pastikan Node.js terpasang.
    pause
)
