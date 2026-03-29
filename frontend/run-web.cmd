@echo off
setlocal

set "PATH=C:\Program Files\nodejs;%PATH%"
cd /d "%~dp0"

start "" /b cmd /c "npm.cmd run web -- --port 19006 --host localhost 1>expo-web.stdout.log 2>expo-web.stderr.log"
