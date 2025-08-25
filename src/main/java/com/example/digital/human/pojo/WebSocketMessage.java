package com.example.digital.human.pojo;

public class WebSocketMessage {
    private String message;
    private boolean last;
    private boolean first;
    private Integer type;

    public WebSocketMessage() {
    }

    public WebSocketMessage(String message, boolean last, boolean first) {
        this.message = message;
        this.last = last;
        this.first = first;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isLast() {
        return last;
    }

    public void setLast(boolean last) {
        this.last = last;
    }

    public boolean isFirst() {
        return first;
    }

    public void setFirst(boolean first) {
        this.first = first;
    }

    @Override
    public String toString() {
        return "WebSocketMessage{" +
                "message='" + message + '\'' +
                ", last=" + last +
                ", first=" + first +
                '}';
    }
}
