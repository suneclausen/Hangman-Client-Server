package hangmanGame;

import helpers.Constants;
import helpers.GameUtility;

import javax.json.Json;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
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
    private HashMap<Socket, String> playerNames = new HashMap<>();

    public GameSetUp(Hangman game, Socket owner, String nameOfOwner) {
        this.game = game;
        this.players = new ArrayList<>();
        this.turnCounter = 0;
        this.owner = owner;
        this.currentPlayer = owner;
        this.gameId = game.getGameId();
        this.players.add(owner);
        this.lastGuessGiven = "";
        this.isGameDone = false;
        this.stopBuffer = true;
        this.playerNames.put(owner, nameOfOwner);

        listenForIsGameDone(); //threaded
        ensureEnoughPlayersListener(); // threaded
        guessAndTurnListener(); // threaded
    }

    private void listenForIsGameDone() {
        Thread isGameDoneThread = new Thread(new Runnable() {
            @Override
            public void run() {
                stopBuffer = true;
                // Stopbuffer is used to only once write a end of game message and declare new owner.
                while (true) {
                    try {
                        Thread.sleep(100);
                        if (isGameDone && stopBuffer) {
                            owner = currentPlayer;
                            DataOutputStream out = new DataOutputStream(owner.getOutputStream());
                            out.writeUTF(createReturnMsg("\n!!! You are now the owner of the game: " + gameId + " so send NEW_WORD;[word] to the server with word being your chosen word !!!"));
                            stopBuffer = false;
                        }
                    } catch (IOException | InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        isGameDoneThread.start();
    }

    private void guessAndTurnListener() {
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
                } catch (IOException | InterruptedException e) {
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
            isGameDone = true;
        }
    }

    private void ensureEnoughPlayersListener() {
        Thread enoughPlayers = new Thread(new Runnable() {
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
                            o.writeUTF(createReturnMsg("Ready to play. We are now two players of: " + players.stream()
                                    .map(socket -> {
                                        String name = playerNames.get(socket);
                                        return GameUtility.getClientName(socket, name);
                                    })
                                    .collect(Collectors.toList())));
                            takeTurn();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }
        });
        enoughPlayers.start();
    }

    public synchronized void startNewGame(String gameWord) {
        game = new Hangman(gameWord);
        lastGuessGiven = "";
        isGameDone = false;
        stopBuffer = true;
        takeTurn();
    }

    public void addPlayer(Socket player, String playerName) {
        playerNames.put(player, playerName);
        Thread newPlayer = new Thread(new Runnable() {
            @Override
            public void run() {
                String newPlayerName = GameUtility.getClientName(player, playerName);
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
        newPlayer.start();
    }

    public void takeTurn() {
        if (players.size() <= 1) {
            //We are too few players so we should not do anything
            try {
                DataOutputStream out = new DataOutputStream(owner.getOutputStream());
                out.writeUTF(createReturnMsg("We are only one player so we cannot take any turns"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        turnCounter++;
        int indexOfPlayer = (turnCounter % players.size());
        String nextPlayer = GameUtility.getClientName(players.get(indexOfPlayer));
        String ownerName = GameUtility.getClientName(owner);
        if (nextPlayer.equals(ownerName) && players.size() >= 2) {
            takeTurn();
            return;
        } else {
            currentPlayer = players.get(indexOfPlayer);
        }

        announceTurnToAll();
    }

    private void announceTurnToAll() {
        String name = playerNames.getOrDefault(currentPlayer, "");
        String currentPlayerName = GameUtility.getClientName(currentPlayer, name);
        DataOutputStream out;
        for (Socket player : players) {
            try {
                out = new DataOutputStream(player.getOutputStream());
                if (player.equals(currentPlayer)) {
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

    public synchronized void removePlayer(Socket clientSocket) throws IOException {
        DataOutputStream out;
        out = new DataOutputStream(clientSocket.getOutputStream());

        if (clientSocket.equals(owner)) {
            out.writeUTF(createReturnMsg("You can not remove yourself from this game, since you are the owner. Wait until a new game has been started to try again."));
            return;
        }
        if (clientSocket.equals(currentPlayer)) {
            out.writeUTF(createReturnMsg("You can not remove yourself from this game, since it is your turn. Finish your turn and then leave."));
            return;
        }

        for (Socket player : players) {
            if (player.equals(clientSocket)) {
                players.remove(player);
                playerNames.remove(player);
                try {
                    out.writeUTF(createReturnMsg("You have been removed from the game: " + gameId + " and will no longer get status or information from this game."));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
        }

        if (players.size() <= 1) {
            ensureEnoughPlayersListener();
        }
    }

    public void sketchHangManOnOf(Socket socket) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        if (socket.equals(owner)) {
            game.sketchHangManOnOf();
            boolean sketchEnabled = game.isSketchEnabled();
            out.writeUTF(createReturnMsg("Sketch for game: " + gameId + " is enabled:" + sketchEnabled));
        } else {
            out.writeUTF(createReturnMsg("Only the owner can enable and disable sketch in game."));
        }
    }

    public Socket getCurrentPlayer() {
        return currentPlayer;
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
}