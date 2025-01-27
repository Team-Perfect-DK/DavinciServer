package com.zziony.Davinci.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
public class MyWebSocketHandler extends TextWebSocketHandler {

    // 연결된 모든 클라이언트를 추적하는 Set
    private Set<WebSocketSession> sessions = new HashSet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("새로운 클라이언트 연결됨: " + session.getId());

        // 연결된 클라이언트에게 알림 메시지 전송 (예: '새로운 사용자가 연결되었습니다')
        broadcastMessage("새로운 사용자가 연결되었습니다: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("수신된 메시지: " + payload);

        // 메시지를 모든 연결된 클라이언트에게 전달
        broadcastMessage(payload);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("클라이언트 연결 종료됨: " + session.getId());

        // 연결 종료된 클라이언트에게 알림 메시지 전송
        broadcastMessage("사용자가 연결을 종료했습니다: " + session.getId());
    }

    private void broadcastMessage(String message) throws Exception {
        for (WebSocketSession session : sessions) {
            try {
                // username만 포함된 메시지 전송
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
