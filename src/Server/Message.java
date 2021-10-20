package Server;

import java.time.LocalDateTime;

public class Message {
    private String sender;
    private String receiver;
    private LocalDateTime timeStamp;
    private String content;

    Message(String sender, String receiver, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.timeStamp = LocalDateTime.now();
    }

    public String getContent() {
        return content;
    }

    public String getSender() {
        return sender;
    }
}
