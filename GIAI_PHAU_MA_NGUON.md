# 🔬 BẢN GIẢI PHẪU CHI TIẾT MÃ NGUỒN DỰ ÁN BITTORRENT P2P
## ĐỀ TÀI DS02: HỆ THỐNG CHIA SẺ TỆP NGANG HÀNG MÔ PHỎNG BITTORRENT (JAVA 17)
### Phân chia theo đúng 3 Thành viên trong TASK_ASSIGNMENT.md

---

# 👤 PHẦN 1: THÀNH VIÊN 1 — KIẾN TRÚC TRACKER, METADATA & CONTAINER DOCKER

Thành viên 1 phụ trách **"Hạ tầng điều phối và Siêu dữ liệu"**: Quản lý danh bạ Swarm tập trung (không truyền file), cấu trúc siêu dữ liệu băm SHA-256 từng mảnh và đóng gói mạng ảo Docker Compose.

---

### 1. `TrackerProcess.java` — Khởi động Server Tracker độc lập
* **Mục đích:** Là điểm khởi chạy (Main Entry Point) của tiến trình Tracker Server.
* **Chi tiết từng dòng code:**
  * `Dòng 8 - 15`: Đọc cổng từ tham số dòng lệnh thứ nhất (`args[0]`). Nếu không truyền tham số, mặc định lắng nghe trên cổng `8888`.
  * `Dòng 17`: `TrackerAdmin trackerAdmin = new TrackerAdmin(60000);` ➔ Khởi tạo bộ nhớ quản trị Swarm với thời gian timeout cho mỗi peer là 60 giây (60.000 ms).
  * `Dòng 19 - 33`: Đọc đường dẫn file `.meta` từ tham số thứ hai (`args[1]`, ví dụ `thefile.meta`), gọi hàm `Metadata.loadFromFile(metaFile)` để nạp cấu trúc siêu dữ liệu vào bộ nhớ của Tracker.
  * `Dòng 35 - 37`: Khởi tạo đối tượng `TrackerServer` và bật luồng lắng nghe nền:
    ```java
    TrackerServer server = new TrackerServer(port, trackerAdmin);
    Thread trackerThread = new Thread(server);
    trackerThread.start();
    ```

---

### 2. `src/TrackerServer.java` — Bộ lắng nghe Socket TCP & Điều phối bản tin
* **Mục đích:** Mở cổng TCP `ServerSocket(8888)`, đón các kết nối đồng thời từ các Peer và xử lý các bản tin điều phối qua giao thức hướng đối tượng Java Serialization.
* **Chi tiết từng dòng code:**
  * `Dòng 32`: `serverSocket = new ServerSocket(port);` ➔ Mở socket lắng nghe tại cổng 8888.
  * `Dòng 37 - 38`: 
    ```java
    Socket clientSocket = serverSocket.accept();
    new Thread(new ClientHandler(clientSocket, trackerAdmin)).start();
    ```
    ➔ **Ý nghĩa phân tán:** Áp dụng mô hình **Multi-threaded Server**. Mỗi kết nối từ một Peer được bàn giao ngay cho một luồng `ClientHandler` độc lập để xử lý, giúp Tracker không bị nghẽn (Non-blocking I/O).
  * `Dòng 61 - 123` (Bên trong luồng `ClientHandler`):
    * `Dòng 67 - 80 (REGISTER)`: Peer mới vào mạng gửi IP, Port, cờ `isSeeder`, và `BitSet` (các mảnh hiện có). Tracker lưu vào Swarm và trả về `SUCCESS`, đối tượng `Metadata` và danh sách các peer đang hoạt động (`activePeers`).
    * `Dòng 81 - 89 (ANNOUNCE)`: Peer định kỳ gửi lên để cập nhật danh sách các mảnh mới tải được (Heartbeat).
    * `Dòng 90 - 97 (GET_PEERS)`: Trả về danh sách `PeerRecord` các nút đang online cho peer yêu cầu.
    * `Dòng 98 - 105 (PUBLISH_METADATA)`: Cho phép peer công bố một metadata file mới vào Tracker.
    * `Dòng 106 - 112 (LEAVE)`: Peer ngắt kết nối an toàn, Tracker lập tức xóa peer khỏi Swarm.
  * ⚠️ **Ý nghĩa sống còn:** Toàn bộ file này **tuyệt đối không có bất kỳ dòng lệnh nào đọc hay ghi file tệp tin**. Tracker chỉ truyền các thông điệp điều phối và siêu dữ liệu (đáp ứng đúng Mục 6.1 và tránh điểm liệt Mục 12 trong đề bài).

---

### 3. `src/TrackerAdmin.java` — Quản trị viên Swarm & Xử lý Churn (Timeout)
* **Mục đích:** Là cơ sở dữ liệu in-memory lưu trữ danh sách các nút đang tham gia mạng và xử lý biến động mạng (Peer Churn).
* **Chi tiết từng dòng code:**
  * `Dòng 9 - 46` (Lớp nội tại `PeerRecord`): Chứa định danh và trạng thái của một nút:
    * `peerId`: Mã định danh (ví dụ "1001").
    * `host`, `port`: Địa chỉ IP và cổng lắng nghe P2P của nút đó.
    * `bitSet`: Mảng bit thể hiện các mảnh mà nút đó đang sở hữu.
    * `isSeeder`: Đánh dấu nút đã có đủ file hay chưa.
    * `lastSeen`: Lưu mốc thời gian nhận tin cuối cùng (`System.currentTimeMillis()`).
  * `Dòng 48`: `private final Map<String, PeerRecord> peerMap = new ConcurrentHashMap<>();`
    ➔ **Ý nghĩa phân tán:** Dùng `ConcurrentHashMap` để đảm bảo **Thread-safety** (an toàn đa luồng) khi hàng trăm luồng `ClientHandler` cùng lúc đọc/ghi vào danh bạ mà không gây xung đột dữ liệu (Race Condition).
  * `Dòng 104 - 114` (Hàm `purgeStalePeers()`):
    ```java
    long now = System.currentTimeMillis();
    Iterator<Map.Entry<String, PeerRecord>> it = peerMap.entrySet().iterator();
    while (it.hasNext()) {
        Map.Entry<String, PeerRecord> entry = it.next();
        if (now - entry.getValue().getLastSeen() > timeoutMillis) {
            System.out.println("[TRACKER] Removing timed-out peer: " + entry.getKey());
            it.remove(); // Tự động loại bỏ nút offline sau 60s
        }
    }
    ```
    ➔ **Ý nghĩa phân tán:** Giải quyết bài toán **Peer Churn**. Nếu một máy bị sập nguồn đột ngột không kịp gửi lệnh `LEAVE`, Tracker sẽ tự động quét và xóa nút đó sau 60 giây, tránh cung cấp địa chỉ "chết" cho các peer khác.

---

### 4. `src/TrackerClient.java` — Thư viện phía Peer gọi Tracker
* **Mục đích:** Được nhúng vào phía Peer để chủ động mở Socket kết nối lên Tracker Server.
* **Chi tiết từng dòng code:**
  * `Dòng 31 - 53` (Hàm `register(...)`): Mở socket `new Socket(trackerHost, trackerPort)`, gửi chuỗi `"REGISTER"` cùng thông tin của mình và nhận về `RegistrationResult` chứa `Metadata` và danh sách Peer.
  * `Dòng 55 - 73` (Hàm `announce(...)`): Gửi chuỗi `"ANNOUNCE"` để báo cáo trạng thái bitset mới.
  * `Dòng 93 - 109` (Hàm `leave(...)`): Gửi chuỗi `"LEAVE"` khi tải xong hoặc dừng chương trình.

---

### 5. `src/Metadata.java` & `src/MetadataGenerator.java` — Băm SHA-256 từng mảnh
* **Mục đích:** Mô hình hóa file `.meta` (tương đương file `.torrent`) và công cụ CLI để sinh file `.meta`.
* **Trong `Metadata.java`:**
  * Định nghĩa cấu trúc siêu dữ liệu gồm: `fileId` (UUID), `fileName`, `fileSize`, `pieceSize` (32KB), `pieceCount`, `pieceHashes` (List mã băm SHA-256 từng mảnh), `fileHash` (SHA-256 toàn bộ file gốc), `trackerUrl`.
  * `Dòng 68 - 84` (`calculateSHA256(byte[] data)`): Dùng `MessageDigest.getInstance("SHA-256")` băm mảng byte thành 32 bytes nhị phân, sau đó dùng vòng lặp chuyển thành chuỗi Hexadecimal 64 ký tự chuẩn.
* **Trong `MetadataGenerator.java`:**
  * `Dòng 27 - 43`: Đọc file nguồn qua luồng `FileInputStream` với buffer 32KB (`pieceSize`). Đọc đến đâu băm SHA-256 mảnh đó đến đấy và thêm vào mảng `pieceHashes`.
  * `Dòng 45 - 51`: Băm toàn bộ file gốc để lấy `fileHashHex`.
  * `Dòng 74 - 95`: Hàm `main` hỗ trợ dòng lệnh: `java -cp bin src.MetadataGenerator <file> <pieceSize> <trackerUrl> <metaOutput>`.

---

### 6. `deploy/Dockerfile` & `deploy/docker-compose.yml` — Đóng gói Mạng ảo
* **Trong `Dockerfile`:** Áp dụng kỹ thuật **Multi-stage build**:
  * `Stage 1 (builder)`: Dùng image `eclipse-temurin:17-jdk-alpine`, chạy lệnh `javac -d bin src/*.java PeerProcess.java TrackerProcess.java` để biên dịch mã nguồn.
  * `Stage 2`: Dùng image `eclipse-temurin:17-jre-alpine` siêu nhẹ, chỉ copy thư mục `bin` và file cấu hình vào, giúp giảm dung lượng container xuống mức tối thiểu.
* **Trong `docker-compose.yml`:**
  * Dựng mạng ảo `p2p-net` với subnet `172.20.0.0/16`.
  * Gán IP tĩnh cho từng container: Tracker (`172.20.0.2:8888`), Peer 1001 (`172.20.0.11`), Peer 1002 (`172.20.0.12`), Peer 1003 (`172.20.0.13`).
  * Thuộc tính `depends_on`: Đảm bảo Tracker và Seeder khởi động trước khi Leecher chạy.

---

# 👤 PHẦN 2: THÀNH VIÊN 2 — GIAO THỨC P2P, ĐỘNG CƠ TRUYỀN TẢI & THUẬT TOÁN CHOKING

Thành viên 2 phụ trách **"Trái tim truyền tải dữ liệu P2P"**: Bắt tay giữa các nút, đóng gói giao thức nhị phân qua TCP, đọc/ghi file ngẫu nhiên đa luồng qua `RandomAccessFile`, và luật chơi công bằng **Tit-for-Tat Choking**.

---

### 1. `src/HandshakeMessage.java` — Gói tin Bắt tay (32 Bytes)
* **Mục đích:** Hai peer khi kết nối socket với nhau việc đầu tiên là phải gửi thông điệp bắt tay để nhận diện.
* **Cấu trúc 32 bytes nhị phân cố định:**
  * 18 bytes đầu: Tiêu đề chuỗi `"P2PFILESHARINGPROJ"`.
  * 10 bytes tiếp theo: Chuỗi bit 0 (Zero Bits padding).
  * 4 bytes cuối: `peerID` của người gửi (ví dụ `"1001"`).
* **Các dòng code cốt lõi:**
  * `Dòng 20 - 31` (`buildHandShakeMessage()`): Dùng `ByteArrayOutputStream` ghép 3 thành phần trên thành mảng 32 bytes gửi qua socket.
  * `Dòng 33 - 36` (`readHandShakeMessage()`): Nhận 32 bytes, đọc substring từ byte 28 đến 32 để trích xuất `peerID` đối phương.

---

### 2. `src/ActualMessage.java` — Đóng gói 8 loại thông điệp BitTorrent
* **Mục đích:** Đóng gói và bóc tách các gói tin trao đổi dữ liệu qua TCP theo chuẩn phân khung (Message Framing) để chống dính gói / vỡ gói.
* **Cấu trúc gói tin 5 bytes tiêu đề:**
  * 4 bytes đầu: `messageLength` (Độ dài toàn bộ phần sau).
  * 1 byte tiếp theo: `messageType` (Ký tự quy định loại thông điệp từ `'0'` đến `'7'`).
  * $N$ bytes còn lại: `messagePayload` (Tải trọng dữ liệu thực tế).
* **Các dòng code cốt lõi:**
  * `Dòng 30 - 41` (`buildActualMessage()`): Dùng `ByteBuffer.allocate(4).putInt(this.messageLength)` ghi 4 bytes Big-Endian, ghép với 1 byte kiểu tin và mảng payload.
  * `Dòng 68 - 72` (`getBitFieldMessage()`): Chuyển mảng payload thành đối tượng `BitSet` để kiểm tra bản đồ mảnh của đối phương.
  * `Dòng 74 - 76` (`getPieceIndexFromPayload()`): Trích xuất 4 bytes đầu của payload để lấy số thứ tự mảnh (`pieceIndex`).
  * `Dòng 78 - 85` (`getPieceFromPayload()`): Trích xuất phần dữ liệu nhị phân thực tế của mảnh (loại bỏ 4 bytes chỉ số ở đầu).

---

### 3. `src/PeerServer.java` — Server Socket lắng nghe của từng Peer
* **Mục đích:** Mỗi Peer vừa là Client vừa là Server. Lớp này mở `ServerSocket` lắng nghe các Peer khác kết nối vào.
* **Các dòng code cốt lõi:**
  * `Dòng 29 - 45`: Vòng lặp `while (!this.dead)`. Lệnh `Socket neighbour = this.listener.accept();` đón kết nối TCP từ peer lân cận.
  * `Dòng 33 - 34`: Khởi tạo đối tượng `PeerHandler` và bọc vào một luồng riêng:
    ```java
    PeerHandler neighbourHandler = new PeerHandler(neighbour, this.peerAdmin);
    new Thread(neighbourHandler).start();
    ```

---

### 4. `src/PeerHandler.java` (Phần Engine) — Cỗ máy điều phối thông điệp P2P
* **Mục đích:** Chạy trên một luồng riêng để liên tục trao đổi thông điệp với một Peer cụ thể qua Socket TCP.
* **Vòng lặp lắng nghe (`run()` dòng 90 - 227):**
  * Nhận 32 bytes bắt tay qua `processHandShakeMessage(handshakeMessage)`.
  * Gửi mảng `Bitfield` của mình cho bạn (`sendBitField()`).
  * Vòng lặp liên tục đọc 4 bytes độ dài và 1 byte loại thông điệp:
    * `Case '0' (Choke)`: Đối phương khóa mình, mình không được gửi request nữa.
    * `Case '1' (Unchoke)`: Đối phương mở khóa! Mình tìm xem đối phương có mảnh nào mình cần không (`checkForRequested()`), nếu có thì gửi ngay lệnh `sendRequestMessage(requestindex)`.
    * `Case '2' (Interested)`: Đối phương quan tâm mảnh của mình, đưa đối phương vào danh sách quan tâm (`interestedList`).
    * `Case '3' (Not Interested)`: Đối phương không cần mảnh nào từ mình nữa.
    * `Case '4' (Have)`: Đối phương vừa tải xong 1 mảnh mới, mình cập nhật lại `BitSet` của đối phương. Nếu mảnh đó mình chưa có, mình gửi thông điệp `INTERESTED`!
    * `Case '5' (Bitfield)`: Cập nhật toàn bộ các mảnh đối phương đang có.
    * `Case '6' (Request)`: Đối phương yêu cầu tải mảnh $X$. Nếu đối phương đang được mở khóa (`Unchoked`), mình gọi `peerAdmin.readFromFile(pieceIndex)` đọc từ đĩa và gửi thông điệp `PIECE` cho đối phương.

---

### 5. `src/PeerAdmin.java` (Phần Engine) — Quản lý trạng thái & Đọc ghi đĩa ngẫu nhiên
* **Mục đích:** "Bộ não" của một máy Peer (quản lý mảnh nào đã có, mảnh nào đang chờ tải, đọc ghi file đĩa).
* **Các dòng code cốt lõi:**
  * `Dòng 160`: `this.fileRaf = new RandomAccessFile(file, "rw");` ➔ Mở file đĩa ở chế độ đọc/ghi ngẫu nhiên.
  * `Dòng 162`: `this.fileRaf.setLength(this.getFileSize());` ➔ Cấp phát trước toàn bộ dung lượng file rỗng trên đĩa để chống phân mảnh file.
  * `Dòng 236 - 244` (`writeToFile(...)`): Dùng `fileRaf.seek(position)` nhảy tới vị trí `pieceSize * pieceindex` và ghi mảng byte xuống đĩa. Có `synchronized` để bảo vệ đa luồng.
  * `Dòng 246 - 263` (`readFromFile(...)`): Nhảy tới vị trí mảnh cần đọc, tính toán chính xác kích thước mảnh cuối cùng và dùng `fileRaf.readFully(data)` nạp dữ liệu lên RAM để gửi đi.
  * `Dòng 336 - 355` (`checkForRequested(...)`): Thuật toán chọn mảnh: So sánh `BitSet` của mình với `BitSet` của bạn, tìm mảnh nào bạn có mà mình chưa có và chưa có ai khác đang tải dở thì chọn mảnh đó để gửi yêu cầu.

---

### 6. `src/ChokeHandler.java` — Thuật toán Tit-for-Tat Choking
* **Mục đích:** Chống hành vi "tải chùa" (Free-riding), khuyến khích các nút chia sẻ băng thông.
* **Các dòng code cốt lõi:**
  * `Dòng 31 - 33`: Khởi động bộ lập lịch định kỳ: Cứ sau mỗi `interval` (mặc định 5 giây) hàm `run()` lại được tự động kích hoạt.
  * `Dòng 64 - 67`: Lấy danh sách tốc độ tải về (`downloadRate`) từ tất cả các peer, dùng Stream API sắp xếp giảm dần theo tốc độ:
    ```java
    Map<String, Integer> rates = downloads.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        ...
    ```
  * `Dòng 74 - 88`: Lấy ra $k$ peer tải nhanh nhất cho mình (`preferredNeighboursCount`, mặc định 2 nút). Gửi `sendUnChokedMessage()` mở khóa cho 2 bạn này.
  * Các peer còn lại không nằm trong top 2 sẽ bị gửi `sendChokedMessage()` khóa lại, không cho tải nữa cho đến chu kỳ 5 giây tiếp theo!

---

### 7. `src/OptimisticUnchokeHandler.java` — Mở khóa Lạc quan
* **Mục đích:** Khám phá các đường truyền tiềm năng tốt hơn và giúp nút mới hòa nhập mạng.
* **Các dòng code cốt lõi:**
  * `Dòng 24`: Chạy định kỳ mỗi 15 giây (`OptimisticUnchokingInterval`).
  * `Dòng 27 - 45`: Chọn ngẫu nhiên (`rand.nextInt()`) 1 peer trong danh sách những peer đang bị khóa nhưng có quan tâm (`interestedList`). Gửi `sendUnChokedMessage()` mở khóa cho bạn đó.

---

# 👤 PHẦN 3: THÀNH VIÊN 3 — CHỊU LỖI (FAULT TOLERANCE), KIỂM THỬ & GHI NHẬT KÝ

Thành viên 3 phụ trách **"Độ tin cậy và Đảm bảo chất lượng hệ thống"**: Phát hiện và xử lý dữ liệu bị hỏng mã băm, xử lý khi máy khác bị sập nguồn, ghi log chi tiết từng giây và viết bộ kiểm thử tự động.

---

### 1. `src/PeerHandler.java` (Phần Chịu lỗi) — Bắt lỗi Mảnh hỏng SHA-256
* **Mục đích:** Kiểm tra tính toàn vẹn từng mảnh dữ liệu trước khi cho phép ghi xuống đĩa cứng.
* **Các dòng code cốt lõi:**
  * `Dòng 154 - 163` (Kịch bản giả lập tiêm lỗi `--corrupt-chunk`):
    ```java
    if (this.peerAdmin.getCorruptChunkIndex() == pieceIndex) {
        byte[] corruptPiece = rawPiece.clone();
        corruptPiece[0] = (byte) (~corruptPiece[0]); // Đảo bit đầu tiên làm sai mã hash
        this.sendPieceMessage(pieceIndex, corruptPiece);
    }
    ```
    ➔ Phía gửi cố tình làm hỏng dữ liệu để phục vụ kịch bản demo cho giảng viên.
  * `Dòng 171 - 192` (Phía nhận phát hiện và xử lý phục hồi):
    ```java
    String actualHash = Metadata.calculateSHA256(pieceData);
    if (!actualHash.equalsIgnoreCase(expectedHash)) {
        // 1. In cảnh báo ra màn hình và file log
        this.peerAdmin.getLogger().logCorruptChunkDetected(this.endPeerID, pieceIndex);
        
        // 2. Hủy trạng thái yêu cầu của mảnh đó
        this.peerAdmin.resetSingleRequestedPiece(pieceIndex);
        
        // 3. Tự động gửi request tải lại mảnh đó từ peer khác
        int retryIndex = this.peerAdmin.checkForRequested(this.endPeerID);
        this.sendRequestMessage(retryIndex);
    }
    ```
    ➔ **Ý nghĩa phân tán xuất sắc:** Mảnh bị sai hash sẽ bị vứt bỏ ngay trên RAM, **không bao giờ ghi vào đĩa**, và lập tức yêu cầu tải lại.

---

### 2. Xử lý sự cố mạng (Peer Crash / Disconnect Resilience)
* **File:** `src/PeerHandler.java` và `src/PeerAdmin.java`
* **Các dòng code cốt lõi:**
  * Trong `PeerHandler.java` (dòng 228 - 238):
    ```java
    catch (SocketException | EOFException e) {
        if (endPeerID != null) {
            this.peerAdmin.removeJoinedPeer(endPeerID); // Dọn dẹp kết nối
        }
    }
    ```
    ➔ Bắt ngoại lệ khi peer hàng xóm bị ngắt kết nối đột ngột (tắt máy/rút dây mạng) mà không làm chương trình bị sập (Crash-resilient).
  * Trong `PeerAdmin.java` (dòng 357 - 363):
    ```java
    public synchronized void resetRequested(String endpeerid) {
        for (int i = 0; i < this.requestedInfo.length; i++) {
            if (this.requestedInfo[i] != null && this.requestedInfo[i].compareTo(endpeerid) == 0) {
                setRequestedInfo(i, null); // Trả lại mảnh về hàng đợi
            }
        }
    }
    ```
    ➔ Những mảnh nào đang giao cho peer bị chết tải dở sẽ được thu hồi, chuyển về trạng thái `null` để các luồng kết nối tới các peer khác tiếp tục nhận nhiệm vụ tải.

---

### 3. `src/PeerAdmin.java` (Dòng 475 - 503) — Kiểm tra toàn vẹn cả file
* **Hàm `verifyFullFileIntegrity()`:**
  * Được kích hoạt khi đã tải xong đủ 31 mảnh.
  * Mở luồng đọc toàn bộ file `thefile`, tính mã băm SHA-256 của toàn bộ file và so sánh với `metadata.getFileHash()`.
  * Nếu trùng khớp, ghi log:
    `[INTEGRITY_VERIFICATION] Full file SHA-256 integrity check PASSED!`

---

### 4. `src/PeerLogger.java` — Hệ thống ghi nhật ký chuẩn sự kiện
* **Mục đích:** Ghi nhận mọi sự kiện phân tán ra các file `log_peer_[ID].log` với nhãn thời gian chuẩn `dd-MMM-yyyy hh:mm:ss a`.
* **Các hàm quan trọng:**
  * `genTCPConnLogSender` / `Receiver`: Ghi log khi kết nối TCP được thiết lập.
  * `changePreferredNeigbors`: Ghi log khi danh sách Preferred Neighbors (Tit-for-Tat) thay đổi.
  * `changeOptimisticallyUnchokedNeighbor`: Ghi log nút được mở khóa lạc quan.
  * `downloadPiece`: Ghi log khi tải xong 1 mảnh, kèm số lượng mảnh hiện có.
  * `logCorruptChunkDetected`: Ghi cảnh báo khi phát hiện mảnh sai hash SHA-256.
  * `logFileHashVerified`: Ghi kết quả kiểm tra toàn vẹn toàn file cuối cùng.

---

### 5. `tests/TestIntegrity.java` — Bộ kiểm thử đơn vị tự động
* **Mục đích:** Chạy độc lập để kiểm chứng thuật toán mật mã trước khi chạy mạng.
* **Gồm 3 bài test:**
  * `Test 1 (Dòng 20 - 34)`: Kiểm thử hàm `calculateSHA256` sinh đúng chuỗi Hex 64 ký tự và tính toán nhất quán.
  * `Test 2 (Dòng 35 - 51)`: Tạo 2 mảng byte chỉ lệch nhau đúng 1 byte cuối (`{..., 8}` và `{..., 9}`). So sánh để chứng minh mã băm sinh ra hoàn toàn khác nhau (hiệu ứng tuyết lở), đảm bảo phát hiện được dữ liệu lỗi.
  * `Test 3 (Dòng 53 - 81)`: Tạo file mẫu, sinh metadata, lưu ra đĩa rồi nạp lại vào bộ nhớ để kiểm tra tính toàn vẹn của việc tuần tự hóa (Serialization).

---

### 6. `scripts/compile.bat` & `scripts/run_demo.bat`
* **Mục đích:** Tự động hóa quá trình đóng gói và khởi chạy demo trên Windows.
* Trong `run_demo.bat`: Tự gọi `compile.bat`, chạy `tests.TestIntegrity`, sau đó dùng lệnh `start` của Windows để mở đồng thời 4 cửa sổ console độc lập (1 Tracker + 3 Peers) có trễ `ping 127.0.0.1 -n 3` để đảm bảo thứ tự khởi động.

