package hangmanGame;

import helpers.GameUtility;
import org.json.JSONObject;
import secondTry.Constants;

import javax.json.Json;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class GameSetUp {
    private String gameId;
    private ArrayList<Socket> players;
    private Hangman game;
    private Socket owner;
    private int turnCounter;
    private Socket currentPlayer;
    private String lastGuessGiven;

    public GameSetUp(Hangman game, Socket owner) {
        this.game = game;
        this.players = new ArrayList<>();
        this.turnCounter = 0;
        this.owner = owner;
        this.currentPlayer = owner;
        this.gameId = game.getGameId();
        players.add(owner);
        lastGuessGiven = "";

        controlTurnsListener(); // threaded
        startTurnsInGame(); // threaded
    }

    private void startTurnsInGame() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                DataOutputStream out;
                DataInputStream in;


//                while (true) { //while true?
//                    int indexOfPlayer = (turnCounter % players.size());
//                    Socket currentPlayer = players.get(indexOfPlayer);
//                    String currentPlayerName = GameUtility.getClientName(currentPlayer);
                try {
                    // Let everyone know whose turn it is.


                    String bufferLastGuessGiven = lastGuessGiven;
                    while (true) {
                        if (!lastGuessGiven.equals(bufferLastGuessGiven)) {
                            in = new DataInputStream(
                                    new BufferedInputStream(currentPlayer.getInputStream()));
                            out = new DataOutputStream(currentPlayer.getOutputStream());

                            //handle guess
                            String response = game.handleGuess(lastGuessGiven);
                            for (Socket player : players) {
                                DataOutputStream toAll = new DataOutputStream(player.getOutputStream());
                                if (response.contains(Constants.WIN_MSG)) {
                                    //TODO: HANDLE WHO GET THE WIN AND LOSE MESSAGE: ALso make it possible to restart game.
                                    toAll.writeUTF(createReturnMsg("Currentplayer is done with his/her turn and game state is\n" + response));
                                } else if (response.contains(Constants.LOSE_MSG)) {
                                    //TODO:
                                    toAll.writeUTF(createReturnMsg("Currentplayer is done with his/her turn and game state is\n" + response));
                                }
                                toAll.writeUTF(createReturnMsg("Currentplayer is done with his/her turn and game state is\n" + response));
                            }

//                            break; // break the inner while true loop that waits for a response from the client.
                            takeTurn();
                            bufferLastGuessGiven = lastGuessGiven;
                        }
                    }


                    // Let the swicthc in the server handle request from people who do not have the turn!

                } catch (IOException e) {
                    e.printStackTrace();
                }
//                }
            }
        });
        t.start();
    }

    private void controlTurnsListener() {
        Thread controlTurns = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket bufferCurrentPlayer = currentPlayer;
                while (true) {
                    if (players.size() == 1) {
                        // We only have one player and we cannot get started before another one has joined the game
                        continue;
                    }
                    if (players.size() == 2) {
                        // We are now enough to play. The owner who created the guessword cannot participate.
                        try {
                            DataOutputStream o = new DataOutputStream(currentPlayer.getOutputStream());
                            o.writeUTF(createReturnMsg("Ready to play. We are now to players of: " + players.stream().map(socket -> GameUtility.getClientName(socket)).collect(Collectors.toList())));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        takeTurn();
                        break;
                    }
                }

                String bufferLastGuessGiven = lastGuessGiven;
                while (true) {
                    if (!bufferCurrentPlayer.equals(currentPlayer) || (players.size() == 2 && !lastGuessGiven.equals(bufferLastGuessGiven)) || !lastGuessGiven.equals(bufferLastGuessGiven)) {
                        // if there are only two players we will have to stay at the same players since owner cannot guess at his own word.
                        String currentPlayerName = GameUtility.getClientName(currentPlayer);
                        DataOutputStream out;
                        for (Socket player : players) {
                            try {
                                out = new DataOutputStream(player.getOutputStream());

                                if (GameUtility.getClientName(player).equals(currentPlayerName)) {
                                    out.writeUTF(createReturnMsg("Your turn"));
                                } else {
                                    out.writeUTF(createReturnMsg("The turn belongs to player: " + currentPlayerName + ". Wait until it is your turn"));
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        bufferCurrentPlayer = currentPlayer;
                        bufferLastGuessGiven = lastGuessGiven;
                    }
                }
            }
        });
        controlTurns.start();
    }

    public void addPlayer(Socket player) {
        Thread newPlayer = new Thread(new Runnable() {
            @Override
            public void run() {
                String newPlayerName = GameUtility.getClientName(player);
                DataOutputStream out;
                try {

                    for (Socket socket : players) {
                        out = new DataOutputStream(socket.getOutputStream());
                        out.writeUTF(createReturnMsg("New player joined game: " + newPlayerName));
                    }

                    out = new DataOutputStream(player.getOutputStream());
                    out.writeUTF(createReturnMsg("Wait for your turn"));
                    players.add(player);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        newPlayer.start();
    }

    public Socket getCurrentPlayer() {
        return currentPlayer;
    }

    public String getLastGuessGiven() {
        return lastGuessGiven;
    }

    public void setLastGuessGiven(String lastGuessGiven) {
        this.lastGuessGiven = lastGuessGiven;
    }

    public ArrayList<Socket> getPlayers() {
        return players;
    }

    public Hangman getGame() {
        return game;
    }

    public String getGameId() {
        return gameId;
    }

    public void takeTurn() {
        if (players.size() <= 1) {
            //We are too few players so we should not do anything
            return; //TODO: some error handling?
        }

        turnCounter++;
        int indexOfPlayer = (turnCounter % players.size());
        String player = GameUtility.getClientName(players.get(indexOfPlayer));
        String ownerName = GameUtility.getClientName(owner);
        if (player.equals(ownerName)) {
            takeTurn();
        } else {
            currentPlayer = players.get(indexOfPlayer);
        }

    }

    private String createReturnMsg(String response) {
        return Json.createObjectBuilder()
                .add("returnMsg", response)
                .build()
                .toString();
    }
}
