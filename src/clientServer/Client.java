package clientServer;
// A Java program for a Client

import helpers.Constants;
import helpers.GameUtility;
import org.json.JSONObject;

import javax.json.Json;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class Client {
    // initialize socket and input output streams
    private Socket socket = null;
    private DataInputStream input = null;
    private DataInputStream in = null;
    private DataOutputStream out = null;

    private boolean isStopMsgSent;
    private String currentGameId;
    private String playerName;
    private List<String> keysWithoutPayload = Arrays.asList(
            Constants.BURN,
            Constants.ENABLE_SKETCH,
            Constants.GAMES
    );

    // constructor to put ip address and port
    public Client(String address, int port, String playerName) {
        this.playerName = playerName;
        // establish a connection
        try {
            setUpConnectionToServer(address, port, playerName);

            listenForServerMessages(); //Threaded

        } catch (IOException i) {
            System.out.println(i);
        }

        handleClientInput(); // Main Thread
    }

    public static void main(String args[]) {
        String playerName = "";
        try {
            playerName = args[0];
        } catch (Exception e) {
        }

        Client client = new Client("127.0.0.1", 5000, playerName);
    }

    private void handleClientInput() {
        // string to read message from input
        String line = "";

        // keep reading until "STOP" is input
        while (!Constants.STOP.equals(line)) {
            try {
                line = input.readLine().trim(); //TODO; Handle bad input.
                String[] split = line.split(";");
                String msgType = split[0].trim().toUpperCase();
                String content = "";
                if (!keysWithoutPayload.contains(msgType)) {
                    // Only read content if it is a msgType that needs to use the payload
                    content = split[1].trim();
                    content = Constants.JOIN_GAME.equals(msgType) ? content : content.toUpperCase(); // only have original case for gameids - the rest is uppercase
                }

                switch (msgType.trim()) {
                    case Constants.START_GAME:
                        out.writeUTF(createPayload(msgType, content, ""));
                        break;
                    case Constants.NEW_WORD:
                        out.writeUTF(createPayload(Constants.NEW_WORD, content, currentGameId));
                        break;
                    case Constants.GAMES:
                        out.writeUTF(createPayload(Constants.GAMES, "", ""));
                        break;
                    case Constants.GUESS:
                        if (GameUtility.checkInput(content.toUpperCase())) { //TODO; Maybe not have this logic here and go for an isolated bahaviour?
                            out.writeUTF(createPayload(Constants.GUESS, content, currentGameId));
                        } else {
                            System.out.println("ERROR: Wrongly formatted input");
                        }
                        break;
                    case Constants.BURN:
                        // For BURNING turn
                        out.writeUTF(createPayload(Constants.BURN, "", currentGameId));
                        break;
                    case Constants.JOIN_GAME:
                        String gameId = content;
                        out.writeUTF(createPayload(Constants.JOIN_GAME, gameId, ""));
                        break;
                    case Constants.ENABLE_SKETCH:
                        if (currentGameId != null || !"".equals(currentGameId)) {
                            out.writeUTF(createPayload(Constants.ENABLE_SKETCH, "", currentGameId));
                        }
                        break;
                    default:
                        System.out.println("Not a keyword recognized by the system. Input was: " + line + "\nTry again with legal input ");
                }
            } catch (IOException | ArrayIndexOutOfBoundsException i) {
                System.out.println(i);
                System.out.println("Wrong format of msg. Try again.\n" + line);
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
            System.out.println("Could not close connections.");
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
                        JSONObject jsonResponse = new JSONObject(serverResponse);
                        String returnMsg = jsonResponse.getString("returnMsg");
                        System.out.println("From server: " + returnMsg);

                        if (jsonResponse.has("gameid")) {
                            currentGameId = jsonResponse.getString("gameid");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        serverResponseThread.start();
    }

    private void setUpConnectionToServer(String address, int port, String playerName) throws IOException {
        socket = new Socket(address, port);
        System.out.println("Client: " + GameUtility.getClientName(socket, playerName) + " is connected");

        // takes input from terminal
        input = new DataInputStream(System.in);

        // sends output to the socket
        out = new DataOutputStream(socket.getOutputStream());

        in = new DataInputStream(socket.getInputStream());

        //Announce player to server:
        out.writeUTF(createPayload(Constants.INTRODUCE_NAME, playerName, ""));
    }

    private String createPayload(String msgType, String payLoadValue, String currentGameId) {
        return Json.createObjectBuilder()
                .add("msg", msgType)
                .add("content", payLoadValue)
                .add("gameid", currentGameId)
                .build()
                .toString();
    }
}