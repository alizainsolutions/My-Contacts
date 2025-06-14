package com.alizainsolutions.mycontacts.Model;
public class MessageModel {
    private String message;
    private long timestamp;
    private boolean isSentByMe;

    public MessageModel(String message, long timestamp, boolean isSentByMe) {
        this.message = message;
        this.timestamp = timestamp;
        this.isSentByMe = isSentByMe;
    }

    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    public boolean isSentByMe() { return isSentByMe; }
}

