package src;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WebDashboard {
    private static final int PORT = 8080;
    private static final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private static final File WORK_DIR = new File(".").getAbsoluteFile();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new IndexHandler());
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/api/action", new ActionHandler());
        server.createContext("/api/hashes", new HashHandler());
        server.createContext("/api/logs", new LogHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println("==================================================================");
        System.out.println("   🌐 BitTorrent P2P Web Dashboard Started Successfully!");
        System.out.println("   👉 Open your browser at: http://localhost:" + PORT);
        System.out.println("==================================================================");

        // Try to open browser automatically
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "http://localhost:" + PORT).start();
            }
        } catch (Exception ignored) {}
    }

    private static String calculateSHA256(File file) {
        if (!file.exists()) return "FILE_NOT_FOUND";
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
            return "ERROR: " + e.getMessage();
        }
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

            boolean trackerRunning = isProcessAlive("tracker");
            boolean peer1001Running = isProcessAlive("peer1001");
            boolean peer1002Running = isProcessAlive("peer1002");
            boolean peer1003Running = isProcessAlive("peer1003");

            long size1001 = f1001.exists() ? f1001.length() : 0;
            long size1002 = f1002.exists() ? f1002.length() : 0;
            long size1003 = f1003.exists() ? f1003.length() : 0;

            String json = String.format(Locale.US,
                "{" +
                "\"tracker\": {\"running\": %b, \"port\": 8888}," +
                "\"peer1001\": {\"running\": %b, \"size\": %d, \"complete\": %b}," +
                "\"peer1002\": {\"running\": %b, \"size\": %d, \"complete\": %b}," +
                "\"peer1003\": {\"running\": %b, \"size\": %d, \"complete\": %b}" +
                "}",
                trackerRunning,
                peer1001Running, size1001, size1001 >= 1000000,
                peer1002Running, size1002, size1002 >= 1000000,
                peer1003Running, size1003, size1003 >= 1000000
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
                    case "run_all":
                        new Thread(() -> {
                            try {
                                startTracker();
                                Thread.sleep(1500);
                                startPeer("peer1001", "1001", false);
                                Thread.sleep(1500);
                                startPeer("peer1002", "1002", true);
                                Thread.sleep(1500);
                                startPeer("peer1003", "1003", false);
                            } catch (Exception ignored) {}
                        }).start();
                        break;
                    case "stop_all":
                        stopAll();
                        break;
                    case "clean":
                        stopAll();
                        new File("peer_1002/thefile").delete();
                        new File("peer_1003/thefile").delete();
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

            boolean match = h1001.equals(h1002) && h1002.equals(h1003) && !h1001.contains("NOT_FOUND");

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
                    int start = Math.max(0, lines.size() - 25);
                    for (int i = start; i < lines.size(); i++) {
                        sb.append(lines.get(i)).append("\n");
                    }
                }
            } else {
                sb.append("Log file not created yet for peer ").append(peer);
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
        if (!isProcessAlive("tracker")) {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", "bin", "TrackerProcess", "8888", "thefile.meta");
            pb.redirectErrorStream(true);
            activeProcesses.put("tracker", pb.start());
        }
    }

    private static synchronized void startPeer(String key, String peerId, boolean injectFault) throws IOException {
        if (!isProcessAlive(key)) {
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

        // Also force kill lingering Java processes on Windows if any
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
            "  <title>BitTorrent P2P Live Dashboard</title>\n" +
            "  <script src=\"https://cdn.tailwindcss.com\"></script>\n" +
            "  <style>\n" +
            "    @keyframes pulse-slow { 0%, 100% { opacity: 1; } 50% { opacity: .4; } }\n" +
            "    .pulse-slow { animation: pulse-slow 2s cubic-bezier(0.4, 0, 0.6, 1) infinite; }\n" +
            "    .piece-box { width: 14px; height: 14px; border-radius: 2px; transition: all 0.3s; }\n" +
            "  </style>\n" +
            "</head>\n" +
            "<body class=\"bg-slate-950 text-slate-100 min-h-screen p-4 md:p-8 font-sans\">\n" +
            "  <div class=\"max-w-7xl mx-auto space-y-6\">\n" +
            "    <!-- Header -->\n" +
            "    <div class=\"flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b border-slate-800 pb-5\">\n" +
            "      <div>\n" +
            "        <div class=\"flex items-center gap-3\">\n" +
            "          <span class=\"p-2 bg-indigo-600/30 text-indigo-400 rounded-lg text-2xl\">⚡</span>\n" +
            "          <div>\n" +
            "            <h1 class=\"text-2xl font-bold tracking-tight text-white\">BitTorrent P2P Swarm Dashboard</h1>\n" +
            "            <p class=\"text-xs md:text-sm text-slate-400\">Đồ án Hệ phân tán (DS02) — Giám sát truyền tải đa luồng & Chịu lỗi thời gian thực</p>\n" +
            "          </div>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "      <div class=\"flex flex-wrap items-center gap-2\">\n" +
            "        <button onclick=\"callAction('run_all')\" class=\"px-4 py-2 bg-indigo-600 hover:bg-indigo-500 font-medium text-sm rounded-lg shadow-lg shadow-indigo-600/30 transition flex items-center gap-2\">\n" +
            "          <span>▶️</span> Chạy 1-Click Tất cả\n" +
            "        </button>\n" +
            "        <button onclick=\"checkHashes()\" class=\"px-4 py-2 bg-emerald-600 hover:bg-emerald-500 font-medium text-sm rounded-lg shadow transition flex items-center gap-2\">\n" +
            "          <span>🔐</span> Đối chiếu SHA-256\n" +
            "        </button>\n" +
            "        <button onclick=\"callAction('stop_all')\" class=\"px-3 py-2 bg-rose-900/60 hover:bg-rose-800 text-rose-200 text-sm rounded-lg border border-rose-700/50 transition\">\n" +
            "          🛑 Dừng tất cả\n" +
            "        </button>\n" +
            "        <button onclick=\"callAction('clean')\" class=\"px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm rounded-lg border border-slate-700 transition\">\n" +
            "          🧹 Reset file tải\n" +
            "        </button>\n" +
            "      </div>\n" +
            "    </div>\n" +
            "\n" +
            "    <!-- Hash Notification Banner -->\n" +
            "    <div id=\"hashBanner\" class=\"hidden rounded-xl p-4 border transition-all\"></div>\n" +
            "\n" +
            "    <!-- Grid Overview -->\n" +
            "    <div class=\"grid grid-cols-1 md:grid-cols-4 gap-4\">\n" +
            "      <!-- Tracker Card -->\n" +
            "      <div class=\"bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between\">\n" +
            "        <div>\n" +
            "          <div class=\"flex items-center justify-between\">\n" +
            "            <span class=\"text-xs font-semibold uppercase tracking-wider text-slate-400\">Máy chủ Tracker</span>\n" +
            "            <span id=\"trackerBadge\" class=\"px-2 py-0.5 text-xs rounded-full bg-slate-800 text-slate-400\">Chưa bật</span>\n" +
            "          </div>\n" +
            "          <div class=\"mt-3 text-lg font-bold text-white\">Cổng: 8888</div>\n" +
            "          <p class=\"text-xs text-slate-400 mt-1\">Quản lý danh bạ Swarm (Không truyền file tệp tin).</p>\n" +
            "        </div>\n" +
            "        <div class=\"mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between\">\n" +
            "          <span class=\"text-xs text-slate-500\">Metadata: thefile.meta</span>\n" +
            "          <button onclick=\"callAction('start_tracker')\" class=\"text-xs px-2.5 py-1 bg-slate-800 hover:bg-slate-700 rounded text-slate-200 border border-slate-700\">Bật</button>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "\n" +
            "      <!-- Peer 1001 (Seeder) -->\n" +
            "      <div class=\"bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between\">\n" +
            "        <div>\n" +
            "          <div class=\"flex items-center justify-between\">\n" +
            "            <span class=\"text-xs font-semibold uppercase tracking-wider text-emerald-400\">Peer 1001 (Seeder)</span>\n" +
            "            <span id=\"badge1001\" class=\"px-2 py-0.5 text-xs rounded-full bg-slate-800 text-slate-400\">Offline</span>\n" +
            "          </div>\n" +
            "          <div class=\"mt-3 flex items-baseline justify-between\">\n" +
            "            <div class=\"text-lg font-bold text-white\">Tiến độ: <span id=\"pct1001\">100%</span></div>\n" +
            "            <span class=\"text-xs text-emerald-400 font-mono\">31/31 Pieces</span>\n" +
            "          </div>\n" +
            "          <div class=\"w-full bg-slate-800 h-2 rounded-full mt-2 overflow-hidden\">\n" +
            "            <div id=\"bar1001\" class=\"bg-emerald-500 h-full w-full transition-all\"></div>\n" +
            "          </div>\n" +
            "        </div>\n" +
            "        <div class=\"mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between\">\n" +
            "          <span class=\"text-xs text-slate-500\">Cổng: 6001</span>\n" +
            "          <button onclick=\"callAction('start_1001')\" class=\"text-xs px-2.5 py-1 bg-slate-800 hover:bg-slate-700 rounded text-slate-200 border border-slate-700\">Bật</button>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "\n" +
            "      <!-- Peer 1002 (Leecher + Fault) -->\n" +
            "      <div class=\"bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between\">\n" +
            "        <div>\n" +
            "          <div class=\"flex items-center justify-between\">\n" +
            "            <span class=\"text-xs font-semibold uppercase tracking-wider text-amber-400\">Peer 1002 (Tiêm lỗi)</span>\n" +
            "            <span id=\"badge1002\" class=\"px-2 py-0.5 text-xs rounded-full bg-slate-800 text-slate-400\">Offline</span>\n" +
            "          </div>\n" +
            "          <div class=\"mt-3 flex items-baseline justify-between\">\n" +
            "            <div class=\"text-lg font-bold text-white\">Tiến độ: <span id=\"pct1002\">0%</span></div>\n" +
            "            <span id=\"pieces1002\" class=\"text-xs text-slate-400 font-mono\">0/31 Pieces</span>\n" +
            "          </div>\n" +
            "          <div class=\"w-full bg-slate-800 h-2 rounded-full mt-2 overflow-hidden\">\n" +
            "            <div id=\"bar1002\" class=\"bg-amber-500 h-full w-0 transition-all duration-300\"></div>\n" +
            "          </div>\n" +
            "        </div>\n" +
            "        <div class=\"mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between\">\n" +
            "          <span class=\"text-xs text-amber-400/80\">⚠️ Tiêm lỗi mảnh #2</span>\n" +
            "          <button onclick=\"callAction('start_1002')\" class=\"text-xs px-2.5 py-1 bg-slate-800 hover:bg-slate-700 rounded text-slate-200 border border-slate-700\">Bật</button>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "\n" +
            "      <!-- Peer 1003 (Leecher Swarm) -->\n" +
            "      <div class=\"bg-slate-900/80 border border-slate-800 rounded-xl p-4 flex flex-col justify-between\">\n" +
            "        <div>\n" +
            "          <div class=\"flex items-center justify-between\">\n" +
            "            <span class=\"text-xs font-semibold uppercase tracking-wider text-cyan-400\">Peer 1003 (Swarm)</span>\n" +
            "            <span id=\"badge1003\" class=\"px-2 py-0.5 text-xs rounded-full bg-slate-800 text-slate-400\">Offline</span>\n" +
            "          </div>\n" +
            "          <div class=\"mt-3 flex items-baseline justify-between\">\n" +
            "            <div class=\"text-lg font-bold text-white\">Tiến độ: <span id=\"pct1003\">0%</span></div>\n" +
            "            <span id=\"pieces1003\" class=\"text-xs text-slate-400 font-mono\">0/31 Pieces</span>\n" +
            "          </div>\n" +
            "          <div class=\"w-full bg-slate-800 h-2 rounded-full mt-2 overflow-hidden\">\n" +
            "            <div id=\"bar1003\" class=\"bg-cyan-500 h-full w-0 transition-all duration-300\"></div>\n" +
            "          </div>\n" +
            "        </div>\n" +
            "        <div class=\"mt-4 pt-3 border-t border-slate-800/80 flex items-center justify-between\">\n" +
            "          <span class=\"text-xs text-slate-500\">Cổng: 6003 (Tit-for-Tat)</span>\n" +
            "          <button onclick=\"callAction('start_1003')\" class=\"text-xs px-2.5 py-1 bg-slate-800 hover:bg-slate-700 rounded text-slate-200 border border-slate-700\">Bật</button>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "    </div>\n" +
            "\n" +
            "    <!-- Interactive Piece Matrix Visualization -->\n" +
            "    <div class=\"bg-slate-900/80 border border-slate-800 rounded-xl p-5 space-y-4\">\n" +
            "      <div class=\"flex items-center justify-between\">\n" +
            "        <h2 class=\"text-base font-semibold text-white flex items-center gap-2\">\n" +
            "          <span>🧩</span> Trực quan hóa Ma trận 31 mảnh dữ liệu (Piece Matrix Grid)\n" +
            "        </h2>\n" +
            "        <div class=\"flex items-center gap-4 text-xs text-slate-400\">\n" +
            "          <div class=\"flex items-center gap-1.5\"><div class=\"w-3 h-3 rounded bg-emerald-500\"></div> Đã tải (Valid)</div>\n" +
            "          <div class=\"flex items-center gap-1.5\"><div class=\"w-3 h-3 rounded bg-rose-500 animate-pulse\"></div> Mảnh lỗi (Rejected)</div>\n" +
            "          <div class=\"flex items-center gap-1.5\"><div class=\"w-3 h-3 rounded bg-slate-800 border border-slate-700\"></div> Chưa có</div>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "      <div class=\"grid grid-cols-1 md:grid-cols-3 gap-6 pt-2\">\n" +
            "        <div>\n" +
            "          <div class=\"text-xs text-slate-400 mb-2 font-medium\">Peer 1001 (Seeder - Nguồn gốc)</div>\n" +
            "          <div id=\"matrix1001\" class=\"flex flex-wrap gap-1.5\"></div>\n" +
            "        </div>\n" +
            "        <div>\n" +
            "          <div class=\"text-xs text-slate-400 mb-2 font-medium\">Peer 1002 (Leecher - Có tiêm lỗi mảnh #2)</div>\n" +
            "          <div id=\"matrix1002\" class=\"flex flex-wrap gap-1.5\"></div>\n" +
            "        </div>\n" +
            "        <div>\n" +
            "          <div class=\"text-xs text-slate-400 mb-2 font-medium\">Peer 1003 (Leecher - Tải chéo Swarm)</div>\n" +
            "          <div id=\"matrix1003\" class=\"flex flex-wrap gap-1.5\"></div>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "    </div>\n" +
            "\n" +
            "    <!-- Real-time Live Log Feed -->\n" +
            "    <div class=\"bg-slate-900/80 border border-slate-800 rounded-xl p-5 space-y-3\">\n" +
            "      <div class=\"flex items-center justify-between\">\n" +
            "        <div class=\"flex items-center gap-2\">\n" +
            "          <span class=\"w-2.5 h-2.5 rounded-full bg-emerald-500 animate-pulse\"></span>\n" +
            "          <h2 class=\"text-base font-semibold text-white\">Nhật ký Sự kiện Thời gian thực (Live Event Stream)</h2>\n" +
            "        </div>\n" +
            "        <div class=\"flex items-center gap-2\">\n" +
            "          <button onclick=\"setLogPeer('1001')\" id=\"btnLog1001\" class=\"px-2.5 py-1 text-xs rounded bg-slate-800 text-slate-400 hover:text-white\">Peer 1001</button>\n" +
            "          <button onclick=\"setLogPeer('1002')\" id=\"btnLog1002\" class=\"px-2.5 py-1 text-xs rounded bg-indigo-600 text-white font-medium\">Peer 1002 (Log)</button>\n" +
            "          <button onclick=\"setLogPeer('1003')\" id=\"btnLog1003\" class=\"px-2.5 py-1 text-xs rounded bg-slate-800 text-slate-400 hover:text-white\">Peer 1003</button>\n" +
            "        </div>\n" +
            "      </div>\n" +
            "      <div class=\"bg-slate-950 border border-slate-800/80 rounded-lg p-3.5 font-mono text-xs text-slate-300 h-52 overflow-y-auto whitespace-pre-wrap leading-relaxed\" id=\"logConsole\">\n" +
            "Đang chờ tiến trình khởi động..." +
            "      </div>\n" +
            "    </div>\n" +
            "  </div>\n" +
            "\n" +
            "  <script>\n" +
            "    let activeLogPeer = '1002';\n" +
            "\n" +
            "    function initMatrices() {\n" +
            "      for (let p of ['1001', '1002', '1003']) {\n" +
            "        const container = document.getElementById('matrix' + p);\n" +
            "        container.innerHTML = '';\n" +
            "        for (let i = 0; i < 31; i++) {\n" +
            "          const box = document.createElement('div');\n" +
            "          box.id = `piece_${p}_${i}`;\n" +
            "          box.className = 'piece-box ' + (p === '1001' ? 'bg-emerald-500' : 'bg-slate-800 border border-slate-700/60');\n" +
            "          box.title = `Piece #${i}`;\n" +
            "          container.appendChild(box);\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "    initMatrices();\n" +
            "\n" +
            "    function setLogPeer(peerId) {\n" +
            "      activeLogPeer = peerId;\n" +
            "      for (let p of ['1001', '1002', '1003']) {\n" +
            "        const btn = document.getElementById('btnLog' + p);\n" +
            "        if (p === peerId) {\n" +
            "          btn.className = 'px-2.5 py-1 text-xs rounded bg-indigo-600 text-white font-medium';\n" +
            "        } else {\n" +
            "          btn.className = 'px-2.5 py-1 text-xs rounded bg-slate-800 text-slate-400 hover:text-white';\n" +
            "        }\n" +
            "      }\n" +
            "      pollLog();\n" +
            "    }\n" +
            "\n" +
            "    async function callAction(cmd) {\n" +
            "      try {\n" +
            "        await fetch('/api/action?cmd=' + cmd);\n" +
            "        pollStatus();\n" +
            "        pollLog();\n" +
            "      } catch (e) { console.error(e); }\n" +
            "    }\n" +
            "\n" +
            "    async function pollStatus() {\n" +
            "      try {\n" +
            "        const res = await fetch('/api/status');\n" +
            "        const data = await res.json();\n" +
            "\n" +
            "        // Tracker badge\n" +
            "        const tb = document.getElementById('trackerBadge');\n" +
            "        if (data.tracker.running) {\n" +
            "          tb.className = 'px-2 py-0.5 text-xs rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';\n" +
            "          tb.innerText = 'Đang chạy';\n" +
            "        } else {\n" +
            "          tb.className = 'px-2 py-0.5 text-xs rounded-full bg-slate-800 text-slate-400';\n" +
            "          tb.innerText = 'Chưa bật';\n" +
            "        }\n" +
            "\n" +
            "        // Peers\n" +
            "        updatePeerUI('1001', data.peer1001);\n" +
            "        updatePeerUI('1002', data.peer1002);\n" +
            "        updatePeerUI('1003', data.peer1003);\n" +
            "      } catch (e) { console.error(e); }\n" +
            "    }\n" +
            "\n" +
            "    function updatePeerUI(id, info) {\n" +
            "      const badge = document.getElementById('badge' + id);\n" +
            "      const bar = document.getElementById('bar' + id);\n" +
            "      const pct = document.getElementById('pct' + id);\n" +
            "      const piecesText = document.getElementById('pieces' + id);\n" +
            "\n" +
            "      if (info.running) {\n" +
            "        badge.className = 'px-2 py-0.5 text-xs rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';\n" +
            "        badge.innerText = 'Online';\n" +
            "      } else {\n" +
            "        badge.className = 'px-2 py-0.5 text-xs rounded-full bg-slate-800 text-slate-400';\n" +
            "        badge.innerText = 'Offline';\n" +
            "      }\n" +
            "\n" +
            "      const pieceCount = Math.min(31, Math.floor(info.size / 32768) + (info.size >= 1000000 ? 1 : 0));\n" +
            "      const percent = Math.min(100, Math.floor((info.size / 1000000) * 100));\n" +
            "\n" +
            "      pct.innerText = percent + '%';\n" +
            "      bar.style.width = percent + '%';\n" +
            "      if (piecesText) piecesText.innerText = pieceCount + '/31 Pieces';\n" +
            "\n" +
            "      // Update matrix blocks\n" +
            "      if (id !== '1001') {\n" +
            "        for (let i = 0; i < 31; i++) {\n" +
            "          const box = document.getElementById(`piece_${id}_${i}`);\n" +
            "          if (box) {\n" +
            "            if (i < pieceCount) {\n" +
            "              box.className = 'piece-box bg-emerald-500';\n" +
            "            } else {\n" +
            "              box.className = 'piece-box bg-slate-800 border border-slate-700/60';\n" +
            "            }\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "\n" +
            "    async function pollLog() {\n" +
            "      try {\n" +
            "        const res = await fetch('/api/logs?peer=' + activeLogPeer);\n" +
            "        const text = await res.text();\n" +
            "        const consoleBox = document.getElementById('logConsole');\n" +
            "        consoleBox.innerText = text;\n" +
            "        consoleBox.scrollTop = consoleBox.scrollHeight;\n" +
            "      } catch (e) { console.error(e); }\n" +
            "    }\n" +
            "\n" +
            "    async function checkHashes() {\n" +
            "      const banner = document.getElementById('hashBanner');\n" +
            "      banner.className = 'rounded-xl p-4 border bg-slate-900 border-slate-700 block';\n" +
            "      banner.innerHTML = '<div class=\"text-sm text-slate-300\">Đang tính toán mã băm SHA-256 trên cả 3 file...</div>';\n" +
            "\n" +
            "      try {\n" +
            "        const res = await fetch('/api/hashes');\n" +
            "        const data = await res.json();\n" +
            "\n" +
            "        if (data.match) {\n" +
            "          banner.className = 'rounded-xl p-5 border bg-emerald-950/40 border-emerald-500/40 block';\n" +
            "          banner.innerHTML = `\n" +
            "            <div class=\"flex items-start gap-3\">\n" +
            "              <span class=\"text-2xl\">✅</span>\n" +
            "              <div class=\"space-y-1.5 w-full\">\n" +
            "                <div class=\"font-bold text-emerald-400 text-sm md:text-base\">MÃ BĂM SHA-256 TRÙNG KHỚP 100% TRÊN CẢ 3 TIẾN TRÌNH!</div>\n" +
            "                <div class=\"text-xs text-slate-300 font-mono break-all\">SHA-256: ${data.peer1001}</div>\n" +
            "                <div class=\"text-xs text-emerald-300/80 pt-1\">Chứng minh dữ liệu tải về từ mạng ngang hàng hoàn toàn nguyên vẹn, không bị lỗi dù có tiêm lỗi mảnh #2!</div>\n" +
            "              </div>\n" +
            "            </div>`;\n" +
            "        } else {\n" +
            "          banner.className = 'rounded-xl p-5 border bg-amber-950/40 border-amber-500/40 block';\n" +
            "          banner.innerHTML = `\n" +
            "            <div class=\"flex items-start gap-3\">\n" +
            "              <span class=\"text-2xl\">⚠️</span>\n" +
            "              <div class=\"space-y-1 text-xs font-mono\">\n" +
            "                <div class=\"font-bold text-amber-400 text-sm\">CÁC NÚT ĐANG TRONG QUÁ TRÌNH TẢI (CHƯA HOÀN TẤT ĐỦ 31 MẢNH):</div>\n" +
            "                <div>• Peer 1001: ${data.peer1001}</div>\n" +
            "                <div>• Peer 1002: ${data.peer1002}</div>\n" +
            "                <div>• Peer 1003: ${data.peer1003}</div>\n" +
            "              </div>\n" +
            "            </div>`;\n" +
            "        }\n" +
            "      } catch (e) { console.error(e); }\n" +
            "    }\n" +
            "\n" +
            "    setInterval(pollStatus, 1500);\n" +
            "    setInterval(pollLog, 2000);\n" +
            "    pollStatus();\n" +
            "  </script>\n" +
            "</body>\n" +
            "</html>";
    }
}
