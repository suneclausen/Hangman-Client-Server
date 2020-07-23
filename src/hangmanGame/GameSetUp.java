package hangmanGame;

import helpers.GameUtility;
import secondTry.Constants;

import javax.json.Json;
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
    private boolean isGameDone;
    private boolean stopBuffer;
    private int numberOfLives = 8;

    public GameSetUp(Hangman game, Socket owner) {
        this.game = game;
        this.players = new ArrayList<>();
        this.turnCounter = 0;
        this.owner = owner;
        this.currentPlayer = owner;
        this.gameId = game.getGameId();
        players.add(owner);
        lastGuessGiven = "";
        isGameDone = false;
        stopBuffer = true;

        listenForIsGameDone(); //threaded
        controlTurnsListener(); // threaded
        startTurnsInGame(); // threaded
    }

    private void listenForIsGameDone() {
        Thread isGameDoneThread = new Thread(new Runnable() {
            @Override
            public void run() {
                stopBuffer = true;
                while (true) {
                    try {
                        Thread.sleep(100);
                        if (isGameDone && stopBuffer) {
                            owner = currentPlayer; //TODO: make logic that takes turn. Kan gøres ved at finde index på owner sætte turncounter på denne og så tage en tur. Det vil tage den næste
                            DataOutputStream out = new DataOutputStream(owner.getOutputStream());
                            out.writeUTF(createReturnMsg("\n!!! You are now the owner of the game: " + gameId + " so send NEW_WORD;[word] to the server with word being your chosen word !!!"));
                            stopBuffer = false;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        isGameDoneThread.start();
    }

    private void startTurnsInGame() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String bufferLastGuessGiven = lastGuessGiven;
                    while (true) {
                        Thread.sleep(100);
                        if (!lastGuessGiven.equals(bufferLastGuessGiven) && !"".equals(lastGuessGiven)) {
                            String response = game.handleGuess(lastGuessGiven);
                            giveFeedBackOnGuessToAllPlayers(response);
                            bufferLastGuessGiven = lastGuessGiven;
                            if (!isGameDone) {
                                // Only take turn if game is not done. Otherwise the logic is handled elsewhere..
                                takeTurn();
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();
    }

    private synchronized void giveFeedBackOnGuessToAllPlayers(String response) throws IOException {
        DataOutputStream out;
        boolean shouldSetIsGameDone = false;
        for (Socket player : players) {
            out = new DataOutputStream(player.getOutputStream());
            if (response.contains(Constants.WIN_MSG)) {
                if (player.equals(owner)) {
                    out.writeUTF(createReturnMsg("The other players guessed your word - so you lost!"));
                } else {
                    out.writeUTF(createReturnMsg("You were able to guess the word - so you win.\n" + response));
                }
                shouldSetIsGameDone = true;
            } else if (response.contains(Constants.LOSE_MSG)) {
                if (player.equals(owner)) {
                    out.writeUTF(createReturnMsg("The other players could not guess the word - hence you win."));
                } else {
                    out.writeUTF(createReturnMsg("None of the players could guess the word, so you lose.\n" + response));
                }
                shouldSetIsGameDone = true;
            } else {
                out.writeUTF(createReturnMsg("Currentplayer is done with his/her turn and game state is\n" + response));
            }
        }

        if (shouldSetIsGameDone) {
            isGameDone = true; //TODO: MAybe use setter???
        }
    }

    private void controlTurnsListener() {
        Thread controlTurns = new Thread(new Runnable() {
            @Override
            public void run() {
                Socket bufferCurrentPlayer = currentPlayer;
                while (true) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (players.size() == 1) {
                        // We only have one player and we cannot get started before another one has joined the game
                        continue;
                    }
                    if (players.size() == 2) {
                        // We are now enough to play. The owner who created the guessword cannot participate.
                        try {
                            DataOutputStream o = new DataOutputStream(currentPlayer.getOutputStream());
                            o.writeUTF(createReturnMsg("Ready to play. We are now to players of: " + players.stream().map(GameUtility::getClientName).collect(Collectors.toList())));

                            takeTurn();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }

            }
        });
        controlTurns.start();
    }

    public synchronized void startNewGame(String gameWord) { //TODO: Er ret sikker på dette skal være synchronized
        game = new Hangman(gameWord);
        lastGuessGiven = "";
        isGameDone = false;
        stopBuffer = true; //TODO: THIS IS UGLY!
        takeTurn();
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
                    if (players.size() >= 2) {
                        out.writeUTF(createReturnMsg("Welcome to the game - Wait for your turn"));
                    } else {
                        out.writeUTF(createReturnMsg("You turn. Begin the game."));
                    }
                    players.add(player);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
//        newPlayer.setPriority(Thread.MAX_PRIORITY);
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
        String nextPlayer = GameUtility.getClientName(players.get(indexOfPlayer));
        String ownerName = GameUtility.getClientName(owner);
        if (nextPlayer.equals(ownerName)) {
            takeTurn();
            return;
        } else {
            currentPlayer = players.get(indexOfPlayer);
        }

        //TODO: Make it a method
        String currentPlayerName = GameUtility.getClientName(currentPlayer);
        DataOutputStream out;
        for (Socket player : players) {
            try {
                out = new DataOutputStream(player.getOutputStream());
                if (GameUtility.getClientName(player).equals(currentPlayerName)) {
                    if (!isGameDone) {
                        out.writeUTF(createReturnMsg("Your turn"));
                    }
                } else {
                    out.writeUTF(createReturnMsg("The turn belongs to player: " + currentPlayerName + ". Wait until it is your turn"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String createReturnMsg(String response) {
        return Json.createObjectBuilder()
                .add("returnMsg", response)
                .build()
                .toString();
    }

    public synchronized void removePlayer(String clientName) {
        for (Socket player : players) {
            String playerName = GameUtility.getClientName(player);
            if (playerName.equals(clientName)) {
                players.remove(player);
                break;
            }
        }

    }
}
