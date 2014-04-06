import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

public class tftp {

	public static void main(String[] args) throws IOException {
		if(args.length == 3 && (args[0].equals("get") || args[0].equals("put"))) {		//make sure usage is safe
			int myTID = 1025 + (int)(Math.random() * ((65535 - 1025) + 1));			//grab a safe random port
			String strServerAddr = args[1];							//grab server address
			String strMode = "octet";							//select a mode
			
			TftpClient tftpclient = new TftpClient(strServerAddr, myTID, strMode);		//Establish a client

			if(args[0].equals("put")) {
				System.out.println(tftpclient.send(args[2]) + " bytes written.");	//write file and display

			} else if(args[0].equals("get")) {
				System.out.println(tftpclient.receive(args[2]) + " bytes retrieved.");	//read file and display
			}
			tftpclient.close();								//close established client
		} else {
			System.out.println("Usage: java tftp [get|put] [serverAddress] [filename]\n");	//display help message
		}
	}
}
