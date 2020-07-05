package secondTry;
// A Java program for a Server

import hangmanGame.Hangman;
import org.json.*;

import javax.json.Json;
import java.net.*;
import java.io.*;
import java.util.HashMap;
import java.util.UUID;

public class Server {
    //initialize socket and input stream
    private Socket socket = null;
    private ServerSocket server = null;
    private DataInputStream in = null;
    private DataInputStream input = null;
    private DataOutputStream outputStream = null;
    private HashMap<String, String> clientGame = new HashMap<>(); // key: clientName, value: gameId
    private HashMap<String, Hangman> games = new HashMap<>(); // key: gameId, value: the Hangman game with that ID

    private int counter = 0;

    // constructor with port
    public Server(int port) {
        // starts server and waits for a connection
        try {
            listenForNewClients(port); //TODO: Consider making threaded and be able to handle multiple clients.

            listenForClientMessage(); // Main Thread. Has a while(true) loop
        } catch (IOException i) {
            System.out.println(i);
        }
    }

    private void listenForClientMessage() throws IOException {
        String line = "";
//        Hangman game = null;

        // reads message from client until "STOP" is sent
        while (!line.equals("STOP")) {
            try {
                line = in.readUTF();
                System.out.println(line); //todo: delete

                JSONObject json = new JSONObject(line);
                String msg = json.getString("msg");
                String content = json.getString("content");
                String givenGameid = json.getString("gameid");

//                outputStream.writeUTF(counter++ + " svar på besked: " + line);
                switch (msg) {
                    case Constants.START_GAME:
                        //Create game
                        createGame(content);
                        break;
                    case Constants.GUESS:
                        //Handle guess to a specific game from a specific user.
                        if (givenGameid == null) {
                            outputStream.writeUTF(errorMsg("No game id provided"));
                            break;
                        }
                        Hangman game = games.get(givenGameid);
                        String response = game.handleGuess(content);
                        if (response.contains(Constants.WIN_MSG)) {
                            //TODO: HANDLE WHO GET THE WIN AND LOSE MESSAGE: ALso make it possible to restart game.
                            outputStream.writeUTF(createReturnMsg(response));
                        } else if (response.contains(Constants.LOSE_MSG)) {
                            //TODO:
                            outputStream.writeUTF(createReturnMsg(response));
                        }
                        games.put(givenGameid, game); //TODo do not now know if reference are updated
                        outputStream.writeUTF(createReturnMsg(response));
                        break;
                    case Constants.JOIN_GAME:
                        // Handle logig for entering game
                        InetAddress inetAddress = socket.getInetAddress();
                        int clientPort = socket.getPort();
                        String clientName = inetAddress.getHostAddress() + ":" + clientPort;

                        String gameIdToJoin = content;

                        if (games.containsKey(gameIdToJoin)) {
                            Hangman hangman = games.get(gameIdToJoin);
                            clientGame.put(clientName, gameIdToJoin);
                            outputStream.writeUTF(
                                    Json.createObjectBuilder()
                                            .add("returnMsg", "SUCCES in joining game with id:" + gameIdToJoin)
                                            .add("gameid", gameIdToJoin)
                                            .build()
                                            .toString()
                            );
                        } else {
                            outputStream.writeUTF(errorMsg("Game with id:" + gameIdToJoin + " does not exist."));
                        }

                        if (clientGame.containsKey(clientName)) {
                            String prevGameId = clientGame.get(clientName);
                            outputStream.writeUTF(createReturnMsg("Removed you from other game that you had joined with id:" + prevGameId));
                            clientGame.remove(clientName);
                        }
                        break;
                    default:
                        // Client msg was not legal json
                        outputStream.writeUTF(errorMsg("Did not receive legal or usefull JSON: " + line));
                        break;
                }

            } catch (IOException io) {
                System.out.println(io);
            }
        }
        System.out.println("Closing connection");

        // close connection
        socket.close();
        in.close();
        input.close();
    }

    private String createReturnMsg(String response) {
        return Json.createObjectBuilder()
                .add("returnMsg", response)
                .build()
                .toString();
    }

    private void createGame(String content) throws IOException {
        InetAddress inetAddress = socket.getInetAddress();
        int clientPort = socket.getPort();
        String clientName = inetAddress.getHostAddress() + ":" + clientPort;
        String gameId = UUID.randomUUID().toString();

        String wordToGuess = content;
//                        game = new Hangman(wordToGuess, clientName, gameId);

        clientGame.put(clientName, gameId);
        games.put(gameId, new Hangman(wordToGuess, clientName, gameId));
        Json.createObjectBuilder().add("returnMsg", "Game created with guessword: " + wordToGuess).add("gameid", gameId).build().toString();
        outputStream.writeUTF(Json.createObjectBuilder().add("returnMsg", "Game created with guessword: " + wordToGuess).add("gameid", gameId).build().toString());
    }

    private String errorMsg(String msg) {
        return Json.createObjectBuilder().add("error", msg).build().toString();
    }

    private void listenForNewClients(int port) throws IOException {
        server = new ServerSocket(port);
        System.out.println("Server started");

        System.out.println("Waiting for a client ...");

        socket = server.accept();
        System.out.println("Client accepted");

        // takes input from the client socket
        in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()));

        // takes input from terminal
        input = new DataInputStream(System.in); //TODO: Do we need this.

        // output stream for returning msg to client that send the message.
        outputStream = new DataOutputStream(socket.getOutputStream());

    }


    public static void main(String args[]) {
        Server server = new Server(5000);
    }
}

