# 🎓 CẨM NANG HƯỚNG DẪN THUYẾT TRÌNH VÀ VẤN ĐÁP ĐỒ ÁN
## ĐỀ TÀI DS02: HỆ THỐNG CHIA SẺ TỆP NGANG HÀNG MÔ PHỎNG BITTORRENT (JAVA P2P)
### Học phần: Hệ thống phân tán (Distributed Systems) - PTIT

---

## ⏱️ PHẦN 1: PHÂN BỔ THỜI GIAN CHUẨN BUỔI BẢO VỆ (15 - 20 PHÚT)

Hội đồng chấm thi thường chia buổi đánh giá thành 3 giai đoạn:
1. **3 – 5 phút đầu:** Giới thiệu bài toán, kiến trúc hệ thống và phân công vai trò (Thành viên 1 dẫn dắt).
2. **7 – 10 phút giữa:** Trình diễn Live Demo trực tiếp kết hợp giải thích luồng dữ liệu (Cả 3 thành viên phối hợp).
3. **5 phút cuối:** Thầy cô đặt câu hỏi vấn đáp riêng cho từng thành viên để chấm điểm cá nhân.

---

## 👥 PHẦN 2: KỊCH BẢN CHI TIẾT TỪNG THÀNH VIÊN

---

### 👤 THÀNH VIÊN 1: TRƯỞNG NHÓM (KIẾN TRÚC TRACKER, METADATA & CONTAINER DOCKER)

#### 🎯 Nhiệm vụ chính trong buổi bảo vệ:
* Mở đầu tự tin, giới thiệu tổng quan đề tài và phân công nhiệm vụ.
* Giải thích nguyên lý Tracker Server (khẳng định không truyền file) và cấu trúc Metadata SHA-256.
* Trình diễn triển khai Docker và giải thích cấu hình mạng ảo.

#### 🎙️ Lời thoại mẫu khi mở đầu (3 phút):
> *"Kính thưa quý Thầy/Cô trong Hội đồng, hôm nay nhóm em xin phép được báo cáo bài tập lớn môn Hệ thống phân tán với Đề tài DS02: Xây dựng hệ thống chia sẻ tệp ngang hàng mô phỏng giao thức BitTorrent bằng ngôn ngữ Java 17.*
>
> *Hệ thống của nhóm em giải quyết triệt để nhược điểm nghẽn băng thông tải lên $O(N)$ và điểm lỗi đơn lẻ (SPOF) của mô hình Client-Server truyền thống bằng cách chia tệp thành các chunk 32KB, băm mã mật mã học SHA-256 cho từng mảnh và điều phối chia sẻ dữ liệu trực tiếp giữa các nút ngang hàng.*
>
> *Nhóm em gồm 3 thành viên, mỗi bạn đảm nhận độc lập một mảng kiến trúc cốt lõi:*
> - *Em (Thành viên 1) phụ trách Kiến trúc Tracker Server, Siêu dữ liệu Metadata và Đóng gói Container Docker.*
> - *Bạn [Tên TV2] phụ trách Giao thức P2P, Phân khung thông điệp nhị phân và Thuật toán Choking Tit-for-Tat.*
> - *Bạn [Tên TV3] phụ trách Khả năng Chịu lỗi (Fault Tolerance), Xử lý biến động mạng và Bộ kiểm thử tự động.*
>
> *Sau đây, em xin phép trình bày về thành phần Tracker Server và Siêu dữ liệu Metadata."*

#### 📂 Các file mã nguồn phụ trách cần mở sẵn trên IDE:
1. [`TrackerProcess.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/TrackerProcess.java): Khởi chạy Tracker Server ở cổng 8888.
2. [`src/TrackerServer.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/TrackerServer.java): Socket TCP đa luồng tiếp nhận `REGISTER`, `ANNOUNCE`, `GET_PEERS`, `LEAVE`.
3. [`src/TrackerAdmin.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/TrackerAdmin.java): Quản lý Swarm bằng `ConcurrentHashMap` và hàm `purgeStalePeers()` (dòng 104–114) dọn dẹp peer rớt mạng sau 60s.
4. [`src/Metadata.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/Metadata.java) & [`src/MetadataGenerator.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/MetadataGenerator.java): Cắt chunk 32KB và băm SHA-256.
5. [`deploy/Dockerfile`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/deploy/Dockerfile) & [`deploy/docker-compose.yml`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/deploy/docker-compose.yml): Cấu hình mạng ảo `172.20.0.0/16`.

#### ❓ Bộ câu hỏi vấn đáp thầy hay hỏi TV1 & Cách trả lời:
* **Hỏi:** *"Tracker của em có gửi dữ liệu tệp tin cho máy tải không?"*
  * **Đáp:** *"Dạ tuyệt đối KHÔNG ạ! Thầy có thể quan sát trong file `TrackerServer.java` (dòng 65–115), Tracker chỉ xử lý bản tin điều phối danh bạ và gửi Metadata. Tracker không hề có luồng đọc hay chứa dữ liệu tệp. Dữ liệu tệp được truyền $100\%$ P2P giữa các máy với nhau, đáp ứng đúng Mục 6.1 và tránh điểm liệt Mục 12."*
* **Hỏi:** *"File `.meta` để làm gì, sao không cho tải trực tiếp luôn?"*
  * **Đáp:** *"Dạ thưa thầy, trong mạng P2P ta tải từ nhiều người lạ cùng lúc. File `.meta` đóng vai trò như 'khuôn mẫu và đáp án': Nó cho máy tải biết file nặng bao nhiêu, có bao nhiêu mảnh, và quan trọng nhất là chứa mảng băm SHA-256 của từng mảnh (`pieceHashes`). Nhờ đó, khi nhận mảnh nào từ mạng về, máy tải băm lại để đối chiếu ngay, nếu phát hiện dữ liệu rác hay bị sửa đổi thì hủy ngay lập tức mà không sợ hỏng cả file."*
* **Hỏi:** *"Nếu Tracker bị sập thì hệ thống có chạy tiếp được không?"*
  * **Đáp:** *"Dạ thưa thầy, những Peer đã kết nối với nhau từ trước thì vẫn tiếp tục tải và chia sẻ chéo cho nhau bình thường vì socket P2P kết nối trực tiếp. Tuy nhiên các Peer mới sẽ không thể gia nhập mạng được nữa vì mất điểm hẹn ban đầu (Rendezvous Point). Đây chính là nhược điểm Single Point of Failure của kiến trúc Hybrid P2P, hướng phát triển tiếp theo của nhóm em là thay thế Tracker bằng Bảng băm phân tán DHT Kademlia."*

---

### 👤 THÀNH VIÊN 2: GIAO THỨC P2P, ĐỘNG CƠ TRUYỀN TẢI & THUẬT TOÁN CHOKING

#### 🎯 Nhiệm vụ chính trong buổi bảo vệ:
* Giải thích cách 2 máy tính kết nối và nói chuyện qua giao thức nhị phân BitTorrent.
* Giải thích cách đọc/ghi file ngẫu nhiên đồng thời bằng `RandomAccessFile`.
* Thuyết minh thuật toán điều phối nghẽn và luật chơi công bằng **Tit-for-Tat Choking** & **Optimistic Unchoking**.

#### 🎙️ Lời thoại mẫu khi tiếp quản phần thuyết trình (3 phút):
> *"Tiếp nối phần kiến trúc Tracker, em xin phép trình bày về Động cơ truyền tải P2P và Giao thức phân khung gói tin giữa các nút mạng.*
>
> *Khi hai Peer tìm thấy nhau qua Tracker, chúng sẽ thiết lập một kết nối TCP Socket trực tiếp. Để đảm bảo không bị hiện tượng dính gói hoặc vỡ gói trên dòng byte liên tục của TCP, nhóm em thiết kế giao thức phân khung chặt chẽ gồm 2 loại thông điệp:*
> 1. *Thông điệp Bắt tay (`HandshakeMessage`) cố định 32 bytes để xác thực định danh hai bên.*
> 2. *Thông điệp Thực thi (`ActualMessage`) với tiêu đề 5 bytes: 4 bytes độ dài Big-Endian + 1 byte mã loại thông điệp từ '0' đến '7'.*
>
> *Bên cạnh đó, để ngăn chặn hiện tượng 'tải chùa' (Free-riders) chỉ tải mà không chia sẻ, em đã cài đặt thuật toán **Tit-for-Tat** trong `ChokeHandler.java` định kỳ đo download rate để ưu tiên mở khóa cho các nút tích cực nhất."*

#### 📂 Các file mã nguồn phụ trách cần mở sẵn trên IDE:
1. [`src/HandshakeMessage.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/HandshakeMessage.java): Đóng gói và giải mã gói tin bắt tay 32 bytes.
2. [`src/ActualMessage.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/ActualMessage.java): Định nghĩa và đóng gói 8 loại thông điệp BitTorrent (`CHOKE`, `UNCHOKE`, `INTERESTED`, `NOT_INTERESTED`, `HAVE`, `BITFIELD`, `REQUEST`, `PIECE`).
3. [`src/PeerServer.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/PeerServer.java): ServerSocket lắng nghe kết nối đến của từng Peer.
4. [`src/PeerHandler.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/PeerHandler.java) (Dòng 90–225): Vòng lặp state machine tiếp nhận và phản hồi các thông điệp.
5. [`src/PeerAdmin.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/PeerAdmin.java) (Dòng 236–263): Đọc ghi file ngẫu nhiên đa luồng qua `RandomAccessFile`.
6. [`src/ChokeHandler.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/ChokeHandler.java): Thuật toán Tit-for-Tat Choking định kỳ 5s.
7. [`src/OptimisticUnchokeHandler.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/OptimisticUnchokeHandler.java): Mở khóa lạc quan định kỳ 15s.

#### ❓ Bộ câu hỏi vấn đáp thầy hay hỏi TV2 & Cách trả lời:
* **Hỏi:** *"Tại sao em lại dùng `RandomAccessFile` mà không dùng `FileOutputStream` thông thường?"*
  * **Đáp:** *"Dạ thưa thầy, trong mạng P2P các mảnh dữ liệu được tải về song song và KHÔNG THEO THỨ TỰ (ví dụ máy em nhận mảnh số 5 trước mảnh số 1). Nếu dùng `FileOutputStream` thì chỉ ghi được tuần tự từ đầu đến cuối. `RandomAccessFile` cho phép em dùng lệnh `seek(position)` nhảy thẳng tới vị trí offset tương ứng trên đĩa để ghi ngay mảnh đó xuống mà không cần chờ đợi, đồng thời nhiều luồng có thể đọc/ghi độc lập vào các vị trí khác nhau của cùng một tệp."*
* **Hỏi:** *"Thuật toán Tit-for-Tat của em hoạt động như thế nào?"*
  * **Đáp:** *"Dạ trong file `ChokeHandler.java` (dòng 31–88), cứ mỗi 5 giây hệ thống sẽ tính số byte/số mảnh đã nhận được từ mỗi peer láng giềng. Em dùng Stream API sắp xếp tốc độ tải về giảm dần và chọn ra $k$ peer tải nhanh nhất cho mình (mặc định $k=2$) để gửi thông điệp `UNCHOKE` cho phép họ tiếp tục tải từ em. Các peer lười chia sẻ hoặc tải chậm sẽ bị gửi `CHOKE` tạm khóa lại. Đây là luật chơi sòng phẳng 'Ăn miếng trả miếng'."*
* **Hỏi:** *"Vậy một nút mới vào mạng chưa có mảnh nào thì làm sao được ai unchoke?"*
  * **Đáp:** *"Dạ chính vì thế giao thức BitTorrent mới cần thêm thuật toán **Optimistic Unchoke** (`OptimisticUnchokeHandler.java`). Cứ mỗi 15 giây, nút sẽ chọn ngẫu nhiên 1 peer đang bị khóa để mở khóa cho họ tải thử. Nhờ đó nút mới sẽ có được những mảnh đầu tiên để bắt đầu tham gia trao đổi với mạng Swarm."*

---

### 👤 THÀNH VIÊN 3: KHẢ NĂNG CHỊU LỖI, GHI NHẬT KÝ & BỘ KIỂM THỬ

#### 🎯 Nhiệm vụ chính trong buổi bảo vệ:
* Thuyết minh cơ chế Chịu lỗi (Fault Tolerance) 2 cấp độ: Tầng dữ liệu (mảnh lỗi SHA-256) và Tầng mạng (nút sập nguồn).
* Trình diễn trực tiếp kịch bản tiêm lỗi `--corrupt-chunk`.
* Chứng minh tính toàn vẹn 100% bằng kiểm tra mã băm SHA-256 toàn file và trích xuất nhật ký `log_peer_*.log`.

#### 🎙️ Lời thoại mẫu khi tiếp quản phần thuyết trình (3 phút):
> *"Tiếp theo, em xin phép trình bày về Khả năng Chịu lỗi (Fault Tolerance), Hệ thống ghi nhật ký chuẩn và Nghiệm thu toàn vẹn của đồ án.*
>
> *Một hệ thống phân tán thực tế luôn phải đối mặt với hai nguy cơ lớn: Dữ liệu bị sai lệch trên đường truyền và Các nút mạng bị ngắt kết nối đột ngột. Nhóm em đã giải quyết trọn vẹn cả hai vấn đề này:*
> 1. *Ở tầng dữ liệu: Mọi khối dữ liệu nhận về đều được tính băm SHA-256 tức thời và đối chiếu với metadata. Nếu phát hiện sai lệch, mảnh lỗi bị hủy bỏ ngay trên RAM, không ghi vào đĩa và tự động tái yêu cầu từ nguồn khác.*
> 2. *Ở tầng kết nối: Khi một peer bị sập nguồn đột ngột, ngoại lệ `SocketException` được bắt an toàn, giải phóng các mảnh đang tải dở về hàng đợi mà không làm ứng dụng bị crash.*
>
> *Sau đây, em xin phép thực hiện Live Demo kịch bản tiêm lỗi trực tiếp để Thầy/Cô kiểm chứng."*

#### 📂 Các file mã nguồn phụ trách cần mở sẵn trên IDE:
1. [`src/PeerHandler.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/PeerHandler.java) (Dòng 154–192): Cơ chế tiêm lỗi `--corrupt-chunk`, băm SHA-256, từ chối mảnh lỗi và tải lại.
2. [`src/PeerHandler.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/PeerHandler.java) (Dòng 228–238) & [`src/PeerAdmin.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/PeerAdmin.java) (Dòng 357–363): Bắt `SocketException` và hàm `resetRequested()` thu hồi mảnh dở dang.
3. [`src/PeerAdmin.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/PeerAdmin.java) (Dòng 475–503): Hàm `verifyFullFileIntegrity()` kiểm tra mã băm toàn file cuối cùng.
4. [`src/PeerLogger.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/src/PeerLogger.java): Hệ thống ghi log chi tiết có timestamp.
5. [`tests/TestIntegrity.java`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/tests/TestIntegrity.java): 3 Unit test tự động kiểm tra SHA-256.
6. [`scripts/compile.bat`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/scripts/compile.bat) & [`scripts/run_demo.bat`](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/scripts/run_demo.bat): Script tự động hóa.

#### ❓ Bộ câu hỏi vấn đáp thầy hay hỏi TV3 & Cách trả lời:
* **Hỏi:** *"Em làm thế nào để chứng minh hệ thống phát hiện được mảnh dữ liệu bị hỏng?"*
  * **Đáp:** *"Dạ thưa thầy, trong file `PeerProcess.java` nhóm em có hỗ trợ tham số dòng lệnh `--corrupt-chunk <index>`. Khi bật cờ này (ví dụ `--corrupt-chunk 2`), phía gửi trong `PeerHandler.java` (dòng 158) sẽ cố tình đảo ngược bit đầu tiên của mảnh 2. Khi máy nhận lấy dữ liệu về, nó tính lại SHA-256 thấy khác với mã băm trong metadata, lập tức in ra dòng cảnh báo đỏ `[FAULT_TOLERANCE] Piece rejected` và tự động gửi request xin lại mảnh lành lặn khác. Em xin phép bấm chạy trực tiếp cho thầy xem ạ!"*
* **Hỏi:** *"Nếu máy gửi bị sập nguồn giữa chừng thì máy nhận có bị đơ (treo) không?"*
  * **Đáp:** *"Dạ không ạ! Trong `PeerHandler.java` (dòng 228) em đã bao bọc vòng lặp đọc socket trong khối `try-catch (SocketException | EOFException)`. Khi bên kia sập nguồn, kết nối TCP bị ngắt, khối catch sẽ kích hoạt gọi `removeJoinedPeer()` để đóng socket an toàn. Đồng thời hàm `resetRequested()` trong `PeerAdmin.java` (dòng 357) sẽ rà soát lại mảng phân công, mảnh nào đang đòi từ nút chết sẽ được trả về trạng thái chưa yêu cầu (`null`) để các luồng kết nối với các peer khác tiếp tục nhận nhiệm vụ tải, tiến trình không hề bị gián đoạn."*
* **Hỏi:** *"Làm sao thầy biết file sau khi ghép lại không bị sai dù chỉ 1 byte?"*
  * **Đáp:** *"Dạ sau khi gom đủ 31 mảnh, hàm `verifyFullFileIntegrity()` sẽ băm SHA-256 toàn bộ file trên đĩa để so sánh với `fileHash` gốc trong metadata. Ngoài ra, em có thể dùng ngay lệnh PowerShell `Get-FileHash` để so sánh mã băm của file gốc và các file tải về trước mặt thầy, hai mã băm trùng khớp 100% từng ký tự hex."*

---

## 🎬 PHẦN 3: KỊCH BẢN PHỐI HỢP LIVE DEMO TẠI CHỖ (7 PHÚT)

Để tránh lúng túng ai làm gì, 3 bạn hãy phối hợp nhịp nhàng theo đúng 4 bước sau:

```
[BƯỚC 1: DỌN DẸP] ---> [BƯỚC 2: CHẠY DEMO] ---> [BƯỚC 3: GIẢI THÍCH LOG] ---> [BƯỚC 4: NGHIỆM THU HASH]
  (TV1 thao tác)         (TV2 thao tác)             (Cả 3 cùng chỉ)             (TV3 chốt hạ)
```

### 🔹 Bước 1: Thành viên 1 mở màn & Dọn dẹp môi trường (1 phút)
1. TV1 mở Terminal gõ:
   ```powershell
   cd d:\htpt\bitorrent_prj\PeerToPeer-FileSharing
   Remove-Item peer_1002\thefile, peer_1003\thefile -ErrorAction SilentlyContinue
   ```
2. Mở thư mục `peer_1002` và `peer_1003` cho thầy thấy: **Hiện tại chưa có file nào.**
3. TV1 chạy Unit test:
   ```powershell
   java -cp bin tests.TestIntegrity
   ```
   Chỉ cho thầy: **3/3 Passed!**

### 🔹 Bước 2: Thành viên 2 khởi chạy Demo (2 phút)
1. TV2 gõ lệnh chạy tự động:
   ```cmd
   scripts\run_demo.bat
   ```
2. TV2 chỉ tay lên màn hình khi 4 cửa sổ bật lên:
   * *"Cửa sổ 1 là Tracker Server cổng 8888."*
   * *"Cửa sổ 2 là Seeder 1001 mang file gốc."*
   * *"Cửa sổ 3 là Leecher 1002 đang tải về và có bật cờ `--corrupt-chunk 2`."*
   * *"Cửa sổ 4 là Leecher 1003 tải song song từ cả 1001 và 1002."*
3. Đợi 5 giây cho các peer tải xong toàn bộ 31 mảnh.

### 🔹 Bước 3: Cả nhóm cùng giải thích Log minh chứng (2 phút)
1. Mở file [log_peer_1002.log](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/log_peer_1002.log) và [log_peer_1003.log](file:///d:/htpt/bitorrent_prj/PeerToPeer-FileSharing/log_peer_1003.log):
   * **TV2 chỉ:** Dòng `Peer [1002] received 'have' message...` và thuật toán Tit-for-Tat `preferred neighbors [1003, 1002]`.
   * **TV3 chỉ:** Dòng bắt lỗi tiêm lỗi chunk 2 và dòng kết luận hoàn tất:
     `[INTEGRITY_VERIFICATION] Full file SHA-256 integrity check PASSED!`

### 🔹 Bước 4: Thành viên 3 chốt hạ nghiệm thu (2 phút)
1. TV3 mở Terminal gõ lệnh tắt tiến trình để giải phóng file:
   ```powershell
   Stop-Process -Name java -Force
   ```
2. TV3 chạy lệnh kiểm tra mã băm SHA-256 của cả 3 file:
   ```powershell
   Get-FileHash peer_1001\thefile, peer_1002\thefile, peer_1003\thefile
   ```
3. **TV3 dõng dạc kết luận:**
   > *"Kính thưa Thầy/Cô, kết quả lệnh `Get-FileHash` cho thấy cả 3 file ở 3 thư mục độc lập đều có mã băm SHA-256 trùng khớp tuyệt đối là `D29751F2649B32FF572B5E0A9F541EA660A50F94FF0BEEDFB0B692B924CC8025`. Điều này khẳng định 1.000.000 bytes dữ liệu đã được truyền tải qua Socket mạng, vượt qua bài kiểm tra lỗi bit và tái tạo chính xác nguyên bản 100%. Nhóm em xin kết thúc phần trình bày và sẵn sàng lắng nghe câu hỏi từ Thầy/Cô ạ!"*

---

## 🏆 BÍ QUYẾT TÂM LÝ ĐẠT ĐIỂM TỐI ĐA (A+)

1. **Không tranh lời nhau:** Khi thầy cô chỉ định ai, người đó chủ động trả lời. Nếu đồng đội gặp câu khó, bạn khác có thể xin phép: *"Dạ em xin phép bổ sung thêm ý của bạn ạ..."*
2. **Luôn đi kèm Dòng Code:** Khi nói về bất kỳ tính năng nào, hãy mở sẵn file code đó và chỉ tay vào đúng dòng lệnh tương ứng. Thầy cô sẽ đánh giá cực cao vì biết nhóm tự tay làm và hiểu từng dòng code.
3. **Bình tĩnh khi gặp lỗi bất ngờ:** Nếu bấm lệnh mà bị lỗi cổng (Port already in use), bình tĩnh gõ ngay lệnh: `Stop-Process -Name java -Force` để giải phóng cổng rồi chạy lại, tuyệt đối không lúng túng.

