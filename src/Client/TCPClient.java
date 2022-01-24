/*
 * Author: Shaoqian Zhou
 * Date: Oct 2021
 */

package Client;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
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
    private static Map<String, Socket> privateChatSockets;

    /**
     * Seperate thread for listening message sent by other users
     */
    private static class MessageListener extends Thread {
        //private Socket messageSocket;
        //private int identityPort;
        private DataInputStream dataInputStream;
        private Boolean serverListener;
        private String userListento;

        MessageListener(DataInputStream in, Boolean server, String name) {
            serverListener = server;
            dataInputStream = in;
            if (!serverListener) userListento = name;
        }
        
        @Override
        public void run() {
            super.run();
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
                        System.out.print("System: " + targetUsername + "has accepted the private chat. Establishing connection...\n");
                        userName = segments[1];
                        Socket privateSocket = new Socket(host, port);
                        // create input stream for this socket
                        DataInputStream privateInput = new DataInputStream(privateSocket.getInputStream());
                        DataOutputStream privateOutput = new DataOutputStream(privateSocket.getOutputStream());
                        privateChatOutputs.put(targetUsername, privateOutput);
                        privateChatSockets.put(targetUsername, privateSocket);
                        MessageListener privateListener = new MessageListener(privateInput, false, targetUsername);
                        privateListener.start(); 
                    } else if (statusCode == '4') {
                        String[] segments = message.split("\\s+");
                        String targetUsername = segments[2];  // name of the target user (that this user is calling)
                        System.out.print("System: Establishing connection with " + targetUsername + "...\n");
                        userName = segments[1];
                        // create the server socket
                        ServerSocket privateServer = new ServerSocket(0);
                        // tell the server the port number
                        sendServerMessage(String.valueOf(privateServer.getLocalPort()));
                        Socket privateClient = privateServer.accept();
                        DataInputStream privateInput = new DataInputStream(privateClient.getInputStream());
                        DataOutputStream privateOutput = new DataOutputStream(privateClient.getOutputStream());
                        privateChatOutputs.put(targetUsername, privateOutput);
                        privateChatSockets.put(targetUsername, privateClient);
                        MessageListener privateListener = new MessageListener(privateInput, false, targetUsername);
                        privateListener.start(); 
                    } else if (statusCode == '3') {
                        System.out.print(message.substring(1));
                        // server request an answer from user, therefore enter answer mode
                        answerMode = true;
                    } else if (statusCode == '5') {
                        String[] segments = message.split("\\s+");
                        String username = segments[1];
                        System.out.print("System: Private chat closed by " + username + "\n");
                        // remove the output
                        privateChatOutputs.remove(username);
                        // close the private connection
                        privateChatSockets.remove(username);
                        break;
                    }
                } catch (EOFException e) {
                    if (serverListener) System.out.println("System: Server is down, MessageListener is terminated.");
                    else {
                        System.out.println("System: Chat is ended");
                        // remove the bookkeeping
                        privateChatOutputs.remove(userListento);
                        // close the private connection
                        privateChatSockets.remove(userListento);
                    }
                    break;
                } catch (IOException e) {
                    if (serverListener) System.out.println("System: Server is down, MessageListener is terminated.");
                    else {
                        System.out.println("System: Chat is ended");
                        // remove the bookkeeping
                        privateChatOutputs.remove(userListento);
                        // close the private connection
                        privateChatSockets.remove(userListento);
                    }
                    break;
                }
            }
        }
    }


    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Correct usage: java TCPClient SERVER_PORT");
            return;
        }

        serverHost = "127.0.0.1";
        serverPort = Integer.parseInt(args[0]);

        // define socket for client
        Socket clientSocket = new Socket(serverHost, serverPort);
        privateChatOutputs = new ConcurrentHashMap<>();
        privateChatSockets = new ConcurrentHashMap<>();
        // define DataInputStream instance which would be used to receive response from the server
        // define DataOutputStream instance which would be used to send message to the server
        dataInputStream = new DataInputStream(clientSocket.getInputStream());
        dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
        listener = new MessageListener(dataInputStream, true, null);
        listener.start();

        // Upon connection and setup, prompt user to login by first sending a login request to the server
        sendServerMessage("login");

        // define a BufferedReader to get input from command line i.e., standard input from keyboard
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (!serverDown) {
            String message = reader.readLine();

            // if the command is to send private message, then no need to pass the request to server as server should not know the content of the chat
            String[] commands = message.split("\\s+");
            if (!answerMode && commands[0].equals("private")) {
                sendServerMessage("private");
                if (commands.length < 2) {
                    System.out.print("Command usage: private target_user content\n");
                    continue;
                } else if (commands.length < 3) {
                    System.out.print("Error: Message cannot be empty\n");
                    continue;
                }
                // check if has established connection with the target user
                String targetUser = commands[1];
                String content = message.substring(commands[0].length() + commands[1].length() + 2);
                DataOutputStream output = privateChatOutputs.get(targetUser);
                if (output == null) {
                    System.out.println("You have no existing connection with " + targetUser);
                } else {
                    try {
                        output.writeUTF("0(Private)" + userName + ": " + content + "\n");
                        output.flush();
                    } catch (EOFException e) {
                        System.out.println("System: The chat is ended by the other side.");
                    } catch (IOException e) {
                        System.out.println("System: The chat is ended by the other side.");
                    }
                }
            } else if (!answerMode && commands[0].equals("stopprivate")) {
                sendServerMessage("stopprivate");
                // check if has established connection with the target user
                String targetUser = commands[1];
                DataOutputStream output = privateChatOutputs.get(targetUser);
                if (output == null) {
                    System.out.println("You have no existing connection with " + targetUser);
                } else {
                    try {
                        output.writeUTF("5" + " " + userName + "\n");
                        output.flush();
                    } catch (EOFException e) {
                        System.out.println("System: The chat has ended");
                    } catch (IOException e) {
                        System.out.println("System: The chat has ended");
                    }
                    privateChatSockets.get(targetUser).close();
                    privateChatSockets.remove(targetUser);
                    privateChatOutputs.remove(targetUser);
                }
            } else sendServerMessage(message);
            // reset to command mode
            answerMode = false;
        }
    }

    private static void sendServerMessage(String message) {
        try {
            dataOutputStream.writeUTF(message);
            dataOutputStream.flush();
        } catch (EOFException e) {
            System.out.println("System: Server is down.");
            serverDown = true;
        } catch (IOException e) {
            System.out.println("System: Server is down.");
            serverDown = true;
        }
    }

}