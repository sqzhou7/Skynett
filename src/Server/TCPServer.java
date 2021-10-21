/*
 * Java multi-threading server with TCP
 * There are two points of this example code:
 * - socket programming with TCP e.g., how to define a server socket, how to exchange data between client and server
 * - multi-threading
 *
 * Author: Wei Song
 * Date: 2021-09-28
 * */

package Server;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.io.*;

public class TCPServer {

    // Server information
    private static ServerSocket serverSocket;
    private static Integer serverPort;
    // map between username and account
    private static Map<String, Account> yellowBook;

    // map between username and message memo for this user/account
    private static Map<String, Message> memo;

    // map between client's main thread port number and its serve-side main thread
    //private static Map<Integer, ClientThread> listenerChecklist;

    // map between username and its client-thread
    private static Map<String, ClientThread> onlineThreads;

    private static int lockDuration;    // seconds
    private static int inactiveThres;



    // define ClientThread for handling multi-threading issue
    // ClientThread needs to extend Thread and override run() method
    private static class ClientThread extends Thread {
        private final Socket clientSocket;
        protected boolean clientAlive = false;
        private Account userAccount;
        private String clientID;
        private String clientAddress;
        private int clientPort;
        private ClientMessageThread msgSender;
        protected DataInputStream dataInputStream;
        protected DataOutputStream dataOutputStream;
        private String answer = null;
        private Boolean answerMode = false; // default to be command mode


        private class ClientMessageThread extends Thread {
            @Override
            public void run() {
                super.run();
                while (clientAlive) {

                }
            }

            public void sendMessage(String content) {
                try {
                    dataOutputStream.writeUTF(content);
                    dataOutputStream.flush();
                } catch (EOFException e) {
                    System.out.println("===== the user disconnected, user - " + clientID + "=====");
                    clientExit();
                } catch (IOException e) {
                    System.out.println("===== the user disconnected, user - " + clientID + "=====");
                    clientExit();
                }
            }
        }

        ClientThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            super.run();
            // get client Internet Address and port number
            clientAddress = clientSocket.getInetAddress().getHostAddress();
            clientPort = clientSocket.getPort();
            clientID = "("+ clientAddress + ", " + clientPort + ")";
            //listenerChecklist.put(clientPort, this);
            // start the message sender
            msgSender = new ClientMessageThread();
            msgSender.run();

            System.out.println("===== New connection created for user - " + clientID + "\n login initiated...");
            clientAlive = true;

            // define the dataInputStream to get message (input) from client
            // DataInputStream - used to acquire input from client
            // DataOutputStream - used to send data to client
            dataInputStream = null;
            dataOutputStream = null;
            
            setupIO();
            

            while (clientAlive) {
                
                    // get input from client
                    // socket like a door/pipe which connects client and server together
                    // data from client would be read from clientSocket
                    assert dataInputStream != null;
                    assert dataOutputStream != null;

                    Timer t = startTimer();
                    
                    String message = acceptClientMessage();

                    // this message might be used for command, or answer for a question from the message sender trigger by another thread, i.e. another user
                    if (answerMode) {
                        answer = message;
                        // exit answer mode
                        answerMode = false;
                        continue;
                    }

                    String[] commands = message.split("\\s+");

                    if (commands[0].equals("login")) {
                        System.out.println("[recv] login request from user - " + clientID);
                        t.cancel();
                        
                        
                        // prompt the user to enter username
                        sendClientMessage("0Username: ");

                        // waiting for the username
                        String username = acceptClientMessage();
                
                        
                        // check if the username exist
                        Account act = yellowBook.get(username);
                        if (act != null) {
                            // check password
                            int tryout = 3;
                            sendClientMessage("0Password: ");
                            while (true) {
                                String password = acceptClientMessage();
                                int result = act.login(password);
                                if (result == 0) {
                                    onlineThreads.put(username, this);
                                    userAccount = act;
                                    
                                    sendClientMessage("0Login successful! Welcome to Skynet!\nPlease enter command below:\n");
                                    broadCast(userAccount, "System: " + userAccount.getUsername() + " has logged in.\n");
                                    break;
                                } else if (result == 1) {
                                    tryout--;
                                    if (tryout == 0) {
                                        sendClientMessage("1Invalid Password. Your account is locked for " + lockDuration + " seconds. Please try again later\n");
                                        // lock the account
                                        act.noticeLocked(lockDuration);
                                        System.out.println("User with userid " + clientID + " is locked due to multiple loggin failure.");
                                        break;
                                    } else sendClientMessage("0Password incorrect. You have " + tryout + " more chances to try.\nPassword: ");
                                } else if (result == 2) {
                                    sendClientMessage("1This account is already logged in.\n");
                                    break;
                                } else if (result == 3) {
                                    sendClientMessage("1This account has been blocked due to multiple login failures. Please try again later\n");
                                    break;
                                }
                            }
                            
                        } 

                        // username does not exist, ask if to create new account
                        else {
                            sendClientMessage("0Username does not exist, do you want to create it?(y/n): ");
                            String msg = acceptClientMessage();
                            System.out.print(msg);
                            if (msg.equals("y") || msg.equals("Y")) {
                                // start registering
                                System.out.println("Start registering account with username: " + username);
                                // get the new password
                                sendClientMessage("0Please enter a new password: ");
                                String password = acceptClientMessage();
                                
                                // create the account
                                Account newAccount = new Account(username, password);
                                try{
                                    BufferedWriter writer = new BufferedWriter(new FileWriter("Server/credentials.txt", true));
                                    writer.append(username + " " + password + "\n");
                                    writer.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                // auto-login the account
                                yellowBook.put(username, newAccount);
                                newAccount.login(password);
                                userAccount = newAccount;
                                onlineThreads.put(username, this);
                                sendClientMessage("0Account created! You are logged in! Welcome to Skynet!\n");
                                broadCast(userAccount, "System: " + userAccount.getUsername() + " has logged in.\n");
                            } else {
                                sendClientMessage("1");
                                break;
                            }
                        }
                        
                        // make corresponding response i.e., require user to provide username and password for further authentication
                        // dataOutputStream would be used to send the data to client side

                    } else if (commands[0].equals("message")) {
                        System.out.println("[recv] message request from user - " + clientID);
                        t.cancel();
                        String receiver = commands[1];
                        String content = message.substring(commands[0].length() + commands[1].length() + 2);
                        // check if the user exist
                        Account act = yellowBook.get(receiver);
                        if (act == null) {
                            sendClientMessage("0Error. User \"" + receiver + "\" does not exist.\n");
                        } else if (receiver.equals(userAccount.getUsername())) {
                            sendClientMessage("0Error. Can not send message to yourself.\n");
                        } else {
                            // check if blocked
                            if (act.ifblocked(userAccount)) {
                                sendClientMessage("0Error. Can not send message to this account.\n");
                            } else {
                                // check if this user is online
                                ClientThread ct = onlineThreads.get(receiver);
                                if (ct == null) {
                                    // user currently not online, create message memo
                                    Message newMemo = new Message(userAccount.getUsername(), receiver, content);
                                    // associate this memo with the receiver username
                                    memo.put(receiver, newMemo);
                                } else {
                                    ct.sendClientMessage("0" + userAccount.getUsername() + ": " + content + "\n");
                                }
                            }
                        }
                
                    } else if (commands[0].equals("logout")) {
                        System.out.println("[recv] logout request from user - " + clientID);
                        t.cancel();
                        // log out the user/account
                        userAccount.logout();
                        onlineThreads.remove(userAccount.getUsername());
                        //listenerChecklist.remove(clientPort);
                        //stopListener();
                        sendClientMessage("1You are logged out! Thank you for using Skynet!\n");
                        broadCast(userAccount, "System: " + userAccount.getUsername() + " has logged out.\n");
                        break;
                    /*} else if (commands[0].equals("listen")) {
                        // this is a listener thread from a client
                        System.out.println("[recv] listen request from port - " + commands[1]);
                        t.cancel();
                        int portNum = Integer.parseInt(commands[1]);
                        ClientThread mainThread = listenerChecklist.get(portNum);
                        if (mainThread != null) {
                            mainThread.setSender(this);
                            sendMessage("connected");
                            System.out.println("===== sender is connected, user ID: " + getClientID() + " =====");
                        } else {
                            System.out.println("===== sender is not connected, user ID: " + getClientID() + " =====");
                            return;
                        }

                        while (clientAlive) {
                            // wait for any message to be sent
                        }*/
                    } else if (commands[0].equals("whoelse")) {
                        System.out.println("[recv] whoelse request from user - " + clientID);
                        t.cancel();
                        String nameList = "0";
                        for (String user : onlineThreads.keySet()) {
                            // check block
                            if (!onlineThreads.get(user).getAccount().ifblocked(userAccount) && onlineThreads.get(user).getAccount() != userAccount) nameList += user + "\n";
                        }
                        sendClientMessage(nameList);
                    } else if (commands[0].equals("whoelsesince")) {
                        System.out.println("[recv] whoelsesince request from user - " + clientID);
                        t.cancel();
                        int period = Integer.parseInt(commands[1]);
                        String nameList = "0";
                        for (String user : onlineThreads.keySet()) {
                            // check block
                            Account targetAct = onlineThreads.get(user).getAccount();
                            if (!targetAct.ifblocked(userAccount) && targetAct != userAccount && targetAct.isLoggedInWithin(period)) nameList += user + "\n";
                        }
                        sendClientMessage(nameList);
                    } else if (commands[0].equals("broadcast")) {
                        System.out.println("[recv] broadcast request from user - " + clientID);
                        t.cancel();
                        String content = message.substring(commands[0].length() + 1);
                        broadCast(userAccount, userAccount.getUsername() + ": " + content + "\n");
                        

                    } else if (commands[0].equals("block")) {
                        System.out.println("[recv] block request from user - " + clientID);
                        t.cancel();
                        // check the username
                        String username = message.substring(commands[0].length() + 1);

                        Account targetAct = yellowBook.get(username);
                        if (targetAct == null) {
                            sendClientMessage("0User \"" + username + "\"does not exist.\n");
                        } else if (targetAct == userAccount) {
                            sendClientMessage("0Error. Can not block yourself.\n"); 
                        } else {
                            userAccount.block(targetAct);
                        }

                    } else if (commands[0].equals("unblock")) {
                        System.out.println("[recv] logout request from user - " + clientID);
                        t.cancel();
                        // check the username
                        String username = message.substring(commands[0].length() + 1);
                        Account targetAct = yellowBook.get(username);
                        if (targetAct == null) {
                            sendClientMessage("0User \"" + username + "\" does not exist.\n");
                        } else if (targetAct == userAccount) {
                            sendClientMessage("0Error. Can not unblock yourself.\n"); 
                        } else {
                            userAccount.unblock(targetAct);
                        }
                    } else if (commands[0].equals("help")) {
                        System.out.println("[recv] help request from user - " + clientID);
                        t.cancel();
                        // check the username
                    } else if (commands[0].equals("startprivate")) {
                        System.out.println("[recv] startprivate request from user - " + clientID);
                        t.cancel();
                        // check the username
                        String username = message.substring(commands[0].length() + 1);

                        Account targetAct = yellowBook.get(username);
                        if (targetAct == null) {
                            sendClientMessage("0User \"" + username + "\" does not exist.\n");
                        } else {
                            ClientThread th = onlineThreads.get(username);
                            if (th == null) {
                                sendClientMessage("0User \"" + username + "\" is offline.\n");
                            } else {
                                // send the user an invitation for private messaging
                                String host = null;
                                String port = null;
                                Boolean confirm = th.privateCall(userAccount.getUsername(), host, port);
                                if (confirm) {
                                    // send back client '2' so that client knows that the other end has accepted the calling request
                                    System.out.println("sending confirmation with " + th.getClientAddress() +" "+ th.getClientPort());
                                    sendClientMessage("2 " + userAccount.getUsername() + " " + username + " " + th.getClientAddress() + " " + th.getClientPort());
                                }
                            }


                        }
                    } else {
                        sendClientMessage("0Command \"" + commands[0] + "\" does not exist, use \"help\" to list all supported commands.\n");
                    }
                    
                    /*else if (commands[0].equals("download")) {
                        System.out.println("[recv] download request from user - " + clientID);

                        // make corresponding response
                        String responseMessage = "You need to provide the file name you want to download";
                        System.out.println("[send] " + responseMessage);
                        dataOutputStream.writeUTF(responseMessage);
                        dataOutputStream.flush();
                    } else {
                        System.out.println("[recv]  " + message + " from user - " + clientID);
                        String responseMessage = "unknown request";
                        System.out.println("[send] " + message);
                        dataOutputStream.writeUTF(responseMessage);
                        dataOutputStream.flush();
                    }*/

                    //sendClientMessage("0");
           
            }
        }

        /** 
         * send a specific message to all the online users over their message listeners except those who have blocked the broadcaster
         */
        public void broadCast(Account act, String content) {
            for (ClientThread th : onlineThreads.values()) {
                Account thisAct = th.getAccount();
                if (!thisAct.ifblocked(userAccount) && thisAct != act) {
                    th.sendClientMessage("0" + content);
                }
            }
        }


        public void sendClientMessage(String content) {
            msgSender.sendMessage(content);
        }

        public Boolean privateCall(String username, String host, String port) {
            // set the messaging mode to answerMode
            answerMode = true;
            System.out.println("try to send invitation");
            // send the confirmation/invitation
            sendClientMessage("3System: " + username + " wants to have a private chat with you. Do you accept?(y/n): ");
            // wait for user to give the answer
            while (answer == null) {
                System.out.println("waiting");
            }
            String answerCopy = answer;
            answer = null;
            return (answerCopy.equals("y"));

        }

        public String acceptClientMessage() {
            try { 
                String content = (String) dataInputStream.readUTF();
                return content;
            } catch (EOFException e) {
                System.out.println("===== the user disconnected, user - " + clientID + "=====");
                clientExit();
            } catch (IOException e) {
                System.out.println("===== the user disconnected, user - " + clientID + "=====");
                clientExit();
            } return "";
        }

        public Account getAccount() {
            return userAccount;
        }

        public void setupIO() {
            try {
                dataInputStream = new DataInputStream(clientSocket.getInputStream());
                dataOutputStream = new DataOutputStream(clientSocket.getOutputStream());
            } catch (EOFException e) {
                System.out.println("===== the user disconnected, user - " + clientID + "=====");
                clientExit();
            } catch (IOException e) {
                System.out.println("===== the user disconnected, user - " + clientID + "=====");
                clientExit();
            }
        }

        public void clientExit() {
            if (clientAlive) {
                clientAlive = false;
                cleanUp();
            }
        }

        public void cleanUp() {
            System.out.println("[recv] logout request from user - " + clientID);
            // log out the user/account
            userAccount.logout();
            onlineThreads.remove(userAccount.getUsername());
            //listenerChecklist.remove(clientPort);
            //stopListener();
            broadCast(userAccount, "System: " + userAccount.getUsername() + " has logged out.\n");
        }

        public Boolean isClientAlive() {
            return clientAlive;
        }

        public String getClientID() {
            return clientID;
        }
        
        public String getClientAddress() {
            return clientAddress;
        }

        public int getClientPort() {
            return clientPort;
        }
        /*public void setSender(ClientThread s) {
            msgSender = s;
        }

        public void stopListener() {
            sendMessage("1");
        }*/

        public Timer startTimer() {
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    System.out.println("[recv] logout request from user - " + clientID);
                    // log out the user/account
                    userAccount.logout();
                    onlineThreads.remove(userAccount.getUsername());
                    //listenerChecklist.remove(clientPort);
                    sendClientMessage("1Timeout, exiting client...\n");
                    broadCast(userAccount, "System: " + userAccount.getUsername() + " has logged out.");
                }
            };

            Timer activeTimer = new Timer(true);
            
            activeTimer.schedule(task, inactiveThres * 1000);

            return activeTimer;
        }
    }


    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("===== Error usage: java TCPServer SERVER_PORT BLOCK_DURATION INACTIVE_THRESHOLD =====");
            return;
        }

        // acquire port number from command line parameter
        serverPort = Integer.parseInt(args[0]);

        // lock period
        lockDuration = Integer.parseInt(args[1]);

        // inactive threshold
        inactiveThres = Integer.parseInt(args[2]);

        // define server socket with the input port number, by default the host would be localhost i.e., 127.0.0.1
        serverSocket = new ServerSocket(serverPort);

        // fetch all the existing users from credentials.txt
        yellowBook = new ConcurrentHashMap<>();
        onlineThreads = new ConcurrentHashMap<>();
        //listenerChecklist = new ConcurrentHashMap<>();
        File credential = new File("Server/credentials.txt");
        Scanner myScanner = new Scanner(credential);

        // put all the username-password pairs into the map
        while (myScanner.hasNextLine()) {
            String data = myScanner.nextLine();
            String[] dataSet = data.split("\\s+");
            yellowBook.put(dataSet[0], new Account(dataSet[0], dataSet[1]));
        }

        myScanner.close();
        // make serverSocket listen connection request from clients
        System.out.println("===== Server is running =====");
        System.out.println("===== Waiting for connection request from clients...=====");

        while (true) {
            // when new connection request reaches the server, then server socket establishes connection
            Socket clientSocket = serverSocket.accept();
            // for each user there would be one thread, all the request/response for that user would be processed in that thread
            // different users will be working in different thread which is multi-threading (i.e., concurrent)
            ClientThread clientThread = new ClientThread(clientSocket);
            clientThread.start();
        }
    }
}