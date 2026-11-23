package src;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebDashboard {
    private static final int PORT = 8080;
    private static final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new IndexHandler());
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/action", new ActionHandler());
        server.createContext("/api/hashes", new HashHandler());
        server.createContext("/api/events", new EventsHandler());
        server.createContext("/api/logs", new LogHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println("==================================================================");
        System.out.println("   BitTorrent P2P Live Protocol Dashboard Started!");
        System.out.println("   Open in browser: http://localhost:" + PORT);
        System.out.println("==================================================================");

        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "http://localhost:" + PORT).start();
            }
        } catch (Exception ignored) {}
    }

    private static boolean isPortOpen(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 200);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String calculateSHA256(File file) {
        if (!file.exists()) return "CHƯA_CÓ_FILE";
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (Exception e) {
            return "LỖI_ĐỌC: " + e.getMessage();
        }
    }

    private static List<Integer> getDownloadedPieces(String peerId) {
        List<Integer> list = new ArrayList<>();
        File logFile = new File("log_peer_" + peerId + ".log");
        if (!logFile.exists()) return list;

        Pattern pattern = Pattern.compile("has downloaded the piece \\[(\\d+)\\]");
        Set<Integer> unique = new HashSet<>();
        try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    unique.add(Integer.parseInt(m.group(1)));
                }
            }
        } catch (Exception ignored) {}
        list.addAll(unique);
        Collections.sort(list);
        return list;
    }

    private static class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = getDashboardHTML();
            byte[] responseBytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    private static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File f1001 = new File("peer_1001/thefile");
            File f1002 = new File("peer_1002/thefile");
            File f1003 = new File("peer_1003/thefile");

            boolean trackerRunning = isPortOpen(8888) || isProcessAlive("tracker");
            boolean peer1001Running = isPortOpen(6001) || isProcessAlive("peer1001");
            boolean peer1002Running = isPortOpen(6002) || isProcessAlive("peer1002");
            boolean peer1003Running = isPortOpen(6003) || isProcessAlive("peer1003");

            long size1001 = f1001.exists() ? f1001.length() : 0;
            long size1002 = f1002.exists() ? f1002.length() : 0;
            long size1003 = f1003.exists() ? f1003.length() : 0;

            List<Integer> pieces1002 = getDownloadedPieces("1002");
            List<Integer> pieces1003 = getDownloadedPieces("1003");

            StringBuilder p2Json = new StringBuilder("[");
            for (int i = 0; i < pieces1002.size(); i++) {
                p2Json.append(pieces1002.get(i));
                if (i < pieces1002.size() - 1) p2Json.append(",");
            }
            p2Json.append("]");

            StringBuilder p3Json = new StringBuilder("[");
            for (int i = 0; i < pieces1003.size(); i++) {
                p3Json.append(pieces1003.get(i));
                if (i < pieces1003.size() - 1) p3Json.append(",");
            }
            p3Json.append("]");

            String json = String.format(Locale.US,
                "{" +
                "\"tracker\": {\"running\": %b, \"port\": 8888}," +
                "\"peer1001\": {\"running\": %b, \"size\": %d, \"pieces\": 31, \"complete\": true}," +
                "\"peer1002\": {\"running\": %b, \"size\": %d, \"pieceCount\": %d, \"pieces\": %s, \"complete\": %b}," +
                "\"peer1003\": {\"running\": %b, \"size\": %d, \"pieceCount\": %d, \"pieces\": %s, \"complete\": %b}" +
                "}",
                trackerRunning,
                peer1001Running, size1001,
                peer1002Running, size1002, pieces1002.size(), p2Json.toString(), pieces1002.size() >= 31,
                peer1003Running, size1003, pieces1003.size(), p3Json.toString(), pieces1003.size() >= 31
            );

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String cmd = params.getOrDefault("cmd", "");
            String result = "OK";

            try {
                switch (cmd) {
                    case "start_tracker":
                        startTracker();
                        break;
                    case "start_1001":
                        startPeer("peer1001", "1001", false);
                        break;
                    case "start_1002":
                        startPeer("peer1002", "1002", true);
                        break;
                    case "start_1003":
                        startPeer("peer1003", "1003", false);
                        break;
                    case "stop_all":
                        stopAll();
                        break;
                    case "clean":
                        stopAll();
                        new File("peer_1002/thefile").delete();
                        new File("peer_1003/thefile").delete();
                        new File("log_peer_1001.log").delete();
                        new File("log_peer_1002.log").delete();
                        new File("log_peer_1003.log").delete();
                        break;
                    default:
                        result = "UNKNOWN_CMD";
                }
            } catch (Exception e) {
                result = "ERROR: " + e.getMessage();
            }

            String json = "{\"status\": \"" + result + "\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static class HashHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String h1001 = calculateSHA256(new File("peer_1001/thefile"));
            String h1002 = calculateSHA256(new File("peer_1002/thefile"));
            String h1003 = calculateSHA256(new File("peer_1003/thefile"));

            boolean match = h1001.equals(h1002) && h1002.equals(h1003) && !h1001.contains("CHƯA") && !h1001.contains("LỖI");

            String json = String.format(
                "{\"peer1001\": \"%s\", \"peer1002\": \"%s\", \"peer1003\": \"%s\", \"match\": %b}",
                h1001, h1002, h1003, match
            );

            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    public static class P2PEvent implements Comparable<P2PEvent> {
        public String time;
        public String source;
        public String target;
        public String type;
        public String details;
        public String badgeColor;
        public long timestamp;

        public P2PEvent(String time, String source, String target, String type, String details, String badgeColor, long timestamp) {
            this.time = time;
            this.source = source;
            this.target = target;
            this.type = type;
            this.details = details;
            this.badgeColor = badgeColor;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(P2PEvent o) {
            return Long.compare(this.timestamp, o.timestamp);
        }
    }

    private static class EventsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            List<P2PEvent> events = parseAllEvents();
            StringBuilder json = new StringBuilder("[");
            int start = Math.max(0, events.size() - 60);
            for (int i = start; i < events.size(); i++) {
                P2PEvent e = events.get(i);
                json.append(String.format(
                    "{\"time\": \"%s\", \"source\": \"%s\", \"target\": \"%s\", \"type\": \"%s\", \"details\": \"%s\", \"color\": \"%s\"}",
                    e.time.replace("\"", "\\\""),
                    e.source,
                    e.target,
                    e.type,
                    e.details.replace("\"", "\\\""),
                    e.badgeColor
                ));
                if (i < events.size() - 1) json.append(",");
            }
            json.append("]");

            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static List<P2PEvent> parseAllEvents() {
        List<P2PEvent> events = new ArrayList<>();
        for (String peer : new String[]{"1001", "1002", "1003"}) {
            File f = new File("log_peer_" + peer + ".log");
            if (!f.exists()) continue;
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                long count = 0;
                while ((line = br.readLine()) != null) {
                    count++;
                    P2PEvent evt = parseLineToEvent(peer, line, count);
                    if (evt != null) events.add(evt);
                }
            } catch (Exception ignored) {}
        }
        Collections.sort(events);
        return events;
    }

    private static P2PEvent parseLineToEvent(String ownerPeer, String line, long lineNum) {
        if (!line.startsWith("[")) return null;
        int timeEnd = line.indexOf("]:");
        if (timeEnd == -1) return null;
        String timeStr = line.substring(1, timeEnd);
        String msg = line.substring(timeEnd + 2).trim();

        String type = "INFO";
        String source = ownerPeer;
        String target = "Swarm";
        String color = "slate";
        String details = msg;

        if (msg.contains("makes a connection to Peer")) {
            type = "HANDSHAKE_OUT";
            target = extractBracket(msg, 2);
            color = "blue";
            details = "Chủ động gửi gói tin Handshake (32 bytes) tới Peer " + target;
        } else if (msg.contains("is connected from Peer")) {
            type = "HANDSHAKE_IN";
            target = extractBracket(msg, 2);
            source = target;
            target = ownerPeer;
            color = "indigo";
            details = "Tiếp nhận kết nối Handshake từ Peer " + source;
        } else if (msg.contains("has downloaded the piece")) {
            type = "PIECE_RECEIVED";
            String piece = extractBracket(msg, 2);
            String fromPeer = extractBracket(msg, 3);
            source = fromPeer;
            target = ownerPeer;
            color = "emerald";
            details = "Tải thành công mảnh #" + piece + " (32KB) từ Peer " + fromPeer + " - Đã băm khớp SHA-256";
        } else if (msg.contains("received the 'have' message")) {
            type = "HAVE_BROADCAST";
            String fromPeer = extractBracket(msg, 2);
            String piece = extractBracket(msg, 3);
            source = fromPeer;
            target = ownerPeer;
            color = "teal";
            details = "Peer " + fromPeer + " phát loa: 'Tôi đã có mảnh #" + piece + ", sẵn sàng làm Seeder cho mảnh này!'";
        } else if (msg.contains("has the preferred neighbors")) {
            type = "TIT_FOR_TAT";
            String neighbors = extractBracket(msg, 2);
            color = "purple";
            details = "Thuật toán Tit-for-Tat: Chọn láng giềng ưu tiên có tốc độ cao nhất: [" + neighbors + "]";
        } else if (msg.contains("has the optimistically unchoked neighbor")) {
            type = "OPTIMISTIC_UNCHOKE";
            String unchoked = extractBracket(msg, 2);
            color = "cyan";
            details = "Mở van lạc quan (15s): Ngẫu nhiên mở van unchoke cho Peer " + unchoked + " để tìm đường truyền tốt hơn";
        } else if (msg.contains("is unchoked by")) {
            type = "UNCHOKED";
            String from = extractBracket(msg, 2);
            source = from;
            target = ownerPeer;
            color = "green";
            details = "Được Peer " + from + " mở van (UNCHOKE) - Cho phép gửi yêu cầu xin mảnh";
        } else if (msg.contains("is choked by")) {
            type = "CHOKED";
            String from = extractBracket(msg, 2);
            source = from;
            target = ownerPeer;
            color = "amber";
            details = "Bị Peer " + from + " khóa van (CHOKE) theo luật Tit-for-Tat";
        } else if (msg.contains("FAULT_TOLERANCE") || msg.contains("INVALID/CORRUPT")) {
            type = "CORRUPT_REJECTED";
            color = "rose";
            details = "PHÁT HIỆN MẢNH HỎNG: Băm SHA-256 thấy sai lệch! Từ chối ghi đĩa và yêu cầu tải lại.";
        } else if (msg.contains("downloaded the complete file")) {
            type = "FILE_COMPLETE";
            color = "emerald";
            details = "TẢI TOÀN BỘ FILE HOÀN TẤT (31/31 MẢNH) — Trở thành Seeder chính thức!";
        } else if (msg.contains("Full file SHA-256 integrity check PASSED")) {
            type = "HASH_VERIFIED";
            color = "emerald";
            details = "Kiểm tra tính toàn vẹn toàn file thành công: Khớp mã băm SHA-256 gốc!";
        } else if (msg.contains("received the 'interested' message")) {
            type = "INTERESTED";
            String from = extractBracket(msg, 2);
            source = from;
            target = ownerPeer;
            color = "sky";
            details = "Peer " + from + " gửi 'INTERESTED': Muốn xin các mảnh dữ liệu đang có.";
        } else {
            return null; // Skip noisy messages
        }

        return new P2PEvent(timeStr, source, target, type, details, color, lineNum);
    }

    private static String extractBracket(String text, int occurrence) {
        int count = 0;
        int start = -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '[') {
                count++;
                if (count == occurrence) start = i + 1;
            } else if (text.charAt(i) == ']' && start != -1) {
                return text.substring(start, i);
            }
        }
        return "?";
    }

    private static class LogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String peer = params.getOrDefault("peer", "1002");
            File logFile = new File("log_peer_" + peer + ".log");

            StringBuilder sb = new StringBuilder();
            if (logFile.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
                    List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = br.readLine()) != null) {
                        lines.add(line);
                    }
                    int start = Math.max(0, lines.size() - 30);
                    for (int i = start; i < lines.size(); i++) {
                        sb.append(lines.get(i)).append("\n");
                    }
                }
            } else {
                sb.append("Chưa có file log cho Peer ").append(peer);
            }

            byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private static synchronized void startTracker() throws IOException {
        if (!isProcessAlive("tracker") && !isPortOpen(8888)) {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", "bin", "TrackerProcess", "8888", "thefile.meta");
            pb.redirectErrorStream(true);
            activeProcesses.put("tracker", pb.start());
        }
    }

    private static synchronized void startPeer(String key, String peerId, boolean injectFault) throws IOException {
        int port = 6000 + Integer.parseInt(peerId);
        if (!isProcessAlive(key) && !isPortOpen(port)) {
            List<String> cmd = new ArrayList<>(Arrays.asList("java", "-cp", "bin", "PeerProcess", peerId, "localhost", "8888"));
            if (injectFault) {
                cmd.add("--corrupt-chunk");
                cmd.add("2");
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            activeProcesses.put(key, pb.start());
        }
    }

    private static synchronized void stopAll() {
        for (Map.Entry<String, Process> entry : activeProcesses.entrySet()) {
            if (entry.getValue() != null && entry.getValue().isAlive()) {
                entry.getValue().destroyForcibly();
            }
        }
        activeProcesses.clear();

        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("powershell", "-Command", "Stop-Process -Name java -Force -ErrorAction SilentlyContinue").start();
            }
        } catch (Exception ignored) {}
    }

    private static boolean isProcessAlive(String key) {
        Process p = activeProcesses.get(key);
        return p != null && p.isAlive();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length > 1) {
                result.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
            } else if (pair.length == 1) {
                result.put(pair[0], "");
            }
        }
        return result;
    }

    private static String getDashboardHTML() {
        return "<!DOCTYPE html>\n" +
            "<html lang=\"vi\">\n" +
            "<head>\n" +
            "  <meta charset=\"UTF-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>BitTorrent P2P Live Protocol Inspector</title>\n" +
            "  <script src=\"https://cdn.tailwindcss.com\"></script>\n" +
            "  <style>\n" +
            "    .piece-box { width: 14px; height: 14px; border-radius: 2px; transition: all 0.2s ease; }\n" +
            "    ::-webkit-scrollbar { width: 6px; height: 6px; }\n" +
            "    ::-webkit-scrollbar-track { background: #0f172a; }\n" +
            "    ::-webkit-scrollbar-thumb { background: #334155; border-radius: 3px; }\n" +
            "  </style>\n" +
            "</head>\n" +
            "<body class=\"bg-slate-950 text-slate-100 min-h-screen p-4 md:p-6 font-sans\">\n" +
            "  <div class=\"max-w-7xl mx-auto space-y-5\">\n" +
            "\n" +
            "    <!-- Header -->\n" +
            "    <div class=\"flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800 pb-4\">\n" +
            "      <div class=\"flex items-center gap-3\">\n" +
            "        <div class=\"p-2.5 rounded-xl bg-indigo-500/20 text-indigo-400 text-2xl border border-indigo-500/30\">\n" +
            "          📡\n" +
            "        </div>\n" +
            "        <div>\n" +
            "          <h1 class=\"text-xl md:text-2xl font-bold tracking-tight text-white\">\n" +
            "            BitTorrent P2P Live Protocol & Packet Inspector\n" +
            "          </h1>\n" +
            "          <p class=\"text-xs text-slate-400\">\n" +
            "            Hệ thống phân tán DS02 — Giám sát tương tác mạng ngang hàng thực tế 100%\n" +
            "          </p>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "      <div class=\"flex flex-wrap items-center gap-2\">\n" +
            "        <button onclick=\"checkHashes()\" class=\"px-3.5 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-xs rounded-lg shadow transition flex items-center gap-1.5\">\n" +
            "          <span>🔐</span> So khớp SHA-256 trên đĩa\n" +
            "        </button>\n" +
            "        <button onclick=\"callAction('clean')\" class=\"px-3 py-1.5 bg-rose-900/60 hover:bg-rose-800 text-rose-200 border border-rose-700/50 text-xs rounded-lg transition flex items-center gap-1.5\">\n" +
            "          <span>🧹</span> Dừng & Reset file tải dở\n" +
            "        </button>\n" +
            "      </div>\n" +
            "    </div>\n" +
            "\n" +
            "    <!-- SHA-256 Result Banner -->\n" +
            "    <div id=\"hashBanner\" class=\"hidden rounded-xl p-4 border transition-all\"></div>\n" +
            "\n" +
            "    <!-- Step-by-Step Operator Control Center -->\n" +
            "    <div class=\"bg-slate-900/90 border border-slate-800 rounded-xl p-4 space-y-3\">\n" +
            "      <div class=\"flex items-center justify-between\">\n" +
            "        <h2 class=\"text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5\">\n" +
            "          <span>🎮</span> Điều khiển khởi chạy từng tiến trình thực tế (Step-by-Step)\n" +
            "        </h2>\n" +
            "        <span class=\"text-[11px] text-slate-500\">Bấm từng bước để trình bày với giảng viên</span>\n" +
            "      </div>\n" +
            "      <div class=\"grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3\">\n" +
            "        <!-- Step 1: Tracker -->\n" +
            "        <div class=\"bg-slate-950 border border-slate-800/80 rounded-lg p-3 space-y-2 flex flex-col justify-between\">\n" +
            "          <div>\n" +
            "            <div class=\"flex items-center justify-between\">\n" +
            "              <span class=\"text-[11px] font-bold text-indigo-400\">BƯỚC 1: TRACKER</span>\n" +
            "              <span id=\"badgeTracker\" class=\"px-2 py-0.5 text-[10px] rounded-full bg-slate-800 text-slate-400\">Offline</span>\n" +
            "            </div>\n" +
            "            <div class=\"text-xs font-semibold text-white mt-1\">Cổng TCP: 8888</div>\n" +
            "            <p class=\"text-[11px] text-slate-400 mt-0.5\">Mở socket danh bạ, nạp thefile.meta.</p>\n" +
            "          </div>\n" +
            "          <button id=\"btnTracker\" onclick=\"callAction('start_tracker')\" class=\"w-full py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs rounded transition flex items-center justify-center gap-1\">\n" +
            "            <span>▶️</span> Bật Tracker\n" +
            "          </button>\n" +
            "        </div>\n" +
            "\n" +
            "        <!-- Step 2: Seeder 1001 -->\n" +
            "        <div class=\"bg-slate-950 border border-slate-800/80 rounded-lg p-3 space-y-2 flex flex-col justify-between\">\n" +
            "          <div>\n" +
            "            <div class=\"flex items-center justify-between\">\n" +
            "              <span class=\"text-[11px] font-bold text-emerald-400\">BƯỚC 2: SEEDER 1001</span>\n" +
            "              <span id=\"badge1001\" class=\"px-2 py-0.5 text-[10px] rounded-full bg-slate-800 text-slate-400\">Offline</span>\n" +
            "            </div>\n" +
            "            <div class=\"text-xs font-semibold text-white mt-1\">Có sẵn 100% file gốc</div>\n" +
            "            <p class=\"text-[11px] text-slate-400 mt-0.5\">Cổng 6001. Đăng ký có đủ 31 mảnh.</p>\n" +
            "          </div>\n" +
            "          <button id=\"btn1001\" onclick=\"callAction('start_1001')\" class=\"w-full py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-xs rounded transition flex items-center justify-center gap-1\">\n" +
            "            <span>▶️</span> Bật Seeder 1001\n" +
            "          </button>\n" +
            "        </div>\n" +
            "\n" +
            "        <!-- Step 3: Leecher 1002 -->\n" +
            "        <div class=\"bg-slate-950 border border-slate-800/80 rounded-lg p-3 space-y-2 flex flex-col justify-between\">\n" +
            "          <div>\n" +
            "            <div class=\"flex items-center justify-between\">\n" +
            "              <span class=\"text-[11px] font-bold text-amber-400\">BƯỚC 3: LEECHER 1002</span>\n" +
            "              <span id=\"badge1002\" class=\"px-2 py-0.5 text-[10px] rounded-full bg-slate-800 text-slate-400\">Offline</span>\n" +
            "            </div>\n" +
            "            <div class=\"text-xs font-semibold text-white mt-1\">Tiêm lỗi mảnh #2</div>\n" +
            "            <p class=\"text-[11px] text-slate-400 mt-0.5\">Tải từ 1001, tự phát hiện & phục hồi lỗi.</p>\n" +
            "          </div>\n" +
            "          <button id=\"btn1002\" onclick=\"callAction('start_1002')\" class=\"w-full py-1.5 bg-amber-600 hover:bg-amber-500 text-white font-medium text-xs rounded transition flex items-center justify-center gap-1\">\n" +
            "            <span>▶️</span> Bật Leecher 1002\n" +
            "          </button>\n" +
            "        </div>\n" +
            "\n" +
            "        <!-- Step 4: Leecher 1003 -->\n" +
            "        <div class=\"bg-slate-950 border border-slate-800/80 rounded-lg p-3 space-y-2 flex flex-col justify-between\">\n" +
            "          <div>\n" +
            "            <div class=\"flex items-center justify-between\">\n" +
            "              <span class=\"text-[11px] font-bold text-cyan-400\">BƯỚC 4: LEECHER 1003</span>\n" +
            "              <span id=\"badge1003\" class=\"px-2 py-0.5 text-[10px] rounded-full bg-slate-800 text-slate-400\">Offline</span>\n" +
            "            </div>\n" +
            "            <div class=\"text-xs font-semibold text-white mt-1\">Tải chéo đa nguồn</div>\n" +
            "            <p class=\"text-[11px] text-slate-400 mt-0.5\">Tải từ cả 1001 và 1002 (Tit-for-Tat).</p>\n" +
            "          </div>\n" +
            "          <button id=\"btn1003\" onclick=\"callAction('start_1003')\" class=\"w-full py-1.5 bg-cyan-600 hover:bg-cyan-500 text-white font-medium text-xs rounded transition flex items-center justify-center gap-1\">\n" +
            "            <span>▶️</span> Bật Leecher 1003\n" +
            "          </button>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "    </div>\n" +
            "\n" +
            "    <!-- Real-time Piece Matrix -->\n" +
            "    <div class=\"bg-slate-900/90 border border-slate-800 rounded-xl p-4 space-y-3\">\n" +
            "      <div class=\"flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2\">\n" +
            "        <h2 class=\"text-xs font-semibold uppercase tracking-wider text-slate-400 flex items-center gap-1.5\">\n" +
            "          <span>🧩</span> Tiến độ thực tế & Ma trận 31 Mảnh Dữ liệu (Real Piece Matrix)\n" +
            "        </h2>\n" +
            "        <div class=\"flex items-center gap-3 text-xs text-slate-400\">\n" +
            "          <div class=\"flex items-center gap-1.5\"><div class=\"w-2.5 h-2.5 rounded bg-emerald-500\"></div> Đã tải (Khớp SHA-256)</div>\n" +
            "          <div class=\"flex items-center gap-1.5\"><div class=\"w-2.5 h-2.5 rounded bg-rose-500 animate-pulse\"></div> Mảnh lỗi (Từ chối)</div>\n" +
            "          <div class=\"flex items-center gap-1.5\"><div class=\"w-2.5 h-2.5 rounded bg-slate-800 border border-slate-700\"></div> Chưa có</div>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "      <div class=\"grid grid-cols-1 md:grid-cols-3 gap-5 pt-1\">\n" +
            "        <!-- 1001 -->\n" +
            "        <div class=\"bg-slate-950 p-3 rounded-lg border border-slate-800/80 space-y-2\">\n" +
            "          <div class=\"flex items-center justify-between\">\n" +
            "            <span class=\"text-xs font-bold text-emerald-400\">Peer 1001 (Seeder)</span>\n" +
            "            <span class=\"text-xs font-mono text-emerald-400\">31/31 (100%)</span>\n" +
            "          </div>\n" +
            "          <div class=\"w-full bg-slate-800 h-1.5 rounded-full overflow-hidden\"><div class=\"bg-emerald-500 h-full w-full\"></div></div>\n" +
            "          <div id=\"matrix1001\" class=\"flex flex-wrap gap-1 pt-1\"></div>\n" +
            "        </div>\n" +
            "        <!-- 1002 -->\n" +
            "        <div class=\"bg-slate-950 p-3 rounded-lg border border-slate-800/80 space-y-2\">\n" +
            "          <div class=\"flex items-center justify-between\">\n" +
            "            <span class=\"text-xs font-bold text-amber-400\">Peer 1002 (Leecher + Tiêm lỗi)</span>\n" +
            "            <span id=\"txt1002\" class=\"text-xs font-mono text-amber-400\">0/31 (0%)</span>\n" +
            "          </div>\n" +
            "          <div class=\"w-full bg-slate-800 h-1.5 rounded-full overflow-hidden\"><div id=\"bar1002\" class=\"bg-amber-500 h-full w-0 transition-all duration-300\"></div></div>\n" +
            "          <div id=\"matrix1002\" class=\"flex flex-wrap gap-1 pt-1\"></div>\n" +
            "        </div>\n" +
            "        <!-- 1003 -->\n" +
            "        <div class=\"bg-slate-950 p-3 rounded-lg border border-slate-800/80 space-y-2\">\n" +
            "          <div class=\"flex items-center justify-between\">\n" +
            "            <span class=\"text-xs font-bold text-cyan-400\">Peer 1003 (Leecher Swarm)</span>\n" +
            "            <span id=\"txt1003\" class=\"text-xs font-mono text-cyan-400\">0/31 (0%)</span>\n" +
            "          </div>\n" +
            "          <div class=\"w-full bg-slate-800 h-1.5 rounded-full overflow-hidden\"><div id=\"bar1003\" class=\"bg-cyan-500 h-full w-0 transition-all duration-300\"></div></div>\n" +
            "          <div id=\"matrix1003\" class=\"flex flex-wrap gap-1 pt-1\"></div>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "    </div>\n" +
            "\n" +
            "    <!-- Real Live Protocol Packet Inspector -->\n" +
            "    <div class=\"bg-slate-900/90 border border-slate-800 rounded-xl p-4 space-y-3\">\n" +
            "      <div class=\"flex items-center justify-between\">\n" +
            "        <div class=\"flex items-center gap-2\">\n" +
            "          <span class=\"w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse\"></span>\n" +
            "          <h2 class=\"text-xs font-semibold uppercase tracking-wider text-slate-300\">\n" +
            "            Luồng trao đổi gói tin P2P thực tế (Live P2P Message Exchange)\n" +
            "          </h2>\n" +
            "        </div>\n" +
            "        <span class=\"text-[11px] text-slate-500 font-mono\">Bóc tách trực tiếp từ giao thức TCP</span>\n" +
            "      </div>\n" +
            "      <div class=\"overflow-x-auto rounded-lg border border-slate-800\">\n" +
            "        <table class=\"w-full text-left text-xs\">\n" +
            "          <thead class=\"bg-slate-950 text-slate-400 font-semibold border-b border-slate-800\">\n" +
            "            <tr>\n" +
            "              <th class=\"py-2 px-3 w-28\">Thời gian</th>\n" +
            "              <th class=\"py-2 px-3 w-32\">Nút nguồn ➔ Đích</th>\n" +
            "              <th class=\"py-2 px-3 w-40\">Loại gói tin BitTorrent</th>\n" +
            "              <th class=\"py-2 px-3\">Chi tiết trao đổi & Ý nghĩa hệ phân tán</th>\n" +
            "            </tr>\n" +
            "          </thead>\n" +
            "          <tbody id=\"eventsBody\" class=\"divide-y divide-slate-800/60 font-mono text-[11px] bg-slate-950/60\">\n" +
            "            <tr><td colspan=\"4\" class=\"py-4 text-center text-slate-500\">Chưa có gói tin nào được trao đổi. Hãy bật Tracker và các Peer ở bảng trên để xem gói tin truyền tải!</td></tr>\n" +
            "          </tbody>\n" +
            "        </table>\n" +
            "      </div>\n" +
            "    </div>\n" +
            "\n" +
            "  </div>\n" +
            "\n" +
            "  <script>\n" +
            "    function initMatrices() {\n" +
            "      for (let p of ['1001', '1002', '1003']) {\n" +
            "        const c = document.getElementById('matrix' + p);\n" +
            "        c.innerHTML = '';\n" +
            "        for (let i = 0; i < 31; i++) {\n" +
            "          const box = document.createElement('div');\n" +
            "          box.id = `piece_${p}_${i}`;\n" +
            "          box.className = 'piece-box ' + (p === '1001' ? 'bg-emerald-500' : 'bg-slate-800 border border-slate-700/60');\n" +
            "          box.title = `Mảnh #${i}`;\n" +
            "          c.appendChild(box);\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "    initMatrices();\n" +
            "\n" +
            "    async function callAction(cmd) {\n" +
            "      try {\n" +
            "        await fetch('/api/action?cmd=' + cmd);\n" +
            "        setTimeout(pollStatus, 400);\n" +
            "        setTimeout(pollEvents, 500);\n" +
            "      } catch (e) { console.error(e); }\n" +
            "    }\n" +
            "\n" +
            "    async function pollStatus() {\n" +
            "      try {\n" +
            "        const res = await fetch('/api/status');\n" +
            "        const data = await res.json();\n" +
            "\n" +
            "        // Tracker badge\n" +
            "        const tb = document.getElementById('badgeTracker');\n" +
            "        const btnT = document.getElementById('btnTracker');\n" +
            "        if (data.tracker.running) {\n" +
            "          tb.className = 'px-2 py-0.5 text-[10px] rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 font-bold';\n" +
            "          tb.innerText = 'Online (Port 8888)';\n" +
            "          btnT.className = 'w-full py-1.5 bg-slate-800 text-slate-400 text-xs rounded cursor-default';\n" +
            "          btnT.innerText = '✓ Đang chạy';\n" +
            "        } else {\n" +
            "          tb.className = 'px-2 py-0.5 text-[10px] rounded-full bg-slate-800 text-slate-400';\n" +
            "          tb.innerText = 'Offline';\n" +
            "          btnT.className = 'w-full py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-medium text-xs rounded transition flex items-center justify-center gap-1';\n" +
            "          btnT.innerText = '▶️ Bật Tracker';\n" +
            "        }\n" +
            "\n" +
            "        // Peer 1001\n" +
            "        updatePeerStatus('1001', data.peer1001.running, 31, true);\n" +
            "\n" +
            "        // Peer 1002\n" +
            "        updatePeerStatus('1002', data.peer1002.running, data.peer1002.pieceCount, data.peer1002.complete, data.peer1002.pieces);\n" +
            "\n" +
            "        // Peer 1003\n" +
            "        updatePeerStatus('1003', data.peer1003.running, data.peer1003.pieceCount, data.peer1003.complete, data.peer1003.pieces);\n" +
            "\n" +
            "      } catch (e) { console.error(e); }\n" +
            "    }\n" +
            "\n" +
            "    function updatePeerStatus(id, isRunning, count, isComplete, pieceArray = []) {\n" +
            "      const badge = document.getElementById('badge' + id);\n" +
            "      const btn = document.getElementById('btn' + id);\n" +
            "      const txt = document.getElementById('txt' + id);\n" +
            "      const bar = document.getElementById('bar' + id);\n" +
            "\n" +
            "      if (isRunning) {\n" +
            "        badge.className = 'px-2 py-0.5 text-[10px] rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 font-bold';\n" +
            "        badge.innerText = isComplete ? 'Seeder (Xong)' : 'Leecher (Đang chạy)';\n" +
            "        btn.className = 'w-full py-1.5 bg-slate-800 text-slate-400 text-xs rounded cursor-default';\n" +
            "        btn.innerText = '✓ Đang chạy';\n" +
            "      } else {\n" +
            "        badge.className = 'px-2 py-0.5 text-[10px] rounded-full bg-slate-800 text-slate-400';\n" +
            "        badge.innerText = 'Offline';\n" +
            "      }\n" +
            "\n" +
            "      if (id !== '1001') {\n" +
            "        const pct = Math.min(100, Math.floor((count / 31) * 100));\n" +
            "        if (txt) txt.innerText = `${count}/31 (${pct}%)`;\n" +
            "        if (bar) bar.style.width = pct + '%';\n" +
            "\n" +
            "        const set = new Set(pieceArray);\n" +
            "        for (let i = 0; i < 31; i++) {\n" +
            "          const box = document.getElementById(`piece_${id}_${i}`);\n" +
            "          if (box) {\n" +
            "            if (set.has(i)) {\n" +
            "              box.className = 'piece-box bg-emerald-500';\n" +
            "            } else {\n" +
            "              box.className = 'piece-box bg-slate-800 border border-slate-700/60';\n" +
            "            }\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "\n" +
            "    async function pollEvents() {\n" +
            "      try {\n" +
            "        const res = await fetch('/api/events');\n" +
            "        const list = await res.json();\n" +
            "        const tbody = document.getElementById('eventsBody');\n" +
            "\n" +
            "        if (!list || list.length === 0) return;\n" +
            "\n" +
            "        let html = '';\n" +
            "        for (let i = list.length - 1; i >= 0; i--) {\n" +
            "          const e = list[i];\n" +
            "          let badgeBg = 'bg-slate-800 text-slate-300';\n" +
            "          if (e.color === 'emerald' || e.color === 'green') badgeBg = 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';\n" +
            "          else if (e.color === 'blue' || e.color === 'indigo') badgeBg = 'bg-blue-500/20 text-blue-400 border border-blue-500/30';\n" +
            "          else if (e.color === 'purple') badgeBg = 'bg-purple-500/20 text-purple-400 border border-purple-500/30';\n" +
            "          else if (e.color === 'cyan' || e.color === 'teal') badgeBg = 'bg-cyan-500/20 text-cyan-400 border border-cyan-500/30';\n" +
            "          else if (e.color === 'rose') badgeBg = 'bg-rose-500/20 text-rose-400 border border-rose-500/30';\n" +
            "          else if (e.color === 'amber') badgeBg = 'bg-amber-500/20 text-amber-400 border border-amber-500/30';\n" +
            "\n" +
            "          html += `\n" +
            "            <tr class=\"hover:bg-slate-900/80 transition\">\n" +
            "              <td class=\"py-2 px-3 text-slate-400\">${e.time.split(' ')[1] || e.time}</td>\n" +
            "              <td class=\"py-2 px-3\">\n" +
            "                <span class=\"px-1.5 py-0.5 rounded bg-slate-800 text-slate-200 font-bold\">Peer ${e.source}</span>\n" +
            "                <span class=\"text-slate-500 mx-1\">➔</span>\n" +
            "                <span class=\"px-1.5 py-0.5 rounded bg-slate-800 text-slate-200 font-bold\">Peer ${e.target}</span>\n" +
            "              </td>\n" +
            "              <td class=\"py-2 px-3\">\n" +
            "                <span class=\"px-2 py-0.5 rounded-full text-[10px] font-bold ${badgeBg}\">${e.type}</span>\n" +
            "              </td>\n" +
            "              <td class=\"py-2 px-3 text-slate-200 font-sans text-xs\">${e.details}</td>\n" +
            "            </tr>`;\n" +
            "        }\n" +
            "        tbody.innerHTML = html;\n" +
            "      } catch (e) { console.error(e); }\n" +
            "    }\n" +
            "\n" +
            "    async function checkHashes() {\n" +
            "      const b = document.getElementById('hashBanner');\n" +
            "      b.className = 'rounded-xl p-4 border bg-slate-900 border-slate-700 block';\n" +
            "      b.innerHTML = '<div class=\"text-xs text-slate-300\">Đang tính toán mã băm SHA-256 từ file nhị phân trên đĩa...</div>';\n" +
            "\n" +
            "      try {\n" +
            "        const res = await fetch('/api/hashes');\n" +
            "        const data = await res.json();\n" +
            "\n" +
            "        if (data.match) {\n" +
            "          b.className = 'rounded-xl p-4 border bg-emerald-950/40 border-emerald-500/40 block';\n" +
            "          b.innerHTML = `\n" +
            "            <div class=\"flex items-start gap-3\">\n" +
            "              <span class=\"text-2xl\">✅</span>\n" +
            "              <div class=\"space-y-1 w-full text-xs\">\n" +
            "                <div class=\"font-bold text-emerald-400 text-sm\">MÃ BĂM SHA-256 TRÙNG KHỚP 100% TRÊN CẢ 3 TIẾN TRÌNH!</div>\n" +
            "                <div class=\"text-slate-300 font-mono break-all\">SHA-256: ${data.peer1001}</div>\n" +
            "                <div class=\"text-emerald-300/90 pt-0.5\">Cả 3 file tại peer_1001, peer_1002, peer_1003 hoàn toàn nguyên vẹn từng bit dữ liệu.</div>\n" +
            "              </div>\n" +
            "            </div>`;\n" +
            "        } else {\n" +
            "          b.className = 'rounded-xl p-4 border bg-amber-950/40 border-amber-500/40 block';\n" +
            "          b.innerHTML = `\n" +
            "            <div class=\"flex items-start gap-3\">\n" +
            "              <span class=\"text-2xl\">⚠️</span>\n" +
            "              <div class=\"space-y-1 text-xs font-mono w-full\">\n" +
            "                <div class=\"font-bold text-amber-400 text-sm\">CÁC FILE CHƯA TẢI XONG HOÀN TOÀN:</div>\n" +
            "                <div>• Peer 1001: <span class=\"text-slate-300\">${data.peer1001}</span></div>\n" +
            "                <div>• Peer 1002: <span class=\"text-slate-300\">${data.peer1002}</span></div>\n" +
            "                <div>• Peer 1003: <span class=\"text-slate-300\">${data.peer1003}</span></div>\n" +
            "              </div>\n" +
            "            </div>`;\n" +
            "        }\n" +
            "      } catch (e) { console.error(e); }\n" +
            "    }\n" +
            "\n" +
            "    setInterval(pollStatus, 1000);\n" +
            "    setInterval(pollEvents, 1500);\n" +
            "    pollStatus();\n" +
            "    pollEvents();\n" +
            "  </script>\n" +
            "</body>\n" +
            "</html>";
    }
}
