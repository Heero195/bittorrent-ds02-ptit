import java.io.File;
import src.Metadata;
import src.TrackerAdmin;
import src.TrackerServer;

public class TrackerProcess {
    public static void main(String[] args) {
        int port = 8888;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port number specified. Defaulting to 8888.");
            }
        }

        TrackerAdmin trackerAdmin = new TrackerAdmin(60000);

        if (args.length > 1) {
            String metaPath = args[1];
            try {
                File metaFile = new File(metaPath);
                if (metaFile.exists()) {
                    Metadata metadata = Metadata.loadFromFile(metaFile);
                    trackerAdmin.setFileMetadata(metadata);
                    System.out.println("[TRACKER] Loaded initial metadata for file: " + metadata.getFileName());
                } else {
                    System.err.println("[TRACKER] Specified metadata file does not exist: " + metaPath);
                }
            } catch (Exception e) {
                System.err.println("[TRACKER] Failed to load metadata: " + e.getMessage());
            }
        }

        TrackerServer server = new TrackerServer(port, trackerAdmin);
        Thread trackerThread = new Thread(server);
        trackerThread.start();

        System.out.println("=================================================");
        System.out.println("   BitTorrent Tracker Server Running on Port " + port);
        System.out.println("=================================================");
    }
}
