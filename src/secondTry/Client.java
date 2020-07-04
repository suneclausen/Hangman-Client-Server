package secondTry;
// A Java program for a Client

import java.net.*;
import java.io.*;

public class Client {
    // initialize socket and input output streams
    private Socket socket = null;
    private DataInputStream input = null;
    private DataInputStream in = null;
    private DataOutputStream out = null;

    private boolean isStopMsgSent;

    // constructor to put ip address and port
    public Client(String address, int port) {
        // establish a connection
        try {
            setUpConnectionToServer(address, port);

            listenForServerMessages(); //Threaded

        } catch (IOException i) {
            System.out.println(i);
        }

        handleClientInput(); // Main Thread
    }

    private void handleClientInput() {
        // string to read message from input
        String line = "";

        // keep reading until "Over" is input
        while (!line.equals("STOP")) {
            try {
                line = input.readLine();
                out.writeUTF(line);

            } catch (IOException i) {
                System.out.println(i);
            }
        }
        isStopMsgSent = true;


        // close the connection
        try {
            input.close();
            out.close();
            socket.close();
            in.close();
        } catch (IOException i) {
            System.out.println(i);
        }
    }

    private void listenForServerMessages() {
        Thread serverResponseThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String serverResponse = null;
                try {
                    while (!isStopMsgSent) {
                        serverResponse = in.readUTF();
                        System.out.println("From server: " + serverResponse);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        serverResponseThread.start();
    }

    private void setUpConnectionToServer(String address, int port) throws IOException {
        socket = new Socket(address, port);
        System.out.println("Connected");

        // takes input from terminal
        input = new DataInputStream(System.in);

        // sends output to the socket
        out = new DataOutputStream(socket.getOutputStream());

        in = new DataInputStream(socket.getInputStream());
    }

    public static void main(String args[]) {
        Client client = new Client("127.0.0.1", 5000);
    }
}