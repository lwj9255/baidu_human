package com.example.digital.human.pojo;

public class WebSocketMessage {
    private String message;
    private boolean last;
    private boolean first;
    // 0-问题，1-文字回答，2-图片回答，3-表格回答
    private Integer type;

    public WebSocketMessage() {
    }

    public WebSocketMessage(String message, boolean first, boolean last, Integer type) {
        this.message = message;
        this.last = last;
        this.first = first;
        this.type = type;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
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
                ", type=" + type +
                '}';
    }
}
