# 🎬 KỊCH BẢN VÀ LỆNH DEMO HỆ THỐNG BITTORRENT P2P
## ĐỀ TÀI DS02: HỆ THỐNG CHIA SẺ TỆP NGANG HÀNG MÔ PHỎNG BITTORRENT (JAVA 17)

---

## 📋 MỤC LỤC
1. [Chuẩn bị trước khi Demo](#1-chuẩn-bị-trước-khi-demo)
2. [Cách 1: Demo Tự động 1-Click (Khuyên dùng)](#2-cách-1-demo-tự-động-1-click-khuyên-dùng)
3. [Cách 2: Demo Từng bước bằng Dòng lệnh (Thủ công)](#3-cách-2-demo-từng-bước-bằng-dòng-lệnh-thủ-công)
4. [Cách 3: Demo Môi trường ảo hóa Docker Compose](#4-cách-3-demo-môi-trường-ảo-hóa-docker-compose)
5. [Kịch bản Thuyết minh Chi tiết (Lời thoại theo từng hành động)](#5-kịch-bản-thuyết-minh-chi-tiết-lời-thoại-theo-từng-hành-động)
6. [Bí kíp Vấn đáp khi Giảng viên hỏi vặn lúc Demo](#6-bí-kíp-vấn-đáp-khi-giảng-viên-hỏi-vặn-lúc-demo)

---

## 1. CHUẨN BỊ TRƯỚC KHI DEMO

Trước khi bắt đầu buổi bảo vệ, hãy đảm bảo:
1. Mở sẵn **PowerShell** hoặc **Command Prompt** tại thư mục gốc của dự án:
   ```powershell
   cd D:\htpt\bitorrent_prj\PeerToPeer-FileSharing
   ```
2. Đã xóa các file tải cũ của Peer 1002 và 1003 (để chứng minh Leecher tải từ con số 0):
   ```powershell
   Remove-Item peer_1002\thefile, peer_1003\thefile -ErrorAction SilentlyContinue
   ```
3. Đã có file gốc 1MB trong thư mục của Seeder (`peer_1001\thefile`) và file siêu dữ liệu `thefile.meta`.

---

## 2. CÁCH 1: DEMO TỰ ĐỘNG 1-CLICK (KHUYÊN DÙNG)

Khi thầy cô yêu cầu: *"Nhóm cho chạy demo hệ thống đi!"*, cách nhanh nhất và ấn tượng nhất là dùng script 1-click.

### Lệnh thực thi:
```powershell
.\scripts\run_demo.bat
```

### Quá trình tự động diễn ra:
1. **Biên dịch mã nguồn** qua `javac` vào thư mục `bin/`.
2. **Tự động chạy Unit Test** (`tests.TestIntegrity`): In ra `3/3 Tests PASSED!` chứng minh thuật toán băm SHA-256 hoạt động chuẩn xác.
3. **Tự động bật 4 cửa sổ terminal riêng biệt**:
   * 🖥️ **Cửa sổ 1 - Tracker Server**: Lắng nghe tại cổng `8888`.
   * 🖥️ **Cửa sổ 2 - Seeder 1001**: Đã có 100% file, đăng ký với Tracker.
   * 🖥️ **Cửa sổ 3 - Leecher 1002**: Tải file, tự kích hoạt tiêm lỗi mảnh 2 (`--corrupt-chunk 2`), phát hiện băm sai, vứt bỏ và tải lại thành công.
   * 🖥️ **Cửa sổ 4 - Leecher 1003**: Tải file song song từ cả 1001 và 1002 (chứng minh mạng Swarm P2P và luật Tit-for-Tat).

---

## 3. CÁCH 2: DEMO TỪNG BƯỚC BẰNG DÒNG LỆNH (THỦ CÔNG)

Áp dụng khi thầy cô muốn xem chi tiết từng dòng lệnh khởi động của mỗi tiến trình.

### Bước 0: Dọn dẹp và Biên dịch
```powershell
cd D:\htpt\bitorrent_prj\PeerToPeer-FileSharing
Remove-Item peer_1002\thefile, peer_1003\thefile -ErrorAction SilentlyContinue
.\scripts\compile.bat
```

### Bước 1: Chạy kiểm thử tự động (Unit Test)
```powershell
java -cp bin tests.TestIntegrity
```
*Kết quả:* Màn hình in 3 bài test đạt yêu cầu: Băm file, Bắt mảnh hỏng và Mảng BitSet.

### Bước 2: Bật Cửa sổ 1 — Tracker Server
Mở tab PowerShell thứ nhất:
```powershell
java -cp bin TrackerProcess 8888 thefile.meta
```
*Giải thích:* Tracker lắng nghe cổng 8888, nạp `thefile.meta` vào RAM, không lưu nội dung file.

### Bước 3: Bật Cửa sổ 2 — Seeder 1001 (Người có file gốc)
Mở tab PowerShell thứ hai:
```powershell
java -cp bin PeerProcess 1001 localhost 8888
```
*Giải thích:* Đăng ký lên Tracker với cờ `isSeeder = true`, báo cáo có đủ 31/31 mảnh.

### Bước 4: Bật Cửa sổ 3 — Leecher 1002 (Người tải có tiêm lỗi)
Mở tab PowerShell thứ ba:
```powershell
java -cp bin PeerProcess 1002 localhost 8888 --corrupt-chunk 2
```
*Giải thích:* Leecher 1002 kết nối vào Swarm, xin dữ liệu từ 1001. Tại mảnh số 2, hệ thống giả lập lỗi đường truyền mạng làm sai lệch byte. Hàm `verifyPiece()` phát hiện mã SHA-256 không khớp, từ chối lưu đĩa và tự động xin tải lại.

### Bước 5: Bật Cửa sổ 4 — Leecher 1003 (Tải chéo Swarm)
Mở tab PowerShell thứ tư:
```powershell
java -cp bin PeerProcess 1003 localhost 8888
```
*Giải thích:* Nút 1003 vào mạng, vừa tải từ Seeder 1001, vừa tải các mảnh mới có từ Leecher 1002. Cứ mỗi 5 giây, thuật toán `ChokeHandler` xếp hạng tốc độ download để unchoke cho 2 láng giềng nhanh nhất (Tit-for-Tat).

### Bước 6: Nghiệm thu Toàn vẹn Dữ liệu bằng mã băm SHA-256
Sau khi các cửa sổ báo hoàn thành tải, quay lại cửa sổ chính chạy:
```powershell
Get-FileHash peer_1001\thefile, peer_1002\thefile, peer_1003\thefile -Algorithm SHA256
```
*Kết quả:* Cả 3 file tại 3 thư mục có mã băm giống hệt nhau 100%:
`D29751F2649B32FF572B5E0A9F541EA660A50F94FF0BEEDFB0B692B924CC8025`

---

## 4. CÁCH 3: DEMO MÔI TRƯỜNG ẢO HÓA DOCKER COMPOSE

Nếu giảng viên hỏi: *"Hệ thống có chạy được trên container Docker tách biệt mạng không?"*:

```powershell
cd D:\htpt\bitorrent_prj\PeerToPeer-FileSharing\deploy
docker-compose up --build
```
*Đặc điểm:* Dựng mạng ảo `172.20.0.0/16`, cấp IP tĩnh riêng cho Tracker (`172.20.0.2`), Seeder 1001 (`172.20.0.11`), Leecher 1002 (`172.20.0.12`), Leecher 1003 (`172.20.0.13`).

---

## 5. KỊCH BẢN THUYẾT MINH CHI TIẾT (LỜI THOẠI THEO TỪNG HÀNH ĐỘNG)

Dưới đây là lời thoại chuẩn từng bước để nhóm phối hợp nhịp nhàng:

| Bước | Hành động trên màn hình | Lời thoại thuyết minh của sinh viên |
| :--- | :--- | :--- |
| **1. Mở đầu** | Mở thư mục dự án trên VS Code / Terminal | *"Kính thưa thầy/cô, sau đây nhóm em xin phép trình diễn hoạt động thực tế của hệ thống chia sẻ file P2P mô phỏng BitTorrent. Hệ thống bao gồm 1 Server Tracker điều phối và 3 Peer tham gia truyền tải dữ liệu đa luồng."* |
| **2. Chạy Test** | Chạy `java -cp bin tests.TestIntegrity` | *"Đầu tiên, em chạy bộ kiểm thử Unit Test. Như thầy thấy, cả 3 test đều PASSED: kiểm tra cắt nhỏ file thành các mảnh 32KB, kiểm tra băm SHA-256 từng mảnh, và kiểm tra cơ chế phát hiện mảnh dữ liệu hỏng."* |
| **3. Bật Tracker** | Chạy `TrackerProcess 8888 thefile.meta` | *"Tiếp theo, em khởi chạy Tracker Server tại cổng 8888. Tracker nạp cấu trúc file `thefile.meta` vào RAM. Em xin nhấn mạnh: Tracker của nhóm tuyệt đối không lưu nội dung file dữ liệu, mà chỉ quản lý danh bạ IP/Port của các nút và trạng thái mảnh dữ liệu (BitSet)."* |
| **4. Bật Seeder** | Chạy `PeerProcess 1001 localhost 8888` | *"Bây giờ em bật nút Seeder 1001. Nút này đã có sẵn file 1MB trong thư mục `peer_1001`. Nó gửi bản tin REGISTER báo cáo với Tracker rằng nó đang nắm giữ trọn vẹn 31 mảnh dữ liệu và sẵn sàng chia sẻ."* |
| **5. Bật Leecher & Tiêm lỗi** | Chạy `PeerProcess 1002 ... --corrupt-chunk 2` | *"Đây là nút Leecher 1002 chưa có file. Nó kết nối Tracker xin danh bạ, bắt tay 32-bytes với 1001 và gửi bản tin request xin từng mảnh. Để chứng minh khả năng chịu lỗi, nhóm em tiêm lỗi mạng làm hỏng mảnh số 2. Log hiển thị rõ: Khi nhận mảnh 2, hệ thống băm SHA-256 thấy sai lệch, lập tức vứt bỏ và gửi request tải lại thành công."* |
| **6. Tải chéo P2P & Choking** | Chạy `PeerProcess 1003 localhost 8888` | *"Khi em bật nút Leecher 1003, mạng Swarm chính thức hoạt động đa nguồn: 1003 không chỉ xin dữ liệu từ Seeder 1001 mà còn tải chéo các mảnh từ Leecher 1002. Cứ mỗi 5 giây, thuật toán Tit-for-Tat sẽ đo tốc độ tải để mở van (unchoke) cho 2 peer nhanh nhất, đồng thời cứ mỗi 15 giây thuật toán Optimistic Unchoke sẽ mở van ngẫu nhiên 1 peer để tìm kiếm kết nối tốt hơn."* |
| **7. Nghiệm thu** | Chạy lệnh `Get-FileHash` | *"Tất cả các nút đã tải xong. Em chạy lệnh băm SHA-256 trên cả 3 file tại 3 thư mục `peer_1001`, `peer_1002`, `peer_1003`. Kết quả: Mã băm của cả 3 file trùng khớp 100% từng ký tự. Toàn bộ quá trình cũng được ghi chi tiết vào các file log riêng biệt."* |

---

## 6. BÍ KÍP VẤN ĐÁP KHI GIẢNG VIÊN HỎI VẶN LÚC DEMO

#### ❓ Câu hỏi 1: "Tracker có phải là nơi lưu trữ file để các máy tải về không?"
* **Trả lời chuẩn:** *"Dạ không thưa thầy/cô! Điểm cốt lõi của BitTorrent là Tracker **tuyệt đối không tham gia truyền file**. Tracker chỉ đóng vai trò danh bạ (Directory Service) lưu địa chỉ IP, Port và mảng BitSet của các Peer. Các Peer tải trực tiếp từ nhau qua kết nối TCP P2P."*

#### ❓ Câu hỏi 2: "Tại sao không tải file từ đầu đến cuối mà phải chia thành các mảnh (pieces)?"
* **Trả lời chuẩn:** *"Dạ thưa thầy, chia nhỏ thành các mảnh 32KB giúp:  
  1. Tải song song từ nhiều người cùng một lúc (Swarm downloading).  
  2. Dễ dàng kiểm tra tính toàn vẹn từng mảnh bằng mã băm SHA-256. Nếu hỏng mảnh nào chỉ cần tải lại đúng 32KB của mảnh đó chứ không phải tải lại cả file 1GB.  
  3. Cho phép ghi file ngẫu nhiên ra đĩa bằng `RandomAccessFile`."*

#### ❓ Câu hỏi 3: "Thuật toán Tit-for-Tat giải quyết vấn đề gì trong mạng P2P?"
* **Trả lời chuẩn:** *"Dạ thưa thầy, thuật toán Tit-for-Tat (Bánh ít đi, bánh quy lại) dùng để giải quyết vấn nạn **Free-riding (xài chùa)**. Ai cho mình tải với tốc độ cao thì mình sẽ ưu tiên mở van (`unchoke`) cho họ tải lại. Kẻ nào chỉ muốn tải mà bóp nghẹt không upload cho người khác thì sẽ bị toàn mạng bóp nghẹt (`choke`) không cho tải."*

#### ❓ Câu hỏi 4: "Nếu ai cũng chỉ mở van cho người tải nhanh thì một máy mới toanh vào mạng sẽ ra sao?"
* **Trả lời chuẩn:** *"Dạ chính vì vậy hệ thống mới cần thuật toán **Optimistic Unchoke** (Mở van lạc quan). Cứ định kỳ 15 giây, hệ thống chọn ngẫu nhiên 1 peer đang bị nghẽn để mở van cho họ tải. Cơ chế này giúp các nút mới chưa có dữ liệu vẫn có cơ hội nhận được những mảnh đầu tiên để tham gia vào mạng lưới."*
