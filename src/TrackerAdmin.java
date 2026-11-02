package src;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrackerAdmin {

    public static class PeerRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        private String peerId;
        private String host;
        private int port;
        private BitSet bitSet;
        private long lastSeen;
        private boolean isSeeder;

        public PeerRecord(String peerId, String host, int port, BitSet bitSet, boolean isSeeder) {
            this.peerId = peerId;
            this.host = host;
            this.port = port;
            this.bitSet = bitSet != null ? (BitSet) bitSet.clone() : new BitSet();
            this.lastSeen = System.currentTimeMillis();
            this.isSeeder = isSeeder;
        }

        public String getPeerId() { return peerId; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public BitSet getBitSet() { return bitSet; }
        public long getLastSeen() { return lastSeen; }
        public boolean isSeeder() { return isSeeder; }

        public void update(BitSet newBitSet, boolean isSeeder) {
            if (newBitSet != null) {
                this.bitSet = (BitSet) newBitSet.clone();
            }
            this.isSeeder = isSeeder;
            this.lastSeen = System.currentTimeMillis();
        }

        public void touch() {
            this.lastSeen = System.currentTimeMillis();
        }
    }

    private final Map<String, PeerRecord> peerMap;
    private Metadata fileMetadata;
    private final long timeoutMillis;

    public TrackerAdmin(long timeoutMillis) {
        this.peerMap = new ConcurrentHashMap<>();
        this.timeoutMillis = timeoutMillis;
    }

    public synchronized void setFileMetadata(Metadata metadata) {
        this.fileMetadata = metadata;
    }

    public synchronized Metadata getFileMetadata() {
        return fileMetadata;
    }

    public synchronized void registerPeer(String peerId, String host, int port, BitSet bitSet, boolean isSeeder) {
        PeerRecord record = peerMap.get(peerId);
        if (record == null) {
            peerMap.put(peerId, new PeerRecord(peerId, host, port, bitSet, isSeeder));
            System.out.println("[TRACKER] Registered peer: " + peerId + " @ " + host + ":" + port + " (Seeder: " + isSeeder + ")");
        } else {
            record.update(bitSet, isSeeder);
            System.out.println("[TRACKER] Updated peer: " + peerId + " (Seeder: " + isSeeder + ")");
        }
    }

    public synchronized void announcePeer(String peerId, BitSet bitSet, boolean isSeeder) {
        PeerRecord record = peerMap.get(peerId);
        if (record != null) {
            record.update(bitSet, isSeeder);
        }
    }

    public synchronized void unregisterPeer(String peerId) {
        peerMap.remove(peerId);
        System.out.println("[TRACKER] Unregistered peer: " + peerId);
    }

    public synchronized List<PeerRecord> getActivePeers(String requestingPeerId) {
        purgeStalePeers();
        List<PeerRecord> list = new ArrayList<>();
        for (Map.Entry<String, PeerRecord> entry : peerMap.entrySet()) {
            if (!entry.getKey().equals(requestingPeerId)) {
                list.add(entry.getValue());
            }
        }
        return list;
    }

    public synchronized Map<String, PeerRecord> getPeerMap() {
        purgeStalePeers();
        return new HashMap<>(peerMap);
    }

    private void purgeStalePeers() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, PeerRecord>> it = peerMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, PeerRecord> entry = it.next();
            if (now - entry.getValue().getLastSeen() > timeoutMillis) {
                System.out.println("[TRACKER] Removing timed-out peer: " + entry.getKey());
                it.remove();
            }
        }
    }
}
