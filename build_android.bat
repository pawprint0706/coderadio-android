@echo off
cd /d "%~dp0"
python scripts\gradle.py %*
exit /b %errorlevel%
