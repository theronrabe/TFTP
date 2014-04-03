package main;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author 	Jeffrey Moon
 * @date	3/15/2014
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
		serverAddr = InetAddress.getByName(strServerAddr);
		this.myTID = myTID;
		this.mode = mode.getBytes();
		udpSocket = new DatagramSocket(myTID);
	}
	
	/**
	 * Sends a file to the tftp server using mode 'octet'
	 * @param strFilename The file that will be sent to the tftp server
	 * @throws FileNotFoundException 
	 * @throws IOException 
	 */
	public void send(String strFilename) throws FileNotFoundException {
		FileInputStream in = null;
	
		byte[] fileBytes = new byte[512];
		
		int blockNum = 0;
		int bytesRead = 0;
 		
		
		DatagramPacket initPacket = createInitPacket(WRITEOP, strFilename);
		DatagramPacket dataPacket;	
		System.out.println("PRESEND");
		
		try {
			udpSocket.send(initPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("SENT");
		
		try {
			udpSocket.receive(initPacket);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("GOTIT");
		File file = new File(strFilename);
		if(checkAck(initPacket, blockNum)){
			blockNum++;
			serverTID = initPacket.getPort();
			in = new FileInputStream(file);
			
			// MAIN TRANSMISSION LOOP
			do{
				try {
					bytesRead = in.read(fileBytes);
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(bytesRead<0) bytesRead = 0;
				System.out.print(bytesRead + " ");
				dataPacket = createDataPacket(blockNum, fileBytes, bytesRead);
				
				try {
					udpSocket.send(dataPacket);
					udpSocket.receive(dataPacket);
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				if(checkAck(dataPacket, blockNum)){
					blockNum++;
				}else{
					break;
				}
			}while(bytesRead==512);	
		}else{
			//Error handle
		}
		udpSocket.close();
	}
	/**
	 * @param strFilename	File to pull from tftp server
	 * @throws IOException	
	 */
	public void receive(String strFilename) throws IOException{
		
		ByteArrayOutputStream bytestream = new ByteArrayOutputStream();
		FileOutputStream fos = new FileOutputStream(new File("C:\\net\\" + strFilename));
		
		byte[] dataPacketArray = new byte[516];
		DatagramPacket dataPacket = new DatagramPacket(dataPacketArray, dataPacketArray.length, serverAddr, 0);
		
		int blockNum = 1;
		
		DatagramPacket initPacket = createInitPacket(READOP, strFilename);
		udpSocket.send(initPacket);
		do{
			udpSocket.receive(dataPacket);
			if(dataPacket.getData()[1] == ERROROP[1]){
				break;
			}
			if(serverTID == 0) serverTID = dataPacket.getPort();
			
			if(checkDataPacket(dataPacket, blockNum)){
				udpSocket.send(createAckPacket(blockNum));
				blockNum++;
				bytestream.write(Arrays.copyOfRange(dataPacket.getData(), 4, dataPacket.getLength()));
			}else{
				// Handle error
				break;
			}
		}while(dataPacket.getLength() - 4 == 512);
		
		byte[] outFileBytes = bytestream.toByteArray();
		fos.write(outFileBytes);
		fos.close();
		udpSocket.close();
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
	 * @param ack		ACK packet from server
	 * @param blockNum	Block number of datagram the ACK refers to
	 * @return 			true if ACK is correct
	 */
	private boolean checkAck(DatagramPacket ack, int blockNum){
		byte[] byBlockNum = new byte[2];
		System.arraycopy(ack.getData(), 2, byBlockNum, 0, 2);
		if(ack.getData()[1] == ACKOP[1] && byteArrayToInt(byBlockNum) == blockNum) 
			return true;
		else return false;
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
