//package main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author 	Jeffrey Moon, Theron Rabe
 * @date	4/1/2014
 *
 *
 */
public class TftpClient {
	
	private int myTID;
	private int serverTID = 0;
	private byte[] mode;
	
	private DatagramSocket udpSocket;
	private InetAddress serverAddr;
	
	// CONSTANTS
	private final byte terminator = 0;
	
	// All 2-byte opcodes, actual opcode is at index 1
	private final byte[] READOP = {0,1};
	private final byte[] WRITEOP = {0,2};
	private final byte[] DATAOP = {0,3};
	private final byte[] ACKOP = {0, 4};
	private final byte[] ERROROP = {0, 5};
	
	/**
	 * Constructor
	 * 
	 * @param strServerAddr Textual representation of either server hostname or IP address
	 * @param myTID			The TID (also port) that was randomly selected for this connection
	 * @param mode			The transfer mode; either 'octet', 'netascii', or 'mail'
	 */
	public TftpClient(String strServerAddr, int myTID, String mode) throws UnknownHostException, SocketException{
		try {
			serverAddr = InetAddress.getByName(strServerAddr);	//Lookup address
		} catch (UnknownHostException e) {
			ErrorHandler.error(8);						//error, if not found
		}
		this.myTID = myTID;						//Set TID
		this.mode = mode.getBytes();					//Set mode
		udpSocket = new DatagramSocket(myTID);				//Make socket
		udpSocket.setSoTimeout(10000);					//Set socket timeout value
	}

	/**
	 * Gracefully frees client resources
	 */
	public void close() {
		udpSocket.close();
	}
	
	/**
	 * Sends a file to the tftp server using mode 'octet'
	 * @param strFilename The file that will be sent to the tftp server
	 * @throws FileNotFoundException 
	 * @throws IOException 
	 * @return Number of bytes written
	 */
	public long send(String strFilename) throws FileNotFoundException {
		FileInputStream in = null;		
		File file = new File(strFilename);		//Grab the file
		if(!file.exists()) { ErrorHandler.error(1); }
	
		byte[] fileBytes = new byte[512];		//allocate read buffer
		
		int blockNum = 0;				//initialize state
		int bytesRead = 0;
		long totalBytes = 0;
		
		DatagramPacket initPacket = createInitPacket(WRITEOP, strFilename);	//build WRQ
		DatagramPacket dataPacket;
		
		try {//to initiate communication
			udpSocket.send(initPacket);		//Send WRQ
			grabPacket(udpSocket, initPacket);	//Wait for reply
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(checkAck(initPacket, blockNum)){		//if reply is ACK...
			blockNum++;
			serverTID = initPacket.getPort();		//grab server TID
			in = new FileInputStream(file);			//ready to read file
			
			// MAIN TRANSMISSION LOOP
			do {									//repeatedly...
				try {//to keep sending
					bytesRead = in.read(fileBytes);					//read a chunk
					if(bytesRead<0) bytesRead = 0;
					dataPacket = createDataPacket(blockNum, fileBytes, bytesRead);	//turn into packet
				
					udpSocket.send(dataPacket);					//send packet
					do {
						grabPacket(udpSocket, dataPacket);			//wait for proper reply
					} while (dataPacket.getPort() != serverTID);
				
					if(checkAck(dataPacket, blockNum)) {				//if reply is ACK
						blockNum++;						//change values, continue
						totalBytes += bytesRead;			
					} else {
						ErrorHandler.error(extractError(dataPacket));		//else, error
						break;
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			} while(bytesRead==512);						//until packet is terminal
		} else {
			ErrorHandler.error(extractError(initPacket));
		}
		return totalBytes;				//return total bytes transferred
	}


	/**
	 * @param strFilename	File to pull from tftp server
	 * @throws IOException	
	 * @return Number of bytes read
	 */
	public long receive(String strFilename) throws IOException{
		long totalBytes = 0;

		ByteArrayOutputStream bytestream = new ByteArrayOutputStream();			//prepare to write
		FileOutputStream fos = new FileOutputStream(new File("./" + strFilename));	//to file
		
		byte[] dataPacketArray = new byte[516];						//initialize packet
		DatagramPacket dataPacket = new DatagramPacket(dataPacketArray, dataPacketArray.length, serverAddr, 0);
		
		int blockNum = 1;
		
		DatagramPacket initPacket = createInitPacket(READOP, strFilename);		//create RRQ
		udpSocket.send(initPacket);							//send RRQ

		do{									//repeatedly...
			grabPacket(udpSocket, dataPacket);					//wait for packet
			if(serverTID == 0) {
				serverTID = dataPacket.getPort();				//set serverTID, if not set
			} else {
				while(dataPacket.getPort() != serverTID) {
					grabPacket(udpSocket, dataPacket);			//ensure correct server
				}
			}
			
			if(checkDataPacket(dataPacket, blockNum)){				//if no errors
				udpSocket.send(createAckPacket(blockNum));				//send ACK
				blockNum++;								//advance state
				bytestream.write(Arrays.copyOfRange(dataPacket.getData(), 4, dataPacket.getLength()));
				totalBytes += dataPacket.getLength();					//write to memory
			}else{
				ErrorHandler.error(extractError(dataPacket));			//else print error
			}
		} while(dataPacket.getLength() - 4 == 512);				//until packet is terminal
		
		byte[] outFileBytes = bytestream.toByteArray();
		fos.write(outFileBytes);						//write bytes to file
		fos.close();								//close file
		return totalBytes;							//return file size
	}
	
	/**
	 * @param opcode		The opcode (either WRQ or RRQ) for the initialization packet
	 * @param strFileName	The file name to send/receive from server
	 * @return				The init packet
	 */
	private DatagramPacket createInitPacket(byte[] opcode, String strFileName){
		byte[] filename = strFileName.getBytes();
		
		// Length of the data portion of the init packet in bytes. The +2 accounts for the 2 terminator bytes
		int newArrayLen = WRITEOP.length + filename.length + mode.length + 2;
		
		// Byte array used for storing data portion of init packet
		byte[] initPacketArray = concatByteArray(new byte[][] {opcode, filename, {terminator}, mode, {terminator}}, newArrayLen);
		DatagramPacket initPacket = new DatagramPacket(initPacketArray, initPacketArray.length, serverAddr, 69);
		initPacket.setData(initPacketArray);
		return initPacket;
	}
	
	/**
	 * @param blockNum	The current block number which will be used for the data packet
	 * @param fileBytes	The bytes read from file, length of this array is 512 (not all bytes have to be used)
	 * @param bytesRead	The actual number of bytes read from the file
	 * @return 			The data packet
	 */
	private DatagramPacket createDataPacket(int blockNum, byte[] fileBytes, int bytesRead){
		byte[] outByteArray = new byte[bytesRead + 4];	
		byte[] blockNumBytes = intToByteArray(blockNum);
		
		System.arraycopy(DATAOP, 0, outByteArray, 0, DATAOP.length);
		System.arraycopy(blockNumBytes, 0, outByteArray, 2, blockNumBytes.length);
		System.arraycopy(fileBytes, 0, outByteArray, 4, bytesRead);
		DatagramPacket dataPacket = new DatagramPacket(outByteArray, outByteArray.length, serverAddr, serverTID);
		dataPacket.setData(outByteArray);
		return dataPacket;
	}
	
	/**
	 * @param blockNum	The block number of the data packet we wish to ack
	 * @return			The full ack packet, ready for transmission
	 */
	private DatagramPacket createAckPacket(int blockNum){
		byte[] ackArray = {0, 4, 0, 0};
		System.arraycopy(intToByteArray(blockNum), 0, ackArray, 2, 2);
		DatagramPacket ack = new DatagramPacket(ackArray, ackArray.length, serverAddr, serverTID);
		return ack;
	}

	/**
	 * @param in	2D byte array that will be concatenated in big endian into a 1D byte
	 * @param len	Length of new 1D byte aray
	 * @return		1D concatenated byte array
	 */
	private byte[] concatByteArray(byte[][] b, int len){
		byte[] out = new byte[len];
		int offset = 0;
		for(int i = 0; i < b.length; i++){
			System.arraycopy(b[i], 0, out, offset, b[i].length);
			offset += b[i].length;
		}
		return out;	
	}

	/**
	 * Grabs next packet from socket, timing out if needed.
	 */
	private void grabPacket(DatagramSocket sock, DatagramPacket pack) {
		try {
			sock.receive(pack);
		} catch (Exception e) {
			ErrorHandler.error(9);
		}
	}
	
	/**
	 * @param ack		ACK packet from server
	 * @param blockNum	Block number of datagram the ACK refers to
	 * @return 			is this an acknowledgement?
	 */
	private boolean checkAck(DatagramPacket ack, int blockNum){
		byte[] byBlockNum = new byte[2];
		System.arraycopy(ack.getData(), 2, byBlockNum, 0, 2);
		if(ack.getData()[1] == ACKOP[1] && byteArrayToInt(byBlockNum) == blockNum) 
			return true;
		else return false;
	}

	/**
	 * Extracts an error number from an error packet
	 */
	private int extractError(DatagramPacket p) {
		if(p.getData()[1] == ERROROP[1]) {
			return (int) p.getData()[3];
		} else {
			return 0;
		}
	}
	
	/**
	 * @param dataPacket	The data packet that was received from the server
	 * @param blockNum		The previously-acked block number to compare against new block number
	 * @return				True if data packet is correctly formulated
	 */
	private boolean checkDataPacket(DatagramPacket dataPacket, int blockNum){
		byte[] opcode = new byte[2];
		byte[] blockNumByte = new byte[2];
		opcode = Arrays.copyOf(dataPacket.getData(), 2);
		blockNumByte = Arrays.copyOfRange(dataPacket.getData(), 2, 4);
		if ((opcode[1] == DATAOP[1]) && (blockNum == byteArrayToInt(blockNumByte))){
			return true;
		}
		else return false;
		
	}
	/**
	 * @param b	The byte array that will be converted into an int representation
	 * @return		int that has been converted from a 2-byte byte array
	 */
	private static int byteArrayToInt(byte[] b){
	    return   b[1] & 0xFF |
	            (b[0] & 0xFF) << 8;
	}
	
	/**
	 * @param a the int to be converted into a 2-byte byte array
	 * @return 	the 2-byte byte array from the int
	 */
	public static byte[] intToByteArray(int a){
	    return new byte[] {   
	        (byte) ((a >> 8) & 0xFF),   
	        (byte) (a & 0xFF)
	    };
	}
}
