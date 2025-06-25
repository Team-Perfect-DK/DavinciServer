package com.zziony.Davinci.controller;

import com.zziony.Davinci.model.Card;
import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.enums.RoomStatus;
import com.zziony.Davinci.model.ws.JoinRequest;
import com.zziony.Davinci.model.ws.LeaveRequest;
import com.zziony.Davinci.model.ws.StartRequest;
import com.zziony.Davinci.service.CardService;
import com.zziony.Davinci.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class RoomWebSocketController {

    private final RoomService roomService;
    private final CardService cardService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public RoomWebSocketController(RoomService roomService, CardService cardService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.cardService = cardService;
        this.messagingTemplate = messagingTemplate;
    }

    private void sendRoomUpdate(String roomCode, String action, Room room) {
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("action", action);
        wrapper.put("payload", room);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, wrapper);
    }

    @MessageMapping("/rooms/join")
    public void joinRoom(JoinRequest req) {
        Room room = roomService.joinRoom(req.getRoomCode(), req.getUserId());
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + req.getRoomCode(),
                Map.of("action", "ROOM_UPDATED", "payload", room)
        );
    }


    @MessageMapping("/rooms/leave")
    public void leaveRoom(LeaveRequest request) {
        Room room = roomService.leaveRoom(request.getRoomCode(), request.getUserId());
        sendRoomUpdate(request.getRoomCode(), "ROOM_UPDATED", room);
    }

    @MessageMapping("/rooms/start")
    public void startGame(StartRequest request) {
        Room room = roomService.findRoomByCode(request.getRoomCode())
                .orElseThrow(() -> new IllegalArgumentException("방 없음"));

        if (room.getStatus() == RoomStatus.PLAYING) {
            System.out.println("이미 시작된 게임입니다.");
            return;
        }

        Room updatedRoom = roomService.startGame(request.getRoomCode());
        List<Card> all = cardService.getCardsByRoom(updatedRoom.getId());

        Map<String,Object> payload = Map.of(
                "cards", all,
                "room", updatedRoom
        );

        messagingTemplate.convertAndSend(
                "/topic/rooms/" + request.getRoomCode(),
                Map.of("action", "GAME_STARTED", "payload", payload)
        );
    }

}
