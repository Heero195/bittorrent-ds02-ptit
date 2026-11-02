package src;

import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class Metadata implements Serializable {
    private static final long serialVersionUID = 1L;

    private String fileId;
    private String fileName;
    private long fileSize;
    private int pieceSize;
    private int pieceCount;
    private List<String> pieceHashes;
    private String fileHash;
    private String trackerUrl;

    public Metadata() {
        this.pieceHashes = new ArrayList<>();
    }

    public Metadata(String fileId, String fileName, long fileSize, int pieceSize, int pieceCount, List<String> pieceHashes, String fileHash, String trackerUrl) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.pieceSize = pieceSize;
        this.pieceCount = pieceCount;
        this.pieceHashes = pieceHashes != null ? pieceHashes : new ArrayList<>();
        this.fileHash = fileHash;
        this.trackerUrl = trackerUrl;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public int getPieceSize() { return pieceSize; }
    public void setPieceSize(int pieceSize) { this.pieceSize = pieceSize; }

    public int getPieceCount() { return pieceCount; }
    public void setPieceCount(int pieceCount) { this.pieceCount = pieceCount; }

    public List<String> getPieceHashes() { return pieceHashes; }
    public void setPieceHashes(List<String> pieceHashes) { this.pieceHashes = pieceHashes; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public String getTrackerUrl() { return trackerUrl; }
    public void setTrackerUrl(String trackerUrl) { this.trackerUrl = trackerUrl; }

    public String getPieceHash(int index) {
        if (pieceHashes != null && index >= 0 && index < pieceHashes.size()) {
            return pieceHashes.get(index);
        }
        return null;
    }

    public static String calculateSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public void saveToFile(File file) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(this);
        }
    }

    public static Metadata loadFromFile(File file) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            return (Metadata) ois.readObject();
        }
    }
}
