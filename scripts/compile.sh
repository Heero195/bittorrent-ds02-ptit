#!/bin/bash
echo "Compiling BitTorrent P2P Project..."
mkdir -p bin
javac -d bin src/*.java tests/*.java PeerProcess.java TrackerProcess.java Ssh.java
if [ $? -eq 0 ]; then
    echo "Compilation SUCCESS! Binaries placed in bin/ directory."
else
    echo "Compilation FAILED!"
fi
