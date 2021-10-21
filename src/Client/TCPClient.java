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
import java.util.HashMap;
import java.util.Map;
import java.io.*;


public class TCPClient {
    // server host and port number, which would be acquired from command line parameter
    private static String serverHost;
    private static Integer serverPort;
    private static DataInputStream dataInputStream;
    private static DataOutputStream dataOutputStream;
    private static MessageListener listener;
    private static Boolean serverDown = false;
    private static Boolean answerMode = false;
    private static String userName;
    private static Map<String, DataOutputStream> privateChatOutputs;

    /**
     * Seperate thread for listening message sent by other users
     */
    private static class MessageListener extends Thread {
        //private Socket messageSocket;
        //private int identityPort;
        private DataInputStream dataInputStream;
        private Boolean serverListener;
        //private DataOutputStream dataOutputStream;

        MessageListener(DataInputStream in, Boolean server) {
            //identityPort = idPort;
            //messageSocket = new Socket(host, port);
            serverListener = server;
            dataInputStream = in;
        }
        
        @Override
        public void run() {
            super.run();
            /*try {
                dataInputStream = new DataInputStream(messageSocket.getInputStream());
                dataOutputStream = new DataOutputStream(messageSocket.getOutputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }*/

            assert dataInputStream != null;
            assert dataOutputStream != null;

            // send the server the port number of the client main thread port for authentication
           /* try {
                dataOutputStream.writeUTF("listen " + String.valueOf(identityPort));
                dataOutputStream.flush();
                // test the message sender
                String message = (String) dataInputStream.readUTF();
                assert message.equals("connected");
                listenerConnected = true;
                System.out.println(listenerConnected);

            } catch (EOFException e) {
                System.out.println("===== Server is down, MessageListener is terminated. =====");
                return;
            } catch (IOException e) {
                System.out.println("===== Server is down, MessageListener is terminated. =====");
                return;
            }*/

            while (true) {
                try {
                    String message = (String) dataInputStream.readUTF();
                    Character statusCode = message.charAt(0);
                    
                    if (statusCode == '0') {
                        // plain message, no special operation
                        System.out.print(message.substring(1));
                    } else if (statusCode == '1') {
                        // exit the client
                        System.out.print(message.substring(1));
                        System.exit(0);
                    } else if (statusCode == '2') {
                        // target user has accepted the private call
                        String[] segments = message.split("\\s+");
                        String host = segments[3];
                        int port = Integer.parseInt(segments[4]);
                        String targetUsername = segments[2];  // name of the target user (that this user is calling)
                        System.out.print(targetUsername + "has accepted the private chat.\n");
                        userName = segments[1];
                        Socket privateSocket = new Socket(host, port);
                        // create input stream for this socket
                        DataInputStream privateInput = new DataInputStream(privateSocket.getInputStream());
                        DataOutputStream privateOutput = new DataOutputStream(privateSocket.getOutputStream());
                        privateChatOutputs.put(targetUsername, privateOutput);
                        MessageListener privateListener = new MessageListener(privateInput, false);
                        privateListener.start();
                    } else if (statusCode == '3') {
                        System.out.print(message.substring(1));
                        // server request an answer from user, therefore enter answer mode
                        answerMode = true;
                    }
                } catch (EOFException e) {
                    if (serverListener) System.out.println("===== Server is down, MessageListener is terminated. =====");
                    else System.out.println("===== The chat is ended by the other side. =====");
                    break;
                } catch (IOException e) {
                    if (serverListener) System.out.println("===== Server is down, MessageListener is terminated. =====");
                    else System.out.println("===== The chat is ended by the other side. =====");
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
        listener = new MessageListener(dataInputStream, true);
        listener.start();
        // first wait the listener to finish connecting with the server

        /*(while (!listenerConnected) {
            System.out.println("?");
        }*/

        // Upon connection and setup, prompt user to login by first sending a login request to the server
        sendServerMessage("login");


        // define a BufferedReader to get input from command line i.e., standard input from keyboard
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (!serverDown) {
            //String responseMessage = (String) dataInputStream.readUTF();
            //Character status = responseMessage.charAt(0);

            // print the msg without the command byte
            /*System.out.print(responseMessage.substring(1));
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
            }*/
            String message = reader.readLine();

            // if the command is to send private message, then no need to pass the request to server as server should not know the content of the chat
            String[] commands = message.split("\\s+");
            if (!answerMode && commands[0].equals("private")) {
                // check if has established connection with the target user
                String targetUser = commands[1];
                String content = message.substring(commands[0].length() + commands[1].length() + 2);
                DataOutputStream output = privateChatOutputs.get(targetUser);
                try {
                    output.writeUTF("0" + userName + ": " + content + "\n");
                    output.flush();
                } catch (EOFException e) {
                    System.out.println("===== The chat is ended by the other side. =====");
                } catch (IOException e) {
                    System.out.println("===== The chat is ended by the other side. =====");
                }
            } else sendServerMessage(message);
            // reset to command mode
            answerMode = false;
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