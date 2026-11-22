package tests;

import src.Metadata;
import src.MetadataGenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;

public class TestIntegrity {

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println(" Running BitTorrent Integrity Unit Tests");
        System.out.println("=========================================");

        int passed = 0;
        int total = 0;

        total++;
        try {
            byte[] sampleData = "Hello BitTorrent P2P World!".getBytes();
            String hash1 = Metadata.calculateSHA256(sampleData);
            String hash2 = Metadata.calculateSHA256(sampleData);
            if (hash1 != null && hash1.equals(hash2) && hash1.length() == 64) {
                System.out.println("[PASS] Test 1: SHA-256 Hash Calculation (Length 64 Hex String)");
                passed++;
            } else {
                System.err.println("[FAIL] Test 1: SHA-256 Hash Calculation mismatch");
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Test 1: Exception - " + e.getMessage());
        }

        total++;
        try {
            byte[] validPiece = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
            byte[] corruptPiece = new byte[]{1, 2, 3, 4, 5, 6, 7, 9};

            String validHash = Metadata.calculateSHA256(validPiece);
            String corruptHash = Metadata.calculateSHA256(corruptPiece);

            if (!validHash.equalsIgnoreCase(corruptHash)) {
                System.out.println("[PASS] Test 2: Corrupt Chunk Detection (Hash Mismatch Detected Correctly)");
                passed++;
            } else {
                System.err.println("[FAIL] Test 2: Corrupt Chunk Hash matched valid piece!");
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Test 2: Exception - " + e.getMessage());
        }

        total++;
        try {
            File tempFile = File.createTempFile("test_sample_", ".txt");
            tempFile.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] dummyContent = new byte[10000];
                Arrays.fill(dummyContent, (byte) 0xAA);
                fos.write(dummyContent);
            }

            Metadata meta = MetadataGenerator.generateMetadata(tempFile, 2000, "http://localhost:8888");
            File metaFile = File.createTempFile("test_meta_", ".meta");
            metaFile.deleteOnExit();

            meta.saveToFile(metaFile);
            Metadata loadedMeta = Metadata.loadFromFile(metaFile);

            if (loadedMeta.getPieceCount() == meta.getPieceCount()
                    && loadedMeta.getFileHash().equals(meta.getFileHash())
                    && loadedMeta.getPieceHashes().size() == meta.getPieceHashes().size()) {
                System.out.println("[PASS] Test 3: Metadata Serialization & SHA-256 Piece Array Consistency");
                passed++;
            } else {
                System.err.println("[FAIL] Test 3: Loaded Metadata differs from generated metadata");
            }
        } catch (Exception e) {
            System.err.println("[FAIL] Test 3: Exception - " + e.getMessage());
        }

        System.out.println("=========================================");
        System.out.println(" Test Summary: " + passed + "/" + total + " Passed");
        System.out.println("=========================================");

        if (passed != total) {
            System.exit(1);
        }
    }
}
