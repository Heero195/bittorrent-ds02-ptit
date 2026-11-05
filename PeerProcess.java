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
		int corruptChunkIndex = -1;

		for (int i = 1; i < args.length; i++) {
			if (args[i].equalsIgnoreCase("--corrupt-chunk") && i + 1 < args.length) {
				corruptChunkIndex = Integer.parseInt(args[i + 1]);
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
		if (corruptChunkIndex >= 0) {
			System.out.println("[FAULT_INJECTION_ACTIVE] Will inject corrupt payload for piece #" + corruptChunkIndex);
		}

		PeerAdmin admin = new PeerAdmin(peerID, trackerHost, trackerPort, corruptChunkIndex);
	}
}
