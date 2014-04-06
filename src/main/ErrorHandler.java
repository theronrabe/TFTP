package main;

public class ErrorHandler {

	public void error(int errno){
		switch(errno){
		case 0:
			System.out.println("Error: Unable to bind socket to port");
			break;
		case 1:
			System.out.println("Error: Unable to pack initialization packet");
			break;
		case 2:
			System.out.println("Error: Unable to transmit initialization packet");
			break;
		case 3:
			System.out.println("Error: Unable to resolve server's hostname");
			break;
		case 4:
			System.out.println("Error: Server timeout. Attempting to resend initialization packet");
			break;
		case 5:
			System.out.println("Error: Server timeout");
			break;
		case 6:
			System.out.println("Error: Unable to communicate with server");
			break;
		case 8:
			System.out.println("Error: Unable to close bytestream");
			break;
		case 9:
			System.out.println("Error: File not found on localhost");
			break;
		case 10:
			System.out.println("Error: Unable to read from file");
			break;
		case 11:
			System.out.println("Error: Unable to pack data packet");
			break;
		case 12:
			System.out.println("Error: Server timeout. Attempting to resend packet");
			break;
		case 13:
			System.out.println("Error: Unable to pack ACK packet");
			break;
		case 14:
			System.out.println("Error: Encoding not supported");
			break;
		}
	
		
	}
	public void error(int errno, String errorMsg){
		switch(errno){
		case 7:
			System.out.printf("Error response from server: %s", errorMsg);

		}
	}

}
