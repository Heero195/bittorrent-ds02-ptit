#!/bin/bash
echo "===================================================="
echo " Running BitTorrent System Demonstration & Tests"
echo "===================================================="

chmod +x scripts/compile.sh
./scripts/compile.sh

echo ""
echo "Running Automated Unit & Integrity Tests..."
java -cp bin tests.TestIntegrity

echo ""
echo "Starting Tracker Server on Port 8888..."
java -cp bin TrackerProcess 8888 &
TRACKER_PID=$!

sleep 2

echo ""
echo "Starting Seeder Peer 1001..."
java -cp bin PeerProcess 1001 localhost 8888 &
P1001_PID=$!

sleep 2

echo ""
echo "Starting Leecher Peer 1002 (with Corrupt Chunk Fault Injection)..."
java -cp bin PeerProcess 1002 localhost 8888 --corrupt-chunk 2 &
P1002_PID=$!

sleep 2

echo ""
echo "Starting Leecher Peer 1003..."
java -cp bin PeerProcess 1003 localhost 8888 &
P1003_PID=$!

echo ""
echo "Demonstration processes running in background. PIDs: Tracker=$TRACKER_PID, P1001=$P1001_PID, P1002=$P1002_PID, P1003=$P1003_PID"
