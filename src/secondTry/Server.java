package secondTry;
// A Java program for a Server

import hangmanGame.GameSetUp;
import hangmanGame.Hangman;
import helpers.GameUtility;
import org.json.JSONObject;

import javax.json.Json;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.UUID;

public class Server {
    //initialize socket and input stream
//    private Socket socket = null;
//    private ServerSocket server = null;
//    private DataInputStream in = null;
//    private DataOutputStream outputStream = null;
    private DataInputStream input = null;
    private HashMap<String, String> clientGame = new HashMap<>(); // key: clientName, value: gameId
    private HashMap<String, GameSetUp> games = new HashMap<>(); // key: gameId, value: GameSetUp  --value: the Hangman game with that ID

    private int counter = 0;

    // constructor with port
    public Server(int port) {
        // starts server and listens for incoming connection. For each new we start a listen thread on that connection.
        try {
            Socket socket = null;
            ServerSocket server = new ServerSocket(port);
            while (true) {
                System.out.println("Server started");
                System.out.println("Waiting for a client ...");

                socket = server.accept();
                System.out.println("Client accepted:" + socket.getInetAddress().getHostAddress() + ":" + socket.getPort());

                Socket finalSocket = socket;
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            listenForClientMessage(finalSocket);  //Has a while(true) loop
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    }
                });
                t.start();
            }
        } catch (IOException i) {
            System.out.println(i);
        }
    }

    public static void main(String args[]) {
        Server server = new Server(5000);
    }

    private void listenForClientMessage(Socket socket) throws IOException {
        String clientName = GameUtility.getClientName(socket);
        // takes input from the client socket
        DataInputStream in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()));

        // output stream for returning msg to client that send the message.
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        String line = "";

        // reads message from client until "STOP" is sent
        while (!line.equals("STOP")) {
            try {
                line = in.readUTF();
                System.out.println(clientName + ": " + line); //Write in server (as log) about messages)

                JSONObject json = new JSONObject(line);
                String msg = json.getString("msg");
                String content = json.getString("content");
                String givenGameid = json.getString("gameid");

                boolean isInputValid;

                switch (msg) {
                    case Constants.NEW_WORD:
                        isInputValid = validateInput(clientName, outputStream, content, givenGameid);
                        if (isInputValid) {
                            games.get(givenGameid).startNewGame(content);
                        }
                        break;
                    case Constants.START_GAME:
                        createGame(content, socket, outputStream);
                        break;
                    case Constants.BURN:
                        games.get(givenGameid).takeTurn();
                        break;
                    case Constants.GUESS:
                        //Handle guess to a specific game from a specific user.
                        // Setter will update variable that threads look at to decide whether or not to do stuff
                        isInputValid = validateInput(clientName, outputStream, content, givenGameid);
                        if (isInputValid) {
                            games.get(givenGameid).setLastGuessGiven(content.toUpperCase());
                        }
                        break;
                    case Constants.JOIN_GAME:
                        handleJoinGame(socket, clientName, outputStream, content);
                        break;
                    default:
                        // Client msg was not legal json
                        outputStream.writeUTF(errorMsg("Did not receive legal or usefull JSON: " + line));
                        break;
                }

            } catch (IOException io) {
                System.out.println("Given input was: " + line);
                System.out.println(io);
            }
        }
        System.out.println("Closing connection");

        // close connection
        socket.close();
        in.close();
        input.close();
    }

    private boolean validateInput(String clientName, DataOutputStream outputStream, String content, String givenGameid) throws IOException {
        // Defensive meausres on input and which user does what
        if (givenGameid == null) {
            outputStream.writeUTF(errorMsg("No game id provided"));
            return false;
        }
        if (!games.containsKey(givenGameid) && !clientGame.containsKey(clientName)) { //TODO: Could ask if the given game id corresponds to what we have saved for secureity measure
            outputStream.writeUTF(errorMsg("The gameId does not exist or you are not signed up to any game"));
            return false;
        }
        if (games.get(givenGameid).getPlayers().size() <= 1) {
            /// too few to play game
            outputStream.writeUTF(errorMsg("Too few players to game: " + givenGameid + ". Needs at least two players"));
            return false;
        }
        if (!GameUtility.getClientName(games.get(givenGameid).getCurrentPlayer()).equals(clientName)) {
            // It is not the turn of this client yet.
            outputStream.writeUTF(errorMsg("It is not your turn yet"));
            return false;
        }
        if (games.get(givenGameid).getGame().getGuessedLetters().contains(content)) {
            outputStream.writeUTF(errorMsg("We have already guessed on that letter:" + content + ". Try again."));
            return false;
        }

        return true;
    }

    private void handleJoinGame(Socket socket, String clientName, DataOutputStream outputStream, String gameIdToJoin) throws IOException {
        if (games.containsKey(gameIdToJoin)) {
            if (clientGame.containsKey(clientName)) {
                //See if id already is in one game. Remove if so
                String prevGameId = clientGame.get(clientName);
                outputStream.writeUTF(createReturnMsg("Removed you from other game that you had joined with id:" + prevGameId));
                clientGame.remove(clientName);
                games.get(prevGameId).removePlayer(clientName);
            }

            // Set
            games.get(gameIdToJoin).addPlayer(socket);
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
    }

    private String createReturnMsg(String response) {
        return Json.createObjectBuilder()
                .add("returnMsg", response)
                .build()
                .toString();
    }

    private void createGame(String content, Socket socket, DataOutput outputStream) throws IOException {
        String clientName = GameUtility.getClientName(socket);
        String gameId = UUID.randomUUID().toString();
        String wordToGuess = content;

        clientGame.put(clientName, gameId);
        games.put(gameId, new GameSetUp(new Hangman(wordToGuess, gameId), socket));
        outputStream.writeUTF(
                Json.createObjectBuilder()
                        .add("returnMsg", "Game created with guessword: " + wordToGuess + " and id:\n" + gameId + "\nNow we need one more player before game can begin.")
                        .add("gameid", gameId)
                        .build()
                        .toString()
        );
    }

    private String errorMsg(String msg) {
        return Json.createObjectBuilder().add("returnMsg", msg).build().toString();
    }
}

