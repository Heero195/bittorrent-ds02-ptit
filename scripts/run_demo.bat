@echo off
echo ====================================================
echo Running BitTorrent System Demonstration and Tests
echo ====================================================

call scripts\compile.bat

echo.
echo Running Automated Unit and Integrity Tests...
java -cp bin tests.TestIntegrity

echo.
echo Starting Tracker Server on Port 8888...
start "Tracker Server" java -cp bin TrackerProcess 8888 thefile.meta

ping 127.0.0.1 -n 3 >nul

echo.
echo Starting Seeder Peer 1001...
start "Peer 1001 (Seeder)" java -cp bin PeerProcess 1001 localhost 8888

ping 127.0.0.1 -n 3 >nul

echo.
echo Starting Leecher Peer 1002 (with Corrupt Chunk Fault Injection test)...
start "Peer 1002 (Leecher)" java -cp bin PeerProcess 1002 localhost 8888 --corrupt-chunk 2

ping 127.0.0.1 -n 3 >nul

echo.
echo Starting Leecher Peer 1003...
start "Peer 1003 (Leecher)" java -cp bin PeerProcess 1003 localhost 8888

echo.
echo Demonstration launched! Check console windows and log_peer_*.log files.
