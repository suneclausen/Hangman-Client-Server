package clientServer;
// A Java program for a Server

import hangmanGame.GameSetUp;
import hangmanGame.Hangman;
import helpers.Constants;
import helpers.GameUtility;
import org.json.JSONObject;

import javax.json.Json;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.UUID;

public class Server {
    // Global maps for structering players association to games and game associtions to actual gameSetUps
    private HashMap<String, String> clientGame = new HashMap<>(); // key: clientName, value: gameId
    private HashMap<String, GameSetUp> games = new HashMap<>(); // key: gameId, value: GameSetUp  --value: the Hangman game with that ID

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
                System.out.println("Client accepted:" + GameUtility.getClientName(socket));

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
        // takes input from the client socket
        DataInputStream in = new DataInputStream(
                new BufferedInputStream(socket.getInputStream()));

        // output stream for returning msg to client that send the message.
        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());

        String playerName = "";
        String clientName = GameUtility.getClientName(socket);
        String line = "";

        // reads message from client until "STOP" is sent
        while (!Constants.STOP.equals(line)) {
            try {
                line = in.readUTF();
                System.out.println(clientName + ": " + line); //Write in server (as log) about messages)

                JSONObject json = new JSONObject(line);
                String msg = json.getString("msg");
                String content = json.getString("content");
                String givenGameid = json.getString("gameid");

                switch (msg) {
                    case Constants.INTRODUCE_NAME:
                        playerName = content;
                        clientName = GameUtility.getClientName(socket, playerName);
                        outputStream.writeUTF(createReturnMsg("You are " + clientName));
                        getGamesOverview(outputStream);
                        break;
                    case Constants.START_GAME:
                        createGame(content, playerName, socket, outputStream);
                        break;
                    case Constants.NEW_WORD:
                        handleNewWordForGame(outputStream, clientName, content, givenGameid, socket);
                        break;
                    case Constants.GAMES:
                        getGamesOverview(outputStream);
                        break;
                    case Constants.GUESS:
                        handleGuess(clientName, outputStream, content, givenGameid, socket);
                        break;
                    case Constants.BURN:
                        games.get(givenGameid).takeTurn();
                        break;
                    case Constants.JOIN_GAME:
                        handleJoinGame(socket, playerName, outputStream, content);
                        getGamesOverview(outputStream);
                        break;
                    case Constants.ENABLE_SKETCH:
                        games.get(givenGameid).sketchHangManOnOf(socket);
                        break;
                    default:
                        // Client msg was not legal json
                        outputStream.writeUTF(errorMsg("Did not receive legal or usefull JSON: " + line));
                        break;
                }

            } catch (IOException io) {
                System.out.println("IO exception - Given input was: " + line);
                System.out.println(io);
            }
        }
        System.out.println("Closing connection");

        // close connection
        socket.close();
        in.close();
    }

    private void handleNewWordForGame(DataOutputStream outputStream, String clientName, String newWord, String givenGameid, Socket socket) throws IOException {
        boolean isInputValid = validateInput(clientName, outputStream, newWord, givenGameid, socket);
        if (isInputValid) {
            games.get(givenGameid).startNewGame(newWord);
        }
    }

    private void handleGuess(String clientName, DataOutputStream outputStream, String guess, String givenGameid, Socket socket) throws IOException {
        //Handle guess to a specific game from a specific user.
        boolean isInputValid;
        isInputValid = validateInput(clientName, outputStream, guess, givenGameid, socket);
        if (isInputValid) {
            // Setter will update variable that threads look at to decide whether or not to do stuff
            games.get(givenGameid).setLastGuessGiven(guess.toUpperCase());
        }
    }

    private void getGamesOverview(DataOutputStream outputStream) throws IOException {
        for (String gameId : games.keySet()) {
            GameSetUp gameSetUp = games.get(gameId);
            String returnMessege = "There are currently the following games active with number of players joined\n" +
                    "game id:" + gameId + "\t" +
                    "current number of players: " + gameSetUp.getPlayers().size();
            outputStream.writeUTF(createReturnMsg(returnMessege));
        }

    }

    private boolean validateInput(String clientName, DataOutputStream outputStream, String content, String givenGameid, Socket socket) throws IOException {
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
        if (!games.get(givenGameid).getCurrentPlayer().equals(socket)) {
            // It is not the turn of this client yet since the comp.
            outputStream.writeUTF(errorMsg("It is not your turn yet"));
            return false;
        }
        if (games.get(givenGameid).getGame().getGuessedLetters().contains(content)) {
            outputStream.writeUTF(errorMsg("We have already guessed on that letter:" + content + ". Try again."));
            return false;
        }

        return true;
    }

    private void handleJoinGame(Socket socket, String playerName, DataOutputStream outputStream, String gameIdToJoin) throws IOException {
        String clientName = GameUtility.getClientName(socket, playerName);
        if (games.containsKey(gameIdToJoin)) {
            if (clientGame.containsKey(clientName)) {
                //See if id already is in one game. Remove if so
                String prevGameId = clientGame.get(clientName);
                outputStream.writeUTF(createReturnMsg("Removed you from other game that you had joined with id:" + prevGameId));
                clientGame.remove(clientName);
                games.get(prevGameId).removePlayer(socket);
            }

            // Set
            games.get(gameIdToJoin).addPlayer(socket, playerName);
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

    private void createGame(String wordToGuess, String playerName, Socket socket, DataOutput outputStream) throws IOException {
        String clientName = GameUtility.getClientName(socket, playerName);
        String gameId = UUID.randomUUID().toString();

        clientGame.put(clientName, gameId);
        games.put(gameId, new GameSetUp(new Hangman(wordToGuess, gameId), socket, playerName));
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

    private String createReturnMsg(String response) {
        return Json.createObjectBuilder()
                .add("returnMsg", response)
                .build()
                .toString();
    }
}

