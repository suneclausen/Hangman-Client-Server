package secondTry;
// A Java program for a Server

import hangmanGame.Hangman;
import org.json.*;

import java.net.*;
import java.io.*;

public class Server {
    //initialize socket and input stream
    private Socket socket = null;
    private ServerSocket server = null;
    private DataInputStream in = null;
    private DataInputStream input = null;
    private DataOutputStream outputStream = null;

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
        Hangman game = null;

        // reads message from client until "STOP" is sent
        while (!line.equals("STOP")) {
            try {
                line = in.readUTF();
                System.out.println(line); //todo: delete

                JSONObject json = new JSONObject(line);
                String msg = json.getString("msg");
                String content = json.getString("content");

//                outputStream.writeUTF(counter++ + " svar på besked: " + line);
                switch (msg) {
                    case Constants.START_GAME:
                        //Create game
                        String wordToGuess = content;
                        game = new Hangman(wordToGuess);
                        outputStream.writeUTF("Game created with guessword: " + wordToGuess);
                        break;
                    case Constants.GUESS:
                        //Handle guess to a specific game from a specific user.
                        String response = game.handleGuess(content);
                        if (response.contains(Constants.WIN_MSG)){
                            //TODO: HANDLE WHO GET THE WIN AND LOSE MESSAGE:
                        }
                        outputStream.writeUTF("Server: " + response);
                        break;
                    case Constants.JOIN_GAME:
                        // Handle logig for entering game
                        break;
                    default:
                        // Client msg was not legal json
                        outputStream.writeUTF("Did not receive legal or usefull JSON: " + line);
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

