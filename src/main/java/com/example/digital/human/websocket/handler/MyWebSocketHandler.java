package com.example.digital.human.websocket.handler;

import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MyWebSocketHandler extends TextWebSocketHandler {

    // 线程安全存储所有活跃连接
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("WebSocket connected: " + session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        System.out.println("Received message from client: " + message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("WebSocket disconnected: " + session.getId());
    }

    // 供外部调用，广播消息给所有连接客户端
    public static void sendMessageToAll(String message) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // 关闭所有活跃连接
    public static void closeAllSessions() {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.close(CloseStatus.NORMAL);
                    System.out.println("主动关闭连接: " + session.getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
