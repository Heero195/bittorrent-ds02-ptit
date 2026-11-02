@echo off
echo Compiling BitTorrent P2P Project...
javac -d bin src/*.java tests/*.java PeerProcess.java TrackerProcess.java Ssh.java
if %ERRORLEVEL% EQU 0 (
    echo Compilation SUCCESS! Binaries placed in bin/ directory.
) else (
    echo Compilation FAILED!
)
