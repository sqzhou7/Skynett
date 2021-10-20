/*
 * Java socket programming client example with TCP
 * socket programming at the client side, which provides example of how to define client socket, how to send message to
 * the server and get response from the server with DataInputStream and DataOutputStream
 *
 * Author: Wei Song
 * Date: 2021-09-28
 * */

package Client;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;
import java.io.*;


public class TCPClient {
    // server host and port number, which would be acquired from command line parameter
    private static String serverHost;
    private static Integer serverPort;
    private static DataInputStream dataInputStream;
    private static DataOutputStream dataOutputStream;
    private static MessageListener listener;
    private static Boolean serverDown = false;

    /**
     * Seperate thread for listening message sent by other users
     */
    private static class MessageListener extends Thread {
        private Socket messageSocket;
        private int identityPort;
        private DataInputStream dataInputStream;
        private DataOutputStream dataOutputStream;

        MessageListener(int port) throws IOException {
            identityPort = port;
            messageSocket = new Socket(serverHost, serverPort);
        }
        
        @Override
        public void run() {
            super.run();
            try {
                dataInputStream = new DataInputStream(messageSocket.getInputStream());
                dataOutputStream = new DataOutputStream(messageSocket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }

            assert dataInputStream != null;
            assert dataOutputStream != null;

            // send the server the port number of the client main thread port for authentication
            try {
                dataOutputStream.writeUTF("listen " + String.valueOf(identityPort));
                dataOutputStream.flush();
            } catch (EOFException e) {
                System.out.println("===== Server is down, MessageListener is terminated. =====");
                return;
            } catch (IOException e) {
                System.out.println("===== Server is down, MessageListener is terminated. =====");
                return;
            }

            while (true) {
                try {
                    String message = (String) dataInputStream.readUTF();
                    Character statusCode = message.charAt(0);
                    if (statusCode == '1') {
                        // main(interaction) thread has ended, message listener needs to end as well 
                        System.out.println(statusCode);
                        break;
                    } else if (statusCode == '2') {
                        // main thread is blocked(from reading input), need listener to print certain info and exit the whole program
                        System.out.println(message.substring(1));
                        System.exit(0);
                    } else System.out.println(message.substring(1));
                } catch (EOFException e) {
                    System.out.println("===== Server is down, MessageListener is terminated. =====");
                    break;
                } catch (IOException e) {
                    System.out.println("===== Server is down, MessageListener is terminated. =====");
                    break;
                }
            }

           
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("===== Invalid parameter! The correct usage is: java TCPClient SERVER_PORT =====");
            return;
        }

        serverHost = "127.0.0.1";
        serverPort = Integer.parseInt(args[0]);

        // define socket for client
        Socket clientSocket = new Socket(serverHost, serverPort);

        // define DataInputStream instance which would be used to receive response from the server
        // define DataOutputStream instance which would be used to send message to the server
        dataInputStream = new DataInputStream(clientSocket.getInputStream());
        dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());

        
        // Upon connection and setup, prompt user to login by first sending a login request to the server
        sendServerMessage("login");


        // define a BufferedReader to get input from command line i.e., standard input from keyboard
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (!serverDown) {
            String responseMessage = (String) dataInputStream.readUTF();
            Character status = responseMessage.charAt(0);

            // print the msg without the command byte
            System.out.print(responseMessage.substring(1));
            if (status == '0') {
                if (listener == null) {
                    listener = new MessageListener(clientSocket.getLocalPort());
                    listener.start();
                }

            } 
            // case '1': read stdin
            else if (status == '1') {
                String message = reader.readLine();
                sendServerMessage(message);
            } 
            // case '2': exit the client
            else if (status == '2') {
                return;
            }
        }

        /*
        // finish login, begin to accept request
        while (true) {
            System.out.println("===== Please input any message you want to send to the server: ");

            // read input from command line
            String message = reader.readLine();

            // write message into dataOutputStream and send/flush to the server
            dataOutputStream.writeUTF(message);
            dataOutputStream.flush();
            // receive the server response from dataInputStream
            String responseMessage = (String) dataInputStream.readUTF();
            System.out.println("[recv] " + responseMessage);

            System.out.println("Do you want to continue(y/n) :");
            String answer = reader.readLine();
            if (answer.equals("n")) {
                System.out.println("Good bye");
                clientSocket.close();
                dataOutputStream.close();
                dataInputStream.close();
                break;
            }
        }*/
    }

    private static void sendServerMessage(String message) {
        try {
            dataOutputStream.writeUTF(message);
            dataOutputStream.flush();
        } catch (EOFException e) {
            System.out.println("===== Server is down. =====");
            serverDown = true;
        } catch (IOException e) {
            System.out.println("===== Server is down. =====");
            serverDown = true;
        }
    }

}
