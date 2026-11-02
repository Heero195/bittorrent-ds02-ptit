package src;

import java.io.*;
import java.net.*;
import java.util.BitSet;
import java.util.List;

public class TrackerServer implements Runnable {
    private final int port;
    private final TrackerAdmin trackerAdmin;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public TrackerServer(int port, TrackerAdmin trackerAdmin) {
        this.port = port;
        this.trackerAdmin = trackerAdmin;
        this.running = true;
    }

    public void stop() {
        this.running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("[TRACKER] Server started listening on port " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ClientHandler(clientSocket, trackerAdmin)).start();
                } catch (SocketException se) {
                    if (!running) break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("[TRACKER] Could not listen on port " + port + ": " + e.getMessage());
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final TrackerAdmin trackerAdmin;

        public ClientHandler(Socket socket, TrackerAdmin trackerAdmin) {
            this.socket = socket;
            this.trackerAdmin = trackerAdmin;
        }

        @Override
        public void run() {
            try (
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())
            ) {
                String action = (String) in.readObject();

                if ("REGISTER".equalsIgnoreCase(action)) {
                    String peerId = (String) in.readObject();
                    String host = socket.getInetAddress().getHostAddress();
                    int peerPort = in.readInt();
                    boolean isSeeder = in.readBoolean();
                    BitSet bitSet = (BitSet) in.readObject();

                    trackerAdmin.registerPeer(peerId, host, peerPort, bitSet, isSeeder);

                    out.writeObject("SUCCESS");
                    out.writeObject(trackerAdmin.getFileMetadata());
                    out.writeObject(trackerAdmin.getActivePeers(peerId));
                    out.flush();

                } else if ("ANNOUNCE".equalsIgnoreCase(action)) {
                    String peerId = (String) in.readObject();
                    boolean isSeeder = in.readBoolean();
                    BitSet bitSet = (BitSet) in.readObject();

                    trackerAdmin.announcePeer(peerId, bitSet, isSeeder);
                    out.writeObject("SUCCESS");
                    out.flush();

                } else if ("GET_PEERS".equalsIgnoreCase(action)) {
                    String peerId = (String) in.readObject();
                    List<TrackerAdmin.PeerRecord> activePeers = trackerAdmin.getActivePeers(peerId);

                    out.writeObject("SUCCESS");
                    out.writeObject(activePeers);
                    out.flush();

                } else if ("PUBLISH_METADATA".equalsIgnoreCase(action)) {
                    Metadata metadata = (Metadata) in.readObject();
                    trackerAdmin.setFileMetadata(metadata);
                    System.out.println("[TRACKER] Metadata published for file: " + metadata.getFileName());

                    out.writeObject("SUCCESS");
                    out.flush();

                } else if ("LEAVE".equalsIgnoreCase(action)) {
                    String peerId = (String) in.readObject();
                    trackerAdmin.unregisterPeer(peerId);

                    out.writeObject("SUCCESS");
                    out.flush();
                } else {
                    out.writeObject("UNKNOWN_COMMAND");
                    out.flush();
                }
            } catch (Exception e) {

            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }
    }
}
