package main;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Main {

	public static void main(String[] args) throws IOException {
		int myTID = 1025 + (int)(Math.random() * ((65535 - 1025) + 1));
		String strServerAddr = "192.168.1.110";
		String strMode = "octet";
		
		TftpClient tftpclient = new TftpClient(strServerAddr, myTID, strMode);
		tftpclient.send("lab3.docx");
		//tftpclient.receive("lab3.docx");
	}

}
