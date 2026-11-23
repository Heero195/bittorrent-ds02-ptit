@echo off
echo ====================================================
echo Starting BitTorrent Live Web Dashboard...
echo ====================================================

cd /d "%~dp0"
call scripts\compile.bat

echo.
echo Starting Web Dashboard on http://localhost:8080 ...
start "" http://localhost:8080
java -cp bin src.WebDashboard

