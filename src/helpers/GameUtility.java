package helpers;

import java.net.Socket;

public class GameUtility {

    public static String getClientName(Socket socket){
        return socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
    }

    //Check the letter input and see if it is legal. Returns true if it is.
    public static boolean checkInput(String input) {
        String legalInput = "abcdefghijklmnopqrstuvwxyz".toUpperCase();
        if  (input.length() > 1) {
            System.out.println("Only insert one letter at a time - has to be an english letter");
            return false;
        }else if (legalInput.contains(input)) {
            return true;
        }

        System.out.println(input + " is not a legal input. Legal inputs are of one of the following: [\"abcdefghijklmnopqrstuvwxyz\"]");
        return false;
    }
}
