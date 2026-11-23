package src;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.*;
import java.util.BitSet;
import java.nio.*;
import java.lang.*;

import src.ActualMessage;
import src.HandshakeMessage;
import src.PeerAdmin;
import src.Metadata;

public class PeerHandler implements Runnable {
	private Socket listener;
	private PeerAdmin peerAdmin;
	private String endPeerID;
	private boolean connectionEstablished = false;
	private boolean initializer = false;
	private HandshakeMessage hsm;
	private volatile int downloadRate = 0;
	private volatile ObjectOutputStream out;
	private volatile ObjectInputStream in;

	public PeerHandler(Socket listener, PeerAdmin admin) {
		this.listener = listener;
		this.peerAdmin = admin;
		initStreams();
		this.hsm = new HandshakeMessage(this.peerAdmin.getPeerID());
	}

	public PeerAdmin getPeerAdmin() {
		return this.peerAdmin;
	}

	public void initStreams() {
		try {
			this.out = new ObjectOutputStream(this.listener.getOutputStream());
			this.out.flush();
			this.in = new ObjectInputStream(this.listener.getInputStream());
		}
		catch (Exception e) {

		}
	}

	public synchronized Socket getListener() {
		return this.listener;
	}

	public void setEndPeerID(String pid) {
		this.endPeerID = pid;
		this.initializer = true;
	}

	public int getDownloadRate() {
		return downloadRate;
	}

	public synchronized void resetDownloadRate() {
		this.downloadRate = 0;
	}

	public void run() {
		try {
			byte[] msg = this.hsm.buildHandShakeMessage();
			this.out.write(msg);
			this.out.flush();

			while (!this.peerAdmin.checkIfDone()) {
				if (!this.connectionEstablished) {
					byte[] response = new byte[32];
					this.in.readFully(response);
					this.processHandShakeMessage(response);
					if (this.peerAdmin.hasFile() || (this.peerAdmin.getAvailabilityOf(this.peerAdmin.getPeerID()) != null && this.peerAdmin.getAvailabilityOf(this.peerAdmin.getPeerID()).cardinality() > 0)) {
						this.sendBitField();
					}
				}
				else {
					int respLen = this.in.readInt();
					byte[] response = new byte[respLen];
					this.in.readFully(response);
					char messageType = (char) response[0];
					ActualMessage am = new ActualMessage();
					am.readActualMessage(respLen, response);

					if (messageType == '0') {

						this.peerAdmin.resetRequested(this.endPeerID);
						this.peerAdmin.getLogger().chokingNeighbor(this.endPeerID);
					}
					else if (messageType == '1') {

						int requestindex = this.peerAdmin.checkForRequested(this.endPeerID);
						if (requestindex == -1) {
							this.sendNotInterestedMessage();
						}
						else {
							this.sendRequestMessage(requestindex);
						}
						this.peerAdmin.getLogger().unchokedNeighbor(this.endPeerID);
					}
					else if (messageType == '2') {

						this.peerAdmin.addToInterestedList(this.endPeerID);
						this.peerAdmin.getLogger().receiveInterested(this.endPeerID);
					}
					else if (messageType == '3') {

						this.peerAdmin.removeFromInterestedList(this.endPeerID);
						this.peerAdmin.getLogger().receiveNotInterested(this.endPeerID);
					}
					else if (messageType == '4') {

						int pieceIndex = am.getPieceIndexFromPayload();
						this.peerAdmin.updatePieceAvailability(this.endPeerID, pieceIndex);
						if (this.peerAdmin.checkIfAllPeersAreDone()) {
							this.peerAdmin.cancelChokes();
						}
						if (this.peerAdmin.checkIfInterested(this.endPeerID)) {
							this.sendInterestedMessage();
						}
						else {
							this.sendNotInterestedMessage();
						}
						this.peerAdmin.getLogger().receiveHave(this.endPeerID, pieceIndex);
					}
					else if (messageType == '5') {

						BitSet bset = am.getBitFieldMessage();
						this.processBitFieldMessage(bset);
						if (!this.peerAdmin.hasFile()) {
							if (this.peerAdmin.checkIfInterested(this.endPeerID)) {
								this.sendInterestedMessage();
							}
							else {
								this.sendNotInterestedMessage();
							}
						}
					}
					else if (messageType == '6') {

						if (this.peerAdmin.getUnchokedList().contains(this.endPeerID)
								|| (this.peerAdmin.getOptimisticUnchokedPeer() != null && this.peerAdmin.getOptimisticUnchokedPeer().compareTo(this.endPeerID) == 0)) {
							int pieceIndex = am.getPieceIndexFromPayload();
							byte[] rawPiece = this.peerAdmin.readFromFile(pieceIndex);

							if (this.peerAdmin.getAndClearCorruptChunkIndex(pieceIndex) == pieceIndex) {
								System.out.println("[FAULT_INJECTION] Tampering piece " + pieceIndex + " payload before sending to Peer " + this.endPeerID);
								byte[] corruptPiece = rawPiece.clone();
								if (corruptPiece.length > 0) {
									corruptPiece[0] = (byte) (~corruptPiece[0]);
								}
								this.sendPieceMessage(pieceIndex, corruptPiece);
							} else {
								this.sendPieceMessage(pieceIndex, rawPiece);
							}
						}
					}
					else if (messageType == '7') {

						int pieceIndex = am.getPieceIndexFromPayload();
						byte[] pieceData = am.getPieceFromPayload();

						if (this.peerAdmin.getAndClearCorruptChunkIndex(pieceIndex) == pieceIndex) {
							System.err.println("[FAULT_INJECTION] Simulating transmission noise: corrupting piece #" + pieceIndex + " payload!");
							if (pieceData != null && pieceData.length > 0) {
								pieceData[0] = (byte) (~pieceData[0]);
							}
						}

						boolean isValidChunk = true;
						Metadata metadata = this.peerAdmin.getMetadata();
						if (metadata != null) {
							String expectedHash = metadata.getPieceHash(pieceIndex);
							if (expectedHash != null) {
								String actualHash = Metadata.calculateSHA256(pieceData);
								if (!actualHash.equalsIgnoreCase(expectedHash)) {
									isValidChunk = false;
									System.err.println("[FAULT_TOLERANCE] Corrupt chunk #" + pieceIndex + " received from Peer " + this.endPeerID + "! Expected SHA-256: " + expectedHash + " but calculated: " + actualHash);
									this.peerAdmin.getLogger().logCorruptChunkDetected(this.endPeerID, pieceIndex);

									this.peerAdmin.resetSingleRequestedPiece(pieceIndex);

									int retryIndex = this.peerAdmin.checkForRequested(this.endPeerID);
									if (retryIndex != -1) {
										this.sendRequestMessage(retryIndex);
									} else {
										this.sendNotInterestedMessage();
									}
								}
							}
						}

						if (isValidChunk) {
							this.peerAdmin.writeToFile(pieceData, pieceIndex);
							this.peerAdmin.updatePieceAvailability(this.peerAdmin.getPeerID(), pieceIndex);
							this.downloadRate++;
							Boolean alldone = this.peerAdmin.checkIfAllPeersAreDone();
							this.peerAdmin.getLogger().downloadPiece(this.endPeerID, pieceIndex, this.peerAdmin.getCompletedPieceCount());
							this.peerAdmin.setRequestedInfo(pieceIndex, null);
							this.peerAdmin.broadcastHave(pieceIndex);

							if (this.peerAdmin.getAvailabilityOf(this.peerAdmin.getPeerID()).cardinality() != this.peerAdmin.getPieceCount()) {
								int requestindex = this.peerAdmin.checkForRequested(this.endPeerID);
								if (requestindex != -1) {
									this.sendRequestMessage(requestindex);
								}
								else {
									this.sendNotInterestedMessage();
								}
							}
							else {
								this.peerAdmin.getLogger().downloadComplete();
								this.peerAdmin.verifyFullFileIntegrity();
								if (alldone) {
									this.peerAdmin.cancelChokes();
								}
								this.sendNotInterestedMessage();
							}
						}
					}
					else {
						System.out.println("Received unknown message type: " + messageType);
					}
				}
			}
		}
		catch (SocketException | EOFException e) {
			if (endPeerID != null) {
				this.peerAdmin.removeJoinedPeer(endPeerID);
			}
		}
		catch (Exception e) {
			if (endPeerID != null) {
				this.peerAdmin.removeJoinedPeer(endPeerID);
			}
		}
	}

	public synchronized void send(byte[] obj) {
		try {
			this.out.write(obj);
			this.out.flush();
		}
		catch (Exception e) {

		}
	}

	public void processHandShakeMessage(byte[] message) {
		HandshakeMessage hsm = new HandshakeMessage(this.peerAdmin.getPeerID());
		hsm.readHandShakeMessage(message);
		this.endPeerID = hsm.getPeerID();
		this.peerAdmin.addJoinedPeer(this, this.endPeerID);
		this.connectionEstablished = true;
		if (this.initializer) {
			this.peerAdmin.getLogger().genTCPConnLogSender(this.endPeerID);
		} else {
			this.peerAdmin.getLogger().genTCPConnLogReceiver(this.endPeerID);
		}
	}

	public void processBitFieldMessage(BitSet bitset) {
		this.peerAdmin.updateBitset(this.endPeerID, bitset);
	}

	public void sendBitField() {
		BitSet mine = this.peerAdmin.getAvailabilityOf(this.peerAdmin.getPeerID());
		byte[] payload = mine.toByteArray();
		ActualMessage am = new ActualMessage('5', payload);
		this.send(am.buildActualMessage());
	}

	public void sendChokedMessage() {
		ActualMessage am = new ActualMessage('0');
		this.send(am.buildActualMessage());
	}

	public void sendUnchokedMessage() {
		ActualMessage am = new ActualMessage('1');
		this.send(am.buildActualMessage());
	}

	public void sendUnChokedMessage() {
		sendUnchokedMessage();
	}

	public void sendInterestedMessage() {
		ActualMessage am = new ActualMessage('2');
		this.send(am.buildActualMessage());
	}

	public void sendNotInterestedMessage() {
		ActualMessage am = new ActualMessage('3');
		this.send(am.buildActualMessage());
	}

	public void sendHaveMessage(int pieceIndex) {
		byte[] payload = ByteBuffer.allocate(4).putInt(pieceIndex).array();
		ActualMessage am = new ActualMessage('4', payload);
		this.send(am.buildActualMessage());
	}

	public void sendRequestMessage(int pieceIndex) {
		byte[] payload = ByteBuffer.allocate(4).putInt(pieceIndex).array();
		ActualMessage am = new ActualMessage('6', payload);
		this.send(am.buildActualMessage());
	}

	public void sendPieceMessage(int pieceIndex, byte[] pieceData) {
		byte[] payload = new byte[4 + pieceData.length];
		byte[] indexBytes = ByteBuffer.allocate(4).putInt(pieceIndex).array();
		System.arraycopy(indexBytes, 0, payload, 0, 4);
		System.arraycopy(pieceData, 0, payload, 4, pieceData.length);
		ActualMessage am = new ActualMessage('7', payload);
		this.send(am.buildActualMessage());
	}
}
