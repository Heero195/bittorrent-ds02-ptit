import java.io.*;
import java.util.*;

import src.PeerAdmin;
import src.PeerLogger;

public class PeerProcess {
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage: java PeerProcess <peerID> [trackerHost] [trackerPort] [--corrupt-chunk <index>]");
			System.exit(1);
		}

		String peerID = args[0];
		String trackerHost = null;
		int trackerPort = 0;
		String corruptChunks = null;
		int noiseRate = 0;

		for (int i = 1; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("--corrupt-chunk") && i + 1 < args.length) {
				corruptChunks = args[i + 1];
				i++;
			} else if (args[i].equalsIgnoreCase("--noise-rate") && i + 1 < args.length) {
				try {
					noiseRate = Integer.parseInt(args[i + 1]);
				} catch (NumberFormatException ignored) {}
				i++;
			} else if (trackerHost == null) {
				trackerHost = args[i];
			} else if (trackerPort == 0) {
				try {
					trackerPort = Integer.parseInt(args[i]);
				} catch (NumberFormatException ignored) {}
			}
		}

		System.out.println("Starting PeerProcess [" + peerID + "]...");
		if (trackerHost != null && trackerPort > 0) {
			System.out.println("Connecting to Tracker at " + trackerHost + ":" + trackerPort);
		}
		if (corruptChunks != null && !corruptChunks.isEmpty() && !corruptChunks.equals("-1")) {
			System.out.println("[FAULT_INJECTION_ACTIVE] Will inject corrupt payload for piece(s): " + corruptChunks);
		}
		if (noiseRate > 0) {
			System.out.println("[NETWORK_NOISE_ACTIVE] Simulating random transmission noise: " + noiseRate + "%");
		}

		PeerAdmin admin = new PeerAdmin(peerID, trackerHost, trackerPort, corruptChunks, noiseRate);
	}
}
