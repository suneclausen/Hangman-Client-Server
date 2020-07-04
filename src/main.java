import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.*;

public class main
{
    // CLIENT
    private static Socket socket = null;
    private static DataInputStream input = null;
    private static DataOutputStream out = null;

    //SERVER - initialize socket and input stream
    private static Socket socketServer   = null;
    private static ServerSocket server   = null;
    private static DataInputStream in       =  null;

    public static void main(String[]args){

        boolean connectionEstablished = connect();
        if(connectionEstablished){
            System.out.println("Connection established!");
            //TODO: established connection... now get a list of all other peers in the system. send along each time
        }

        startServer();

        System.out.println("WOULD LIKE TO BE ABLE TO WRITE MSG");

//        try {
//            // takes input from terminal
//            input = new DataInputStream(System.in);
//
//            // sends output to the socket
//            out = new DataOutputStream(socket.getOutputStream());
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        if(connectionEstablished){
            Scanner scan = new Scanner(System.in);
            String buf = "";
            String msg = "";
            while(true){
                System.out.print("Press Enter to write");
                if (scan.hasNext())
                {
                    buf = scan.next();

                    msg = scan.next();
                }

//            try {
//                int line = input.read(msg.getBytes());
//                out.writeUTF(msg);
//
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

                break; // TODO: FIX THIS

            }

            // close the connection
            try {
                input.close();
                out.close();
                socket.close();
            } catch (IOException i) {
                System.out.println(i);
            }

        }else{
            //TODO: STOP IT FROM TERMINATING
        }


    }

    private static void startServer() {
        try
        {
            int port = new Random().nextInt(1000 + 1)  + 5000; // [0...5]  + 10 = [10...15];
            server = new ServerSocket(port);
            InetAddress IP=InetAddress.getLocalHost();
            System.out.println("My server started on port = " + port + " on IP: " + IP.getHostAddress());

            in = new DataInputStream(
                    new BufferedInputStream(socketServer.getInputStream()));

            // listin for other peers trying to connect
            Thread t = new Thread(){
                public void run(){
                    while(true){

                        System.out.println("Waiting for a client ...");

                        try {
                            socketServer = server.accept();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        System.out.println("Client accepted");
                        System.out.println("ting: " + socketServer.getInetAddress() + " " +  socketServer.getLocalPort() +  "  " + socketServer.getPort());

                        // listen for messages
                        Thread t2 = new Thread(){
                            public void run(){

                                System.out.println("NOW HANDLE  INCOMING MESSAGES FOR CLIENT  " + socket );
                                //TODO: Handle messages
                                // takes input from the client socket

                                String line = "";

                                // reads message from client until "Over" is sent
                                while (!line.equals("STOP"))
                                {
                                    try
                                    {
                                        line = in.readUTF();
                                        System.out.println(line);

                                    }
                                    catch(IOException i)
                                    {
                                        System.out.println(i);
                                    }
                                }
                                System.out.println("Closing connection");
                                // close connection
                                try {
                                    socketServer.close();
                                    in.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        };
                        t2.start();

                    }
                }
            };
            t.start();



        }
        catch(IOException i)
        {
            System.out.println(i);
        }
    }


    private static boolean connect() {
        Scanner scan = new Scanner(System.in);
        System.out.print("IP to connect to: ");
        String IP = scan.next();

        System.out.print("Port: ");
        int port = scan.nextInt();

        scan.close();

        // establish a connection
        try {
            socket = new Socket(IP, port);

            // takes input from terminal
            input = new DataInputStream(System.in);

            // sends output to the socket
            out = new DataOutputStream(socket.getOutputStream());
        } catch (java.net.SocketException e){
            System.out.println("Connection attemp failed");
            return false;
        } catch (IOException i) {
            System.out.println(i);
        }

        return true;
    }

}