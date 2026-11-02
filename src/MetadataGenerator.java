package src;

import java.io.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MetadataGenerator {

    public static Metadata generateMetadata(File file, int pieceSize, String trackerUrl) throws IOException {
        if (!file.exists() || !file.isFile()) {
            throw new FileNotFoundException("Target file not found: " + file.getAbsolutePath());
        }

        long fileSize = file.length();
        int pieceCount = (int) Math.ceil((double) fileSize / pieceSize);
        List<String> pieceHashes = new ArrayList<>();

        MessageDigest fullFileDigest;
        try {
            fullFileDigest = MessageDigest.getInstance("SHA-256");
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm missing", e);
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[pieceSize];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                fullFileDigest.update(buffer, 0, bytesRead);

                byte[] actualPiece;
                if (bytesRead == pieceSize) {
                    actualPiece = buffer;
                } else {
                    actualPiece = new byte[bytesRead];
                    System.arraycopy(buffer, 0, actualPiece, 0, bytesRead);
                }
                String pieceHash = Metadata.calculateSHA256(actualPiece);
                pieceHashes.add(pieceHash);
            }
        }

        byte[] fullHashBytes = fullFileDigest.digest();
        StringBuilder fileHashHex = new StringBuilder();
        for (byte b : fullHashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) fileHashHex.append('0');
            fileHashHex.append(hex);
        }

        String fileId = UUID.randomUUID().toString();
        return new Metadata(
                fileId,
                file.getName(),
                fileSize,
                pieceSize,
                pieceCount,
                pieceHashes,
                fileHashHex.toString(),
                trackerUrl
        );
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java src.MetadataGenerator <filePath> <pieceSize> [trackerUrl] [outputMetaFile]");
            System.exit(1);
        }

        String filePath = args[0];
        int pieceSize = Integer.parseInt(args[1]);
        String trackerUrl = args.length > 2 ? args[2] : "http://localhost:8888";
        String outputPath = args.length > 3 ? args[3] : filePath + ".meta";

        try {
            File inputFile = new File(filePath);
            Metadata metadata = generateMetadata(inputFile, pieceSize, trackerUrl);
            File outputFile = new File(outputPath);
            metadata.saveToFile(outputFile);

            System.out.println("Metadata successfully generated!");
            System.out.println("File ID: " + metadata.getFileId());
            System.out.println("File Name: " + metadata.getFileName());
            System.out.println("File Size: " + metadata.getFileSize() + " bytes");
            System.out.println("Piece Size: " + metadata.getPieceSize() + " bytes");
            System.out.println("Piece Count: " + metadata.getPieceCount());
            System.out.println("File SHA-256: " + metadata.getFileHash());
            System.out.println("Metadata saved to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error generating metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
