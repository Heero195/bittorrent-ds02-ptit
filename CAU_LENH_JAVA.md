# 📖 TỔNG HỢP TOÀN BỘ CÂU LỆNH JAVA TRONG DỰ ÁN BITTORRENT P2P
## ĐỀ TÀI DS02: HỆ THỐNG CHIA SẺ FILE NGANG HÀNG MÔ PHỎNG BITTORRENT (JAVA 17)

> **Lưu ý quan trọng trước khi chạy:**  
> Mọi câu lệnh bên dưới đều phải được thực thi tại thư mục dự án:  
> `cd D:\htpt\bitorrent_prj\PeerToPeer-FileSharing`

---

## 📑 MỤC LỤC
1. [Lệnh Biên dịch Mã nguồn (Compilation)](#1-lệnh-biên-dịch-mã-nguồn-compilation)
2. [Lệnh Tạo File Siêu dữ liệu (Metadata Generator CLI)](#2-lệnh-tạo-file-siêu-dữ-liệu-metadata-generator-cli)
3. [Lệnh Chạy Kiểm thử Toàn vẹn (Unit Test Integrity)](#3-lệnh-chạy-kiểm-thử-toàn-vẹn-unit-test-integrity)
4. [Lệnh Khởi chạy Máy chủ Tracker (Tracker Server)](#4-lệnh-khởi-chạy-máy-chủ-tracker-tracker-server)
5. [Lệnh Khởi chạy các Nút Mạng (Peer Processes)](#5-lệnh-khởi-chạy-các-nút-mạng-peer-processes)
6. [Lệnh Khởi chạy Demo Tự động 1-Click (Batch Scripts)](#6-lệnh-khởi-chạy-demo-tự-động-1-click-batch-scripts)
7. [Lệnh Triển khai Môi trường Ảo hóa Docker](#7-lệnh-triển-khai-môi-trường-ảo-hóa-docker)
8. [Các Lệnh Tiện ích Hỗ trợ (PowerShell & Dọn dẹp)](#8-các-lệnh-tiện-ích-hỗ-trợ-powershell--dọn-dẹp)

---

## 1. LỆNH BIÊN DỊCH MÃ NGUỒN (COMPILATION)

### 🔹 Cách 1: Biên dịch bằng file Script tự động (Khuyên dùng)
```cmd
.\scripts\compile.bat
```

### 🔹 Cách 2: Biên dịch thủ công bằng lệnh `javac`
```cmd
javac -d bin src\*.java PeerProcess.java TrackerProcess.java tests\*.java
```
* **Giải thích tham số:**
  * `-d bin`: Đặt toàn bộ các file `.class` sau khi biên dịch vào thư mục `bin/`.
  * `src\*.java`: Biên dịch tất cả các class thư viện cốt lõi (Giao thức, Quản trị, Thuật toán).
  * `PeerProcess.java`: Biên dịch điểm khởi chạy của Peer.
  * `TrackerProcess.java`: Biên dịch điểm khởi chạy của Tracker.
  * `tests\*.java`: Biên dịch các lớp kiểm thử tự động.

---

## 2. LỆNH TẠO FILE SIÊU DỮ LIỆU (METADATA GENERATOR CLI)

Công cụ cắt nhỏ file nguồn thành các mảnh 32KB và tính mã băm SHA-256 từng mảnh.

### 🔹 Cú pháp chuẩn:
```cmd
java -cp bin src.MetadataGenerator <đường_dẫn_file> <kích_thước_mảnh_bytes> [trackerUrl] [file_meta_đầu_ra]
```

### 🔹 Ví dụ thực tế:
Tạo file `thefile.meta` từ file gốc 1MB của Seeder 1001:
```cmd
java -cp bin src.MetadataGenerator peer_1001\thefile 32768 http://localhost:8888 thefile.meta
```
* **Giải thích tham số:**
  * `peer_1001\thefile`: Đường dẫn tới file dữ liệu gốc cần chia sẻ.
  * `32768`: Kích thước mỗi mảnh là 32KB (32 * 1024 bytes).
  * `http://localhost:8888`: Địa chỉ của máy chủ Tracker.
  * `thefile.meta`: Tên file siêu dữ liệu đầu ra được lưu xuống đĩa.

---

## 3. LỆNH CHẠY KIỂM THỬ TOÀN VẸN (UNIT TEST INTEGRITY)

Chạy bộ kiểm thử tự động 3/3 bài test chứng minh thuật toán băm SHA-256 và cơ chế phát hiện mảnh hỏng.

```cmd
java -cp bin tests.TestIntegrity
```
* **Kết quả hiển thị trên màn hình:**
  * `[PASS] Test 1`: Kiểm tra băm SHA-256 chuỗi Hex 64 ký tự chuẩn.
  * `[PASS] Test 2`: Kiểm tra phát hiện mảnh hỏng (Corrupt Chunk Detection).
  * `[PASS] Test 3`: Kiểm tra tính nhất quán mảng SHA-256 trong Metadata.
  * `Test Summary: 3/3 Passed`.

---

## 4. LỆNH KHỞI CHẠY MÁY CHỦ TRACKER (TRACKER SERVER)

### 🔹 Cú pháp chuẩn:
```cmd
java -cp bin TrackerProcess [cổng_port] [file_meta]
```

### 🔹 Lệnh chạy chuẩn bài thi (Cổng 8888 kèm file siêu dữ liệu):
```cmd
java -cp bin TrackerProcess 8888 thefile.meta
```
* **Giải thích:** Khởi động Tracker Server lắng nghe kết nối TCP tại cổng `8888`, nạp sẵn thông tin 31 mảnh của file `thefile.meta` vào RAM.

### 🔹 Các biến thể khác:
* **Chạy với cổng mặc định 8888 (không truyền tham số):**
  ```cmd
  java -cp bin TrackerProcess
  ```
* **Chạy trên một cổng tùy chỉnh khác (ví dụ cổng 9999):**
  ```cmd
  java -cp bin TrackerProcess 9999 thefile.meta
  ```

---

## 5. LỆNH KHỞI CHẠY CÁC NÚT MẠNG (PEER PROCESSES)

### 🔹 Cú pháp chuẩn:
```cmd
java -cp bin PeerProcess <peerID> [trackerHost] [trackerPort] [--corrupt-chunk <index>]
```

---

### 💻 Kịch bản Demo 3 Peer trên cùng 1 máy tính (Localhost):

#### 1. Khởi chạy Seeder 1001 (Nút có sẵn 100% file gốc):
```cmd
java -cp bin PeerProcess 1001 localhost 8888
```
* *Ý nghĩa:* Bật Peer 1001, kết nối tới Tracker tại `localhost:8888`, đăng ký trạng thái `isSeeder = true`, mở cổng `6001` lắng nghe các nút khác.

#### 2. Khởi chạy Leecher 1002 (Kèm tính năng giả lập lỗi mạng):
```cmd
java -cp bin PeerProcess 1002 localhost 8888 --corrupt-chunk 2
```
* *Ý nghĩa:* Bật Peer 1002 (chưa có file), kết nối Tracker, mở cổng `6002`. Cờ `--corrupt-chunk 2` giả lập lỗi làm sai lệch byte của mảnh số 2 khi gửi để chứng minh cơ chế tự phục hồi lỗi.

#### 3. Khởi chạy Leecher 1003 (Tải chéo Swarm):
```cmd
java -cp bin PeerProcess 1003 localhost 8888
```
* *Ý nghĩa:* Bật Peer 1003 (chưa có file), kết nối Tracker, mở cổng `6003`. Nút này sẽ vừa tải từ 1001, vừa tải chéo từ 1002 theo thuật toán Tit-for-Tat Choking.

---

### 🌐 Kịch bản Demo trên 2 Laptop khác nhau qua mạng LAN / Wi-Fi:
*(Giả sử Laptop A có IP là `192.168.1.15`, Laptop B có IP là `192.168.1.20`)*

* **Trên Laptop A (Chạy Seeder 1001):**
  ```cmd
  java -cp bin PeerProcess 1001 192.168.1.15 8888
  ```
* **Trên Laptop B (Chạy Leecher 1002 kết nối qua Wi-Fi sang Laptop A):**
  ```cmd
  java -cp bin PeerProcess 1002 192.168.1.15 8888
  ```

---

## 6. LỆNH KHỞI CHẠY DEMO TỰ ĐỘNG 1-CLICK (BATCH SCRIPTS)

Tự động chạy compile, unit test và bật 4 cửa sổ terminal trong 15 giây:

```powershell
.\scripts\run_demo.bat
```
*(Trên hệ điều hành Linux / macOS: chạy `./scripts/run_demo.sh`)*

---

## 7. LỆNH TRIỂN KHAI MÔI TRƯỜNG ẢO HÓA DOCKER

Dành cho khi giảng viên hỏi về khả năng đóng gói Container:

### 🔹 Khởi động mạng ảo P2P đa container:
```cmd
cd deploy
docker-compose up --build
```

### 🔹 Dừng và dọn dẹp các container:
```cmd
docker-compose down
```

---

## 8. CÁC LỆNH TIỆN ÍCH HỖ TRỢ (POWERSHELL & DỌN DẸP)

### 🔹 1. Dọn dẹp file tải cũ trước khi demo (Reset về 0):
```powershell
Remove-Item peer_1002\thefile, peer_1003\thefile -ErrorAction SilentlyContinue
```

### 🔹 2. Tắt nóng toàn bộ các tiến trình Java đang chạy nền:
*(Dùng khi muốn dừng chạy hoặc giải phóng file bị khóa)*
```powershell
Stop-Process -Name java -Force -ErrorAction SilentlyContinue
```

### 🔹 3. Kiểm tra tính toàn vẹn file bằng mã băm SHA-256 (Nghiệm thu sau khi tải):
```powershell
Get-FileHash peer_1001\thefile, peer_1002\thefile, peer_1003\thefile -Algorithm SHA256
```
👉 **Mã băm chuẩn khớp 100%:**  
`D29751F2649B32FF572B5E0A9F541EA660A50F94FF0BEEDFB0B692B924CC8025`

### 🔹 4. Xem nhật ký thời gian thực của các Peer:
```powershell
Get-Content log_peer_1001.log -Tail 20
Get-Content log_peer_1002.log -Tail 20
Get-Content log_peer_1003.log -Tail 20
```

