package com.example.digital.human.websocket.handler;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AiFlagWebSocketHandler extends TextWebSocketHandler {
    // 线程安全存储所有活跃连接
    private static final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("WebSocket2 connected: " + session.getId());
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        System.out.println("WebSocket2 Received message from client: " + message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        System.out.println("WebSocket2 disconnected: " + session.getId());
    }

    // 供外部调用，广播消息给所有连接客户端
    public static void sendMessageToAll(String message) {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                    System.out.println("WebSocket2消息发送成功："+message);
                } catch (IOException e) {
                    System.out.println("WebSocket2消息发送失败："+message);
                    e.printStackTrace();
                }
            }else{
                System.out.println("websocket2未连接");
            }
        }
    }
}
