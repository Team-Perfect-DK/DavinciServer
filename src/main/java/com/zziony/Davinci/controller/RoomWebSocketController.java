package com.zziony.Davinci.controller;

import com.zziony.Davinci.model.Card;
import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.ws.JoinRequest;
import com.zziony.Davinci.model.ws.StartRequest;
import com.zziony.Davinci.repository.RoomRepository;
import com.zziony.Davinci.service.CardService;
import com.zziony.Davinci.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;

@Controller
public class RoomWebSocketController {
    private final RoomService roomService;
    private final CardService cardService;
    private final RoomRepository roomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public RoomWebSocketController(RoomService roomService, CardService cardService, RoomRepository roomRepository, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.cardService = cardService;
        this.roomRepository = roomRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // 게스트 참여
    @MessageMapping("/rooms/join")
    public void joinRoom(JoinRequest request) {
        Room room = roomService.joinRoom(request.getRoomCode(), request.getUserId());
        // 해당 방 참여자에게만 전송
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + request.getRoomCode(),
                Map.of("action", "ROOM_UPDATED", "payload", room)
        );

        // 전체 로비에 방 리스트 갱신 알림
        messagingTemplate.convertAndSend(
                "/topic/rooms/update",
                Map.of("action", "ROOM_LIST_CHANGED")
        );
    }

    // 게임 시작
    @MessageMapping("/rooms/start")
    public void startGame(StartRequest request) {
        Room room = roomService.findRoomByCode(request.getRoomCode()).orElseThrow();
        List<Card> allCards = roomService.startGameAndGetCards(request.getRoomCode());
        String currentTurnPlayerId = room.getHostId();
        String currentTurnPlayerNickname = room.getHostNickname();
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + request.getRoomCode(),
                Map.of(
                        "action", "GAME_STARTED",
                        "payload", Map.of(
                                "room", room != null ? room : "",
                                "currentTurnPlayerId", currentTurnPlayerId,
                                "currentTurnPlayerNickname", currentTurnPlayerNickname,
                                "cards", allCards != null ? allCards : List.of()
                        )
                )
        );
    }

    // 카드 한 장 가져가기
    @MessageMapping("/rooms/draw")
    public void drawCard(Map<String, String> payload) {
        String roomCode = payload.get("roomCode");
        String userId = payload.get("userId");
        String color = payload.get("color"); // "WHITE" or "BLACK"
        roomService.drawCard(roomCode, userId, color);
    }

    // 턴 넘기기
    @MessageMapping("/rooms/turn/pass")
    public void passTurn(Map<String, String> message) {
        String roomCode = message.get("roomCode");
        String userId = message.get("userId");
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        String nextTurnUserId = userId.equals(room.getHostId()) ? room.getGuestId() : room.getHostId();
        room.setCurrentTurnPlayerId(nextTurnUserId);
        roomRepository.save(room);

        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode,
                Map.of("action", "TURN_CHANGED", "payload", Map.of("nextTurnUserId", nextTurnUserId))
        );
    }

    // 현재 턴 유저 요청
    @MessageMapping("/rooms/turn/current")
    public void getCurrentTurn(Map<String, String> message) {
        String roomCode = message.get("roomCode");
        String userId = message.get("userId");
        String currentTurnPlayerId = roomService.getCurrentTurnPlayerId(roomCode);
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomCode + "/user/" + userId,
                Map.of(
                        "action", "CURRENT_TURN",
                        "payload", Map.of(
                                "currentTurnPlayerId", currentTurnPlayerId
                        )
                )
        );
    }

    // 카드 맞추기 액션 메시지 핸들러 (Copilot 방식)
    @MessageMapping("/rooms/action")
    public void guessCard(Map<String, Object> message) {
        String roomCode = (String) message.get("roomCode");
        String userId = (String) message.get("userId");
        Long targetCardId = ((Number) message.get("targetCardId")).longValue();
        int guessedNumber = ((Number) message.get("guessedNumber")).intValue();
        String guessedColor = (String) message.get("guessedColor");
        roomService.processGuess(roomCode, userId, targetCardId, guessedNumber, guessedColor);
    }

    // 방 나가기
    @MessageMapping("/rooms/leave")
    public void leaveRoom(Map<String, String> payload) {
        String roomCode = payload.get("roomCode");
        String userId = payload.get("userId");

        if (roomCode != null && userId != null) {
            roomService.leaveRoom(roomCode, userId);

            // 방 상태 업데이트 브로드캐스트
            messagingTemplate.convertAndSend(
                    "/topic/rooms/" + roomCode,
                    Map.of("action", "ROOM_UPDATED", "payload", roomService.findRoomByCode(roomCode).orElse(null))
            );

            // 전체 로비에도 방 변경 알림
            messagingTemplate.convertAndSend(
                    "/topic/rooms/update",
                    Map.of("action", "ROOM_LIST_CHANGED")
            );
        }
    }

}