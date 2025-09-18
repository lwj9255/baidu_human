package com.example.digital.human.websocket.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.example.digital.human.pojo.WebSocketMessage;

public class MyWebSocketHandler extends TextWebSocketHandler {

    // 线程安全存储所有活跃连接
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("WebSocket1 connected: " + session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        System.out.println("WebSocket1 Received message from client: " + message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("WebSocket1 disconnected: " + session.getId());
    }

    // 供外部调用，广播消息给所有连接客户端
    public static void sendMessageToAll(String message) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                    System.out.println("WebSocket1消息发送成功："+message);
                } catch (IOException e) {
                    System.out.println("WebSocket1消息发送失败："+message);
                    e.printStackTrace();
                }
            }else{
                System.out.println("websocket1未连接");
            }
        }
    }

    // 关闭所有活跃连接
    public static void closeAllSessions() {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.close(CloseStatus.NORMAL);
                    System.out.println("WebSocket1主动关闭连接: " + session.getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 判断是否有通道
    public static boolean hasOpenSession() {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                return true;
            }
        }
        return false;
    }

    public static int getOpenSessionCount() {
        return sessions.size();
    }

    // 发送二进制数据
    public static void sendBinaryToAll(byte[] data, int type) throws IOException {
        WebSocketMessage msg = new WebSocketMessage(Base64.getEncoder().encodeToString(data),false,false,type);
        String json = new ObjectMapper().writeValueAsString(msg);
        sendMessageToAll(json);
    }

}
