package secondTry;
// A Java program for a Client

import hangmanGame.Hangman;
import org.json.JSONObject;

import javax.json.Json;
import java.net.*;
import java.io.*;

public class Client {
    // initialize socket and input output streams
    private Socket socket = null;
    private DataInputStream input = null;
    private DataInputStream in = null;
    private DataOutputStream out = null;
    private String ipPort = null;

    private boolean isStopMsgSent;
    private String currentGameId;

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

        // keep reading until "STOP" is input
        while (!line.equals("STOP")) {
            try {
                line = input.readLine();
                String[] split = line.split(";");
                String msgType = split[0];
                String content = split[1];
                switch (msgType.trim()) {
                    case Constants.START_GAME:
                        out.writeUTF(createPayload(msgType, content, ""));
                        break;
                    case Constants.GUESS:
                        if (Hangman.checkInput(content.toUpperCase())){ //TODO; Maybe not have this logic here and go for an isolated bahaviour?
                            out.writeUTF(createPayload(Constants.GUESS, content, currentGameId));
                        }else{
                            System.out.println("ERROR: Wrongly formatted input");
                        }
                        break;
                    case Constants.JOIN_GAME:
                        //TODO
                        String gameId = content;
                        out.writeUTF(createPayload(Constants.JOIN_GAME, gameId, ""));
                        break;
                }

//                out.writeUTF(line);

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

    private String createPayload(String msgType, String payLoadValue, String currentGameId) {
        return Json.createObjectBuilder()
                .add("msg", msgType)
                .add("content", payLoadValue)
                .add("gameid", currentGameId)
                .build()
                .toString();
    }

    private void listenForServerMessages() {
        Thread serverResponseThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String serverResponse = null;
                try {
                    while (!isStopMsgSent) {
                        serverResponse = in.readUTF();
                        JSONObject jsonResponse = new JSONObject(serverResponse);
                        String returnMsg = jsonResponse.getString("returnMsg");
                        System.out.println("From server: " + returnMsg);

                        if (jsonResponse.has("gameid")){
                            currentGameId = jsonResponse.getString("gameid");
                        }
                    }
                } catch (Exception e){
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