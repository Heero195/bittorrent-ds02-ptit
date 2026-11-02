# Distributed Systems Project Report Outline (Topic DS02: BitTorrent P2P)

## Table of Contents
1. **Cover Page & Group Assignment Table**
2. **Abstract**
3. **Problem Description & Distributed Systems Requirements**
4. **System Architecture & Protocol Design**
   - Tracker / Bootstrap Server Specification
   - P2P Peer Communication Protocol (Handshake, Bitfield, Request, Piece, Have, Choke/Unchoke)
   - Metadata & SHA-256 Hashing Architecture
5. **Concurrency, Synchronization, & Tit-For-Tat Algorithm**
   - Choking / Unchoking Mechanism
   - Optimistic Unchoking Strategy
   - Parallel Piece Download Scheduler
6. **Integrity, Fault Tolerance, & Failure Recovery**
   - SHA-256 Chunk Integrity Verification & Invalid Chunk Rejection Workflow
   - Peer Abrupt Disconnect Recovery
7. **Implementation Details & Folder Structure**
8. **Demonstration & Testing Results**
   - Functional Verification
   - Corrupt Chunk Detection Logs & Evidence
   - Performance & Scalability Evaluation
9. **Conclusion & Future Improvements**
10. **References & Appendices**
