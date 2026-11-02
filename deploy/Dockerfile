# Multi-stage Dockerfile for BitTorrent P2P File Sharing System
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy source code and config files
COPY src/ ./src/
COPY PeerProcess.java ./
COPY TrackerProcess.java ./
COPY Ssh.java ./

# Compile all Java files
RUN javac -d bin src/*.java PeerProcess.java TrackerProcess.java

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy compiled binaries
COPY --from=builder /app/bin ./bin
COPY Common.cfg ./
COPY PeerInfo.cfg ./

EXPOSE 8888 6001 6002 6003

# Default entrypoint (override command for Tracker or Peer)
ENTRYPOINT ["java", "-cp", "bin"]
CMD ["TrackerProcess", "8888"]
