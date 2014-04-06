public class ErrorHandler {

	public static void error(int errno){
		switch(errno){
		case 0:
			System.out.println("Error: Undefined error.");
			break;
		case 1:
			System.out.println("Error: File not found.");
			break;
		case 2:
			System.out.println("Error: Access violation.");
			break;
		case 3:
			System.out.println("Error: Disk full or allocation exceeded.");
			break;
		case 4:
			System.out.println("Error: Illegal TFTP operation.");
			break;
		case 5:
			System.out.println("Error: Unknown transfer ID.");
			break;
		case 6:
			System.out.println("Error: File already exists.");
			break;
		case 7:
			System.out.println("Error: No such user.");
			break;
		case 8:
			System.out.println("Error: Host not found.");
		case 9:
			System.out.println("Error: Time out.");
		}
		System.exit(errno);
	}
}
