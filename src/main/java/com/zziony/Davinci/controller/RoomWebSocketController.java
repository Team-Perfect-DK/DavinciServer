package com.zziony.Davinci.controller;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.ws.JoinRequest;
import com.zziony.Davinci.model.ws.LeaveRequest;
import com.zziony.Davinci.model.ws.StartRequest;
import com.zziony.Davinci.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class RoomWebSocketController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public RoomWebSocketController(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }

    // 1. 방 참여
    @MessageMapping("/rooms/join") // 클라이언트: /app/rooms/join
    public void joinRoom(JoinRequest request) {
        Room room = roomService.joinRoom(request.getRoomCode(), request.getUserId());
        messagingTemplate.convertAndSend("/topic/rooms/" + request.getRoomCode(), room);
    }

    // 2. 방 나가기
    @MessageMapping("/rooms/leave") // 클라이언트: /app/rooms/leave
    public void leaveRoom(LeaveRequest request) {
        Room room = roomService.leaveRoom(request.getRoomCode(), request.getUserId());
        messagingTemplate.convertAndSend("/topic/rooms/" + request.getRoomCode(), room);
    }

    // 3. 게임 시작
    @MessageMapping("/rooms/start") // 클라이언트: /app/rooms/start
    public void startGame(StartRequest request) {
        Room room = roomService.startGame(request.getRoomCode());
        messagingTemplate.convertAndSend("/topic/rooms/" + request.getRoomCode(), room);
    }
}
