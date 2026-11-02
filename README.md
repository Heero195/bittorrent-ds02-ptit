# BitTorrent P2P File Sharing System (Topic DS02)

Implementation of a Peer-to-Peer (P2P) file sharing system simulating BitTorrent for the **Distributed Systems** course.

---

## 🌟 Key Features

1. **Centralized Tracker / Bootstrap Server**:
   - Manages dynamic peer registration, online peer states, and file metadata distribution.
   - Automatically purges inactive or timed-out peers.
2. **Metadata & SHA-256 Hash Verification**:
   - `MetadataGenerator` CLI splits target files into chunks and generates SHA-256 digests for each chunk and for the whole file.
   - Peers verify SHA-256 digests upon receiving each downloaded piece.
3. **Fault Tolerance & Corrupt Chunk Recovery**:
   - Detects invalid/corrupted chunks using SHA-256 verification.
   - Automatically rejects bad chunks, resets piece state, and re-requests missing chunks from alternative peers.
   - Supports `--corrupt-chunk <index>` fault injection flag for demonstration testing.
4. **Choking / Unchoking Mechanism (Tit-for-Tat)**:
   - Periodically selects preferred neighbors based on download rates.
   - Optimistically unchokes random neighbors to discover better transfer paths.
5. **Parallel Download & Bitfield Management**:
   - Concurrent piece requesting and uploading across multiple independent peer processes.

---

## 📁 Repository Structure

```
PeerToPeer-FileSharing/
├── README.md                  # Project overview, architecture, and deployment guide
├── TASK_ASSIGNMENT.md         # Detailed group member task assignments & responsibilities
├── .env.example               # Sample environment variables configuration
├── TrackerProcess.java        # Main entry point for Tracker server
├── PeerProcess.java           # Main entry point for Peer process
├── Ssh.java                   # Remote deployment wrapper script
├── Common.cfg                 # Fallback common configuration
├── PeerInfo.cfg               # Fallback peer network configuration
├── src/                       # Source code package
│   ├── Metadata.java          # File metadata and SHA-256 hashing methods
│   ├── MetadataGenerator.java # CLI tool to generate metadata from files
│   ├── TrackerAdmin.java      # Tracker in-memory registry & peer management
│   ├── TrackerServer.java     # Tracker TCP socket server handler
│   ├── TrackerClient.java     # Peer client helper for Tracker communication
│   ├── PeerAdmin.java         # Core Peer administrator & state manager
│   ├── PeerHandler.java       # Peer-to-peer TCP connection handler & message loop
│   ├── PeerServer.java        # Peer socket listener thread
│   ├── PeerLogger.java        # Structured logger for all peer events
│   ├── ActualMessage.java     # BitTorrent protocol message builder & parser
│   ├── HandshakeMessage.java  # P2P handshake message implementation
│   ├── ChokeHandler.java      # Preferred neighbors selection scheduler
│   └── OptimisticUnchokeHandler.java # Optimistic unchoke scheduler
├── deploy/                    # Container & deployment configuration
│   ├── Dockerfile             # Multi-stage Java container dockerfile
│   └── docker-compose.yml     # Isolated network setup (1 Tracker + 3 Peers)
├── tests/                     # Test suite
│   └── TestIntegrity.java     # Unit tests for SHA-256 calculation & chunk rejection
├── scripts/                   # Build and demonstration scripts
│   ├── compile.bat / .sh      # Compilation scripts
│   └── run_demo.bat / .sh     # Automated demonstration launchers
└── docs/                      # Documentation & reports
    └── report_template.md     # Project report structure outline
```

---

## 🚀 Quick Start & Build Instructions

### Prerequisites
- **JDK 11+** or **JDK 17+**
- (Optional) **Docker** & **Docker Compose**

### 1. Compile the Codebase

**On Windows (PowerShell / CMD):**
```cmd
scripts\compile.bat
```

**On Linux / macOS:**
```bash
chmod +x scripts/compile.sh
./scripts/compile.sh
```

---

## 🧪 Running Unit & Integrity Tests

Run the test suite to verify SHA-256 chunk hashing, metadata serialization, and invalid chunk rejection:

```cmd
java -cp bin tests.TestIntegrity
```

---

## 💻 Manual Execution Walkthrough

### Step 1: Generate Metadata for a Shared File
To generate a `.meta` file with SHA-256 chunk hashes for a shared file (e.g. `thefile`):

```cmd
java -cp bin src.MetadataGenerator peer_1001/thefile 32768 http://localhost:8888 thefile.meta
```

### Step 2: Start the Tracker Server
Start the central Tracker process listening on port 8888:

```cmd
java -cp bin TrackerProcess 8888 thefile.meta
```

### Step 3: Start Seeder & Leecher Peers
- **Start Seeder (Peer 1001)**:
```cmd
java -cp bin PeerProcess 1001 localhost 8888
```

- **Start Leecher (Peer 1002)**:
```cmd
java -cp bin PeerProcess 1002 localhost 8888
```

- **Start Leecher with Fault Injection (Simulating Corrupt Chunk #2)**:
```cmd
java -cp bin PeerProcess 1002 localhost 8888 --corrupt-chunk 2
```

---

## 🐳 Docker Container Deployment

Deploy 1 Tracker and 3 Peers inside an isolated container network:

```bash
cd deploy
docker-compose up --build
```

To stop containers:
```bash
docker-compose down
```

---

## 📊 Verification & Logs

All peer activity, connection events, choke/unchoke decisions, downloaded pieces, and corrupt chunk warnings are logged to:
- `log_peer_1001.log`
- `log_peer_1002.log`
- `log_peer_1003.log`

### Log Examples:
- **Corrupt Chunk Detection**:
  `[FAULT_TOLERANCE] Peer [1002] detected INVALID/CORRUPT piece [2] from [1001]. Hash mismatch! Piece rejected.`
- **Integrity Verification Success**:
  `[INTEGRITY_VERIFICATION] Full file SHA-256 integrity check PASSED! Hash: 7a8f9b...`

---

## 👥 Task Assignment & Team Contributions

For detailed member roles, source code ownership, report chapters, and demonstration oral presentation duties, see:
👉 [TASK_ASSIGNMENT.md](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/TASK_ASSIGNMENT.md)

| Member | Core Module | Contribution |
| :--- | :--- | :---: |
| **Member 1 (Lead)** | Tracker Server, Metadata Generator & Docker Deployment | 33.3% |
| **Member 2** | P2P Protocol Engine, BitTorrent Messaging & Tit-for-Tat Choking | 33.3% |
| **Member 3** | Fault Tolerance, Corrupt Chunk Recovery, Logging & Test Suite | 33.3% |

