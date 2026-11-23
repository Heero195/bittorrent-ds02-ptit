package src;

import java.io.*;
import java.lang.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class PeerAdmin {
	private String peerID;
	private RemotePeerInfo myConfig;
	private HashMap<String, RemotePeerInfo> peerInfoMap;
	private ArrayList<String> peerList;
	private volatile HashMap<String, PeerHandler> joinedPeers;
	private volatile HashMap<String, Thread> joinedThreads;
	private volatile ServerSocket listener;
	private PeerServer server;
	private CommonConfig commonConfig;
	private PeerInfoConfig peerInfoConfig;
	private volatile PeerLogger logger;
	private volatile HashMap<String, BitSet> piecesAvailability;
	private volatile String[] requestedInfo;
	private volatile HashSet<String> unChokedList;
	private volatile HashSet<String> interestedList;
	private volatile String optUnchockedPeer;
	private int pieceCount;
	private volatile RandomAccessFile fileRaf;
	private volatile ChokeHandler chHandler;
	private volatile OptimisticUnchokeHandler optHandler;
	private volatile TerminateHandler terminateHandler;
	private volatile HashMap<String, Integer> downloadRate;
	private Thread serverThread;
	private volatile Boolean iamDone;

	private String trackerHost;
	private int trackerPort;
	private TrackerClient trackerClient;
	private Metadata metadata;
	private int corruptChunkIndex = -1;

	public PeerAdmin(String peerID) {
		this(peerID, null, 0, -1);
	}

	public PeerAdmin(String peerID, String trackerHost, int trackerPort, int corruptChunkIndex) {
		this.peerID = peerID;
		this.trackerHost = trackerHost;
		this.trackerPort = trackerPort;
		this.corruptChunkIndex = corruptChunkIndex;
		this.peerInfoMap = new HashMap<>();
		this.piecesAvailability = new HashMap<>();
		this.peerList = new ArrayList<>();
		this.joinedPeers = new HashMap<>();
		this.joinedThreads = new HashMap<>();
		this.commonConfig = new CommonConfig();
		this.peerInfoConfig = new PeerInfoConfig();
		this.logger = new PeerLogger(this.peerID);
		this.iamDone = false;
		this.unChokedList = new HashSet<>();
		this.interestedList = new HashSet<>();
		this.downloadRate = new HashMap<>();
		this.initPeer();
		this.chHandler = new ChokeHandler(this);
		this.optHandler = new OptimisticUnchokeHandler(this);
		this.terminateHandler = new TerminateHandler(this);
		this.chHandler.startJob();
		this.optHandler.startJob();
	}

	public int getCorruptChunkIndex() {
		return corruptChunkIndex;
	}

	public synchronized int getAndClearCorruptChunkIndex(int pieceIndex) {
		if (this.corruptChunkIndex == pieceIndex) {
			this.corruptChunkIndex = -1;
			return pieceIndex;
		}
		return -1;
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public void initPeer() {
		try {
			this.commonConfig.loadCommonFile();
			this.peerInfoConfig.loadConfigFile();
			this.myConfig = this.peerInfoConfig.getPeerConfig(this.peerID);
			this.peerInfoMap = this.peerInfoConfig.getPeerInfoMap();
			this.peerList = this.peerInfoConfig.getPeerList();

			if (trackerHost != null && trackerPort > 0) {
				this.trackerClient = new TrackerClient(trackerHost, trackerPort);
				boolean isSeeder = hasFile();
				BitSet myInitialBitset = new BitSet();
				if (isSeeder) {

				}
				try {
					TrackerClient.RegistrationResult regResult = trackerClient.register(
						peerID,
						myConfig != null ? myConfig.peerPort : 6000 + Integer.parseInt(peerID),
						isSeeder,
						myInitialBitset
					);
					this.logger.logTrackerRegistered(trackerHost, trackerPort);
					if (regResult.getMetadata() != null) {
						this.metadata = regResult.getMetadata();
						this.commonConfig.FileName = metadata.getFileName();
						this.commonConfig.FileSize = (int) metadata.getFileSize();
						this.commonConfig.PieceSize = metadata.getPieceSize();
					}

					if (regResult.getActivePeers() != null) {
						for (TrackerAdmin.PeerRecord pr : regResult.getActivePeers()) {
							if (!peerInfoMap.containsKey(pr.getPeerId())) {
								RemotePeerInfo rpi = new RemotePeerInfo(pr.getPeerId(), pr.getHost(), String.valueOf(pr.getPort()), pr.isSeeder() ? "1" : "0");
								peerInfoMap.put(pr.getPeerId(), rpi);
								if (!peerList.contains(pr.getPeerId())) {
									peerList.add(pr.getPeerId());
								}
							}
						}
					}
				} catch (Exception e) {
					System.err.println("[PEER_" + peerID + "] Tracker registration warning: " + e.getMessage() + ". Falling back to local configuration.");
				}
			}

			this.pieceCount = this.calcPieceCount();
			this.requestedInfo = new String[this.pieceCount];

			if (this.metadata == null) {
				File metaFile = new File(getFileName() + ".meta");
				if (metaFile.exists()) {
					try {
						this.metadata = Metadata.loadFromFile(metaFile);
					} catch (Exception ignored) {}
				}
				if (this.metadata == null && hasFile()) {
					String filepath = "peer_" + this.peerID + "/" + getFileName();
					File f = new File(filepath);
					if (f.exists()) {
						this.metadata = MetadataGenerator.generateMetadata(f, getPieceSize(), "http://localhost:8888");
						this.metadata.saveToFile(metaFile);
					}
				}
			}

			String filepath = "peer_" + this.peerID;
			File file = new File(filepath);
			file.mkdir();
			String filename = filepath + "/" + getFileName();
			file = new File(filename);
			if (!hasFile()) {
				file.createNewFile();
			}
			this.fileRaf = new RandomAccessFile(file, hasFile() ? "r" : "rw");
			if (!hasFile()) {
				this.fileRaf.setLength(this.getFileSize());
			}

			this.initializePieceAvailability();
			this.startServer();
			this.createNeighbourConnections();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void startServer() {
		try {
			int port = myConfig != null ? myConfig.peerPort : 6000 + Integer.parseInt(peerID);
			this.listener = new ServerSocket(port);
			this.server = new PeerServer(this.peerID, this.listener, this);
			this.serverThread = new Thread(this.server);
			this.serverThread.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void createNeighbourConnections() {
		new Thread(() -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException ignored) {}

			while (!this.iamDone) {
				boolean allConnected = true;
				for (String pid : this.peerList) {
					if (pid.equals(this.peerID)) {
						break;
					} else {
						if (!this.joinedPeers.containsKey(pid)) {
							allConnected = false;
							RemotePeerInfo peer = this.peerInfoMap.get(pid);
							if (peer != null) {
								try {
									Socket temp = new Socket(peer.peerAddress, peer.peerPort);
									PeerHandler p = new PeerHandler(temp, this);
									p.setEndPeerID(pid);
									this.addJoinedPeer(p, pid);
									Thread t = new Thread(p);
									this.addJoinedThreads(pid, t);
									t.start();
									System.out.println("[PEER_" + peerID + "] Connected successfully to neighbor peer " + pid);
								} catch (Exception connEx) {
									// Neighbor peer not online yet; will retry automatically
								}
							}
						}
					}
				}
				if (allConnected) {
					break;
				}
				try {
					Thread.sleep(1500);
				} catch (InterruptedException ignored) {
					break;
				}
			}
		}, "NeighbourConnector-" + this.peerID).start();
	}

	public void initializePieceAvailability() {
		for (String pid : this.peerInfoMap.keySet()) {
			BitSet availability = new BitSet(this.pieceCount);
			RemotePeerInfo rpi = this.peerInfoMap.get(pid);
			if (rpi != null && rpi.containsFile == 1) {
				availability.set(0, this.pieceCount);
				this.piecesAvailability.put(pid, availability);
			} else {
				availability.clear();
				this.piecesAvailability.put(pid, availability);
			}
		}
		if (!this.piecesAvailability.containsKey(this.peerID)) {
			BitSet myBitset = new BitSet(this.pieceCount);
			if (hasFile()) {
				myBitset.set(0, this.pieceCount);
			}
			this.piecesAvailability.put(this.peerID, myBitset);
		}
	}

	public synchronized void writeToFile(byte[] data, int pieceindex) {
		try {
			int position = this.getPieceSize() * pieceindex;
			this.fileRaf.seek(position);
			this.fileRaf.write(data);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized byte[] readFromFile(int pieceindex) {
		try {
			int position = this.getPieceSize() * pieceindex;
			int size = this.getPieceSize();
			if (pieceindex == getPieceCount() - 1) {
				size = this.getFileSize() % this.getPieceSize();
				if (size == 0)
					size = this.getPieceSize();
			}
			this.fileRaf.seek(position);
			byte[] data = new byte[size];
			this.fileRaf.readFully(data);
			return data;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return new byte[0];
	}

	public HashMap<String, Integer> getDownloadRates() {
		HashMap<String, Integer> rates = new HashMap<>();
		for (String key : this.joinedPeers.keySet()) {
			rates.put(key, this.joinedPeers.get(key).getDownloadRate());
		}
		return rates;
	}

	public synchronized void broadcastHave(int pieceIndex) {
		for (String key : this.joinedPeers.keySet()) {
			this.joinedPeers.get(key).sendHaveMessage(pieceIndex);
		}

		if (trackerClient != null) {
			boolean isSeeder = (getCompletedPieceCount() == pieceCount);
			trackerClient.announce(peerID, isSeeder, piecesAvailability.get(peerID));
		}
	}

	public synchronized void updatePieceAvailability(String peerID, int index) {
		BitSet bs = this.piecesAvailability.get(peerID);
		if (bs == null) {
			bs = new BitSet(this.pieceCount);
			this.piecesAvailability.put(peerID, bs);
		}
		bs.set(index);
	}

	public synchronized void updateBitset(String peerID, BitSet b) {
		this.piecesAvailability.put(peerID, b);
	}

	public synchronized void addJoinedPeer(PeerHandler p, String endpeerid) {
		this.joinedPeers.put(endpeerid, p);
	}

	public synchronized void removeJoinedPeer(String endpeerid) {
		this.joinedPeers.remove(endpeerid);
		this.joinedThreads.remove(endpeerid);
		this.resetRequested(endpeerid);
		this.logger.logPeerDisconnected(endpeerid);
	}

	public synchronized void addJoinedThreads(String epeerid, Thread th) {
		this.joinedThreads.put(epeerid, th);
	}

	public synchronized HashMap<String, Thread> getJoinedThreads() {
		return this.joinedThreads;
	}

	public PeerHandler getPeerHandler(String peerid) {
		return this.joinedPeers.get(peerid);
	}

	public BitSet getAvailabilityOf(String pid) {
		return this.piecesAvailability.get(pid);
	}

	public synchronized boolean checkIfInterested(String endpeerid) {
		BitSet end = this.getAvailabilityOf(endpeerid);
		BitSet mine = this.getAvailabilityOf(this.peerID);
		if (end == null || mine == null)
			return false;
		for (int i = 0; i < end.size() && i < this.pieceCount; i++) {
			if (end.get(i) == true && mine.get(i) == false) {
				return true;
			}
		}
		return false;
	}

	public synchronized void setRequestedInfo(int id, String peerID) {
		if (id >= 0 && id < this.requestedInfo.length) {
			this.requestedInfo[id] = peerID;
		}
	}

	public synchronized int checkForRequested(String endpeerid) {
		BitSet end = this.getAvailabilityOf(endpeerid);
		BitSet mine = this.getAvailabilityOf(this.peerID);
		if (end == null || mine == null)
			return -1;
		for (int i = 0; i < end.size() && i < this.pieceCount; i++) {
			if (end.get(i) == true && mine.get(i) == false && this.requestedInfo[i] == null) {
				setRequestedInfo(i, endpeerid);
				return i;
			}
		}
		return -1;
	}

	public synchronized void resetRequested(String endpeerid) {
		for (int i = 0; i < this.requestedInfo.length; i++) {
			if (this.requestedInfo[i] != null && this.requestedInfo[i].compareTo(endpeerid) == 0) {
				setRequestedInfo(i, null);
			}
		}
	}

	public synchronized void resetSingleRequestedPiece(int pieceIndex) {
		if (pieceIndex >= 0 && pieceIndex < this.requestedInfo.length) {
			this.requestedInfo[pieceIndex] = null;
		}
	}

	public String getPeerID() {
		return this.peerID;
	}

	public PeerLogger getLogger() {
		return this.logger;
	}

	public boolean hasFile() {
		return this.myConfig != null && this.myConfig.containsFile == 1;
	}

	public int getNoOfPreferredNeighbors() {
		return this.commonConfig.NumberOfPreferredNeighbors > 0 ? this.commonConfig.NumberOfPreferredNeighbors : 2;
	}

	public int getUnchockingInterval() {
		return this.commonConfig.UnchokingInterval > 0 ? this.commonConfig.UnchokingInterval : 5;
	}

	public int getOptimisticUnchockingInterval() {
		return this.commonConfig.OptimisticUnchokingInterval > 0 ? this.commonConfig.OptimisticUnchokingInterval : 15;
	}

	public String getFileName() {
		return this.commonConfig.FileName != null ? this.commonConfig.FileName : "thefile";
	}

	public int getFileSize() {
		return this.commonConfig.FileSize > 0 ? this.commonConfig.FileSize : 1000000;
	}

	public int getPieceSize() {
		return this.commonConfig.PieceSize > 0 ? this.commonConfig.PieceSize : 32768;
	}

	public int calcPieceCount() {
		int len = (getFileSize() / getPieceSize());
		if (getFileSize() % getPieceSize() != 0) {
			len += 1;
		}
		return len;
	}

	public int getPieceCount() {
		return this.pieceCount;
	}

	public int getCompletedPieceCount() {
		BitSet mine = this.piecesAvailability.get(this.peerID);
		return mine != null ? mine.cardinality() : 0;
	}

	public synchronized void addToInterestedList(String endPeerId) {
		this.interestedList.add(endPeerId);
	}

	public synchronized void removeFromInterestedList(String endPeerId) {
		if (this.interestedList != null) {
			this.interestedList.remove(endPeerId);
		}
	}

	public synchronized void resetInterestedList() {
		this.interestedList.clear();
	}

	public synchronized HashSet<String> getInterestedList() {
		return this.interestedList;
	}

	public synchronized boolean addUnchokedPeer(String peerid) {
		return this.unChokedList.add(peerid);
	}

	public synchronized HashSet<String> getUnchokedList() {
		return this.unChokedList;
	}

	public synchronized void resetUnchokedList() {
		this.unChokedList.clear();
	}

	public synchronized void updateUnchokedList(HashSet<String> newSet) {
		this.unChokedList = newSet;
	}

	public synchronized void setOptimisticUnchokdPeer(String peerid) {
		this.optUnchockedPeer = peerid;
	}

	public synchronized String getOptimisticUnchokedPeer() {
		return this.optUnchockedPeer;
	}

	public synchronized boolean checkIfAllPeersAreDone() {
		for (String peer : this.piecesAvailability.keySet()) {
			if (this.piecesAvailability.get(peer).cardinality() != this.pieceCount) {
				return false;
			}
		}
		return true;
	}

	public synchronized void verifyFullFileIntegrity() {
		try {
			String filepath = "peer_" + this.peerID + "/" + getFileName();
			File file = new File(filepath);
			if (this.fileRaf != null) {
				try {
					this.fileRaf.close();
					this.fileRaf = new RandomAccessFile(file, "r");
				} catch (Exception ignored) {}
			}
			if (file.exists() && metadata != null && metadata.getFileHash() != null) {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				try (FileInputStream fis = new FileInputStream(file)) {
					byte[] buf = new byte[8192];
					int read;
					while ((read = fis.read(buf)) != -1) {
						md.update(buf, 0, read);
					}
				}
				byte[] hashBytes = md.digest();
				StringBuilder hexStr = new StringBuilder();
				for (byte b : hashBytes) {
					String hex = Integer.toHexString(0xff & b);
					if (hex.length() == 1)
						hexStr.append('0');
					hexStr.append(hex);
				}
				String actualHash = hexStr.toString();
				boolean match = actualHash.equalsIgnoreCase(metadata.getFileHash());
				this.logger.logFileHashVerified(match, metadata.getFileHash(), actualHash);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public synchronized OptimisticUnchokeHandler getoptHandler() {
		return this.optHandler;
	}

	public synchronized ChokeHandler getchHandler() {
		return this.chHandler;
	}

	public synchronized RandomAccessFile getRefFile() {
		return this.fileRaf;
	}

	public synchronized ServerSocket getListener() {
		return this.listener;
	}

	public synchronized Thread getServerThread() {
		return this.serverThread;
	}

	public synchronized Boolean checkIfDone() {
		return this.iamDone;
	}

	public synchronized void closeHandlers() {
		for (String peer : this.joinedThreads.keySet()) {
			try {
				this.joinedThreads.get(peer).interrupt();
			} catch (Exception ignored) {
			}
		}
	}

	public synchronized void cancelChokes() {
		try {
			if (trackerClient != null) {
				trackerClient.leave(peerID);
			}
			this.getoptHandler().cancelJob();
			this.getchHandler().cancelJob();
			this.resetUnchokedList();
			this.setOptimisticUnchokdPeer(null);
			this.resetInterestedList();
			if (this.getRefFile() != null) {
				this.getRefFile().close();
			}
			this.getLogger().closeLogger();
			if (this.getListener() != null) {
				this.getListener().close();
			}
			this.iamDone = true;
			this.terminateHandler.startJob(6);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
