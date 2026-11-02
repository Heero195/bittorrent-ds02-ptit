package src;

import java.io.*;
import java.net.*;
import java.util.BitSet;
import java.util.List;

public class TrackerClient {
    private final String trackerHost;
    private final int trackerPort;

    public static class RegistrationResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private final Metadata metadata;
        private final List<TrackerAdmin.PeerRecord> activePeers;

        public RegistrationResult(Metadata metadata, List<TrackerAdmin.PeerRecord> activePeers) {
            this.metadata = metadata;
            this.activePeers = activePeers;
        }

        public Metadata getMetadata() { return metadata; }
        public List<TrackerAdmin.PeerRecord> getActivePeers() { return activePeers; }
    }

    public TrackerClient(String trackerHost, int trackerPort) {
        this.trackerHost = trackerHost;
        this.trackerPort = trackerPort;
    }

    public RegistrationResult register(String peerId, int peerPort, boolean isSeeder, BitSet bitSet) throws Exception {
        try (Socket socket = new Socket(trackerHost, trackerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("REGISTER");
            out.writeObject(peerId);
            out.writeInt(peerPort);
            out.writeBoolean(isSeeder);
            out.writeObject(bitSet != null ? bitSet : new BitSet());
            out.flush();

            String response = (String) in.readObject();
            if ("SUCCESS".equals(response)) {
                Metadata metadata = (Metadata) in.readObject();
                @SuppressWarnings("unchecked")
                List<TrackerAdmin.PeerRecord> peers = (List<TrackerAdmin.PeerRecord>) in.readObject();
                return new RegistrationResult(metadata, peers);
            } else {
                throw new IOException("Tracker registration failed: " + response);
            }
        }
    }

    public boolean announce(String peerId, boolean isSeeder, BitSet bitSet) {
        try (Socket socket = new Socket(trackerHost, trackerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("ANNOUNCE");
            out.writeObject(peerId);
            out.writeBoolean(isSeeder);
            out.writeObject(bitSet != null ? bitSet : new BitSet());
            out.flush();

            String response = (String) in.readObject();
            return "SUCCESS".equals(response);
        } catch (Exception e) {
            System.err.println("[TRACKER_CLIENT] Announce failed: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public List<TrackerAdmin.PeerRecord> getPeers(String peerId) {
        try (Socket socket = new Socket(trackerHost, trackerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("GET_PEERS");
            out.writeObject(peerId);
            out.flush();

            String response = (String) in.readObject();
            if ("SUCCESS".equals(response)) {
                return (List<TrackerAdmin.PeerRecord>) in.readObject();
            }
        } catch (Exception e) {
            System.err.println("[TRACKER_CLIENT] GetPeers failed: " + e.getMessage());
        }
        return null;
    }

    public boolean publishMetadata(Metadata metadata) {
        try (Socket socket = new Socket(trackerHost, trackerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("PUBLISH_METADATA");
            out.writeObject(metadata);
            out.flush();

            String response = (String) in.readObject();
            return "SUCCESS".equals(response);
        } catch (Exception e) {
            System.err.println("[TRACKER_CLIENT] Publish metadata failed: " + e.getMessage());
            return false;
        }
    }

    public void leave(String peerId) {
        try (Socket socket = new Socket(trackerHost, trackerPort);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject("LEAVE");
            out.writeObject(peerId);
            out.flush();
        } catch (Exception ignored) {}
    }
}
