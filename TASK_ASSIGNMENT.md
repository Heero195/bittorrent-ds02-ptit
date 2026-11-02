# BẢNG PHÂN CÔNG NHIỆM VỤ ĐỒ ÁN HỆ PHÂN TÁN
## Đề tài DS02: Hệ thống chia sẻ file ngang hàng mô phỏng BitTorrent (P2P File-Sharing System Simulating BitTorrent)

---

## 👥 THÔNG TIN NHÓM

* **Tên nhóm / Mã nhóm**: Nhóm ...
* **Lớp học phần**: Hệ phân tán (Distributed Systems)
* **Giảng viên hướng dẫn**: ...
* **Kho lưu trữ (Repository)**: `https://github.com/...`
* **Phiên bản phát hành (Release tag)**: `v1.0.0`

---

## 📊 BẢNG TỔNG HỢP PHÂN CÔNG NHIỆM VỤ (TASK ASSIGNMENT TABLE)

| STT | Họ và tên | Mã số sinh viên (MSSV) | GitHub Username | Vai trò chính | Tỷ lệ đóng góp |
| :---: | :--- | :---: | :---: | :--- | :---: |
| **1** | [Họ tên Thành viên 1] | [MSSV 1] | `@github_user_1` | **Trưởng nhóm** - Kiến trúc Tracker, Metadata & Môi trường Triển khai | **33.3%** |
| **2** | [Họ tên Thành viên 2] | [MSSV 2] | `@github_user_2` | **Thành viên** - Giao thức P2P, Động cơ truyền tải & Thuật toán Choking (Tit-for-Tat) | **33.3%** |
| **3** | [Họ tên Thành viên 3] | [MSSV 3] | `@github_user_3` | **Thành viên** - Khả năng Chịu lỗi (Fault Tolerance), Kiểm thử & Đánh giá Hiệu năng | **33.3%** |

---

## 🛠️ CHI TIẾT TRÁCH NHIỆM VÀ PHẠM VI MÃ NGUỒN CỦA TỪNG THÀNH VIÊN

### 👤 Thành viên 1: Kiến trúc Tracker, Metadata & Môi trường Triển khai
* **Mã nguồn và thành phần phụ trách**:
  - `TrackerProcess.java`: Điểm khởi chạy tiến trình Tracker Server độc lập.
  - `src/TrackerServer.java`: Bộ lắng nghe Socket TCP tiếp nhận kết nối đồng thời từ các peer.
  - `src/TrackerAdmin.java`: Quản lý danh sách các peer trong mạng (Peer Swarm), trạng thái mảnh dữ liệu (BitSet), loại bỏ peer không hoạt động (peer timeout / churn).
  - `src/Metadata.java`: Mô hình cấu trúc dữ liệu metadata chứa File ID, kích thước file, số chunk, danh sách mã băm SHA-256 từng chunk, mã băm toàn bộ file và URL của Tracker.
  - `src/MetadataGenerator.java`: Công cụ dòng lệnh (CLI) chia file thành các chunk và sinh file `.meta`.
  - `deploy/Dockerfile` & `deploy/docker-compose.yml`: Cấu hình Docker để khởi chạy 1 Tracker và 3 Peers trên mạng ảo cô lập.
  - `.env.example`: Cấu hình các biến môi trường mẫu không chứa thông tin bí mật.
* **Trách nhiệm trong báo cáo (`docs/report.pdf`)**:
  - Viết Mục 1: Trang bìa, thông tin nhóm và bảng phân công nhiệm vụ.
  - Viết Mục 4, 6: Mô tả bài toán, phân tích và lựa chọn giải pháp kiến trúc (Tracker-based P2P).
  - Viết Mục 7, 8: Thiết kế kiến trúc hệ thống và giao thức liên lạc với Tracker.
  - Viết Mục 9: Cơ chế định danh và phát hiện nút mạng (Peer Discovery qua Tracker).
* **Nội dung thuyết minh & vấn đáp (3–5 phút đầu)**:
  - Giới thiệu bài toán, kiến trúc phân tán tổng thể.
  - Giải thích nguyên lý hoạt động của Tracker: chỉ quản lý metadata và danh sách peer, **tuyệt đối không cung cấp nội dung file** (đáp ứng đúng Mục 6.1 và Mục 12).
  - Trình bày định dạng file `.meta` và cách đóng gói mã băm SHA-256.

---

### 👤 Thành viên 2: Giao thức P2P, Động cơ truyền tải & Thuật toán Choking (Tit-for-Tat)
* **Mã nguồn và thành phần phụ trách**:
  - `PeerProcess.java`: Điểm khởi chạy tiến trình Peer độc lập.
  - `src/PeerAdmin.java`: Quản trị viên tiến trình peer, quản lý luồng socket, truy xuất file ngẫu nhiên (`RandomAccessFile`), và quản lý trạng thái tải mảnh dữ liệu.
  - `src/PeerServer.java`: ServerSocket lắng nghe kết nối đến từ các peer khác.
  - `src/PeerHandler.java`: Luồng xử lý giao tiếp song song giữa các peer, vòng lặp phân tích và gửi nhận gói tin BitTorrent.
  - `src/HandshakeMessage.java`: Xây dựng và kiểm tra gói tin bắt tay P2P (32 bytes Handshake Header).
  - `src/ActualMessage.java`: Xây dựng và bóc tách các loại thông điệp BitTorrent: `choke (0)`, `unchoke (1)`, `interested (2)`, `not interested (3)`, `have (4)`, `bitfield (5)`, `request (6)`, `piece (7)`.
  - `src/ChokeHandler.java`: Thuật toán lựa chọn $k$ láng giềng ưu tiên (Preferred Neighbors) theo chu kỳ định kỳ dựa trên tốc độ tải về (Tit-for-Tat).
  - `src/OptimisticUnchokeHandler.java`: Thuật toán mở nghẽn lạc quan (Optimistic Unchoke) chọn ngẫu nhiên 1 láng giềng bị nghẽn để tìm đường truyền tốt hơn.
* **Trách nhiệm trong báo cáo (`docs/report.pdf`)**:
  - Viết Mục 8: Thiết kế chi tiết giao thức truyền tin P2P và cấu trúc thông điệp.
  - Viết Mục 10: Xử lý đồng thời (Concurrency), đồng bộ hóa luồng tải/ghi file, và tính nhất quán dữ liệu.
  - Viết Mục 13: Chi tiết cài đặt thuật toán Choking/Unchoking (Tit-for-Tat).
* **Nội dung thuyết minh & vấn đáp**:
  - Giải thích quy trình bắt tay (Handshake) và trao đổi `bitfield` giữa hai peer.
  - Phân tích cơ chế giải quyết xung đột khi nhiều peer cùng tải/cung cấp các chunk (`synchronized`, `ConcurrentHashMap`).
  - Giải thích thuật toán Tit-for-Tat và cách tính toán download rate để unchoke láng giềng.

---

### 👤 Thành viên 3: Khả năng Chịu lỗi (Fault Tolerance), Kiểm thử & Đánh giá Hiệu năng
* **Mã nguồn và thành phần phụ trách**:
  - Logic phát hiện và xử lý lỗi tại `src/PeerHandler.java`:
    - Tính mã băm SHA-256 của chunk vừa nhận được, đối soát với hash trong Metadata.
    - Từ chối lưu chunk hỏng (Reject invalid/corrupt chunk), ghi log cảnh báo lỗi `[FAULT_TOLERANCE]`.
    - Đặt lại trạng thái mảnh và tự động gửi yêu cầu xin lại từ một peer khác.
  - `src/PeerProcess.java`: Cài đặt cờ tiêm lỗi `--corrupt-chunk <index>` để phục vụ kịch bản demo trực tiếp.
  - `src/PeerAdmin.java`: Cài đặt phương thức `verifyFullFileIntegrity()` đối soát mã băm SHA-256 toàn bộ file sau khi hoàn tất tải.
  - `src/PeerLogger.java`: Hệ thống ghi log chi tiết có timestamp, mã nút, loại sự kiện cho từng peer.
  - `tests/TestIntegrity.java`: Bộ kiểm thử tự động kiểm tra tính toán SHA-256, phát hiện chunk hỏng, và tính nhất quán metadata.
  - `scripts/run_demo.bat` & `scripts/run_demo.sh`: Kịch bản tự động hóa khởi động Tracker và 3 Peers kèm tiêm lỗi chunk.
* **Trách nhiệm trong báo cáo (`docs/report.pdf`)**:
  - Viết Mục 11: Mô hình chịu lỗi và khôi phục sự cố (Fault Tolerance & Recovery).
  - Viết Mục 12: An toàn dữ liệu cơ bản (Basic Security & Data Integrity via SHA-256).
  - Viết Mục 14: Kịch bản kiểm thử và kết quả thực nghiệm (Testing & Results theo Mục 9 của đề tài).
  - Viết Mục 15: Đánh giá hiệu năng và khả năng mở rộng (Performance Evaluation: Throughput, Download Time).
* **Nội dung thuyết minh & vấn đáp (Người chạy Demo trực tiếp)**:
  - Trực tiếp chạy kịch bản Demo trực tiếp (Mục 11): Khởi động Tracker và 3 peer, kích hoạt tải file song song.
  - Trình diễn kịch bản tiêm lỗi chunk hỏng và chỉ rõ trên log cách hệ thống phát hiện, từ chối và tải lại thành công.
  - Báo cáo số liệu thực nghiệm kiểm thử và thời gian hoàn tất tải file.

---

## 📌 QUY ĐỊNH ĐÓNG GÓP GITHUB & PHIÊN BẢN (ACADEMIC INTEGRITY)

1. **Commit History**:
   - Mỗi thành viên sử dụng đúng tài khoản GitHub của mình khi commit code và tài liệu.
   - Tránh việc 1 thành viên đại diện commit toàn bộ kho mã nguồn (vi phạm điều kiện đánh giá tại Mục 12).
2. **Kịch bản kiểm thử (Mục 9)**:
   - Cả nhóm phối hợp chạy đủ các ca kiểm thử: 8 functional tests, 2 concurrency tests, 2 fault tolerance tests, 2 security tests và 1 bài đo hiệu năng.
3. **Thuyết minh & Vấn đáp (Mục 11)**:
   - Cả 3 thành viên đều phải tham gia thuyết minh và trả lời vấn đáp trực tiếp với giảng viên.
