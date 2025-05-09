package com.zziony.Davinci.controller;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomWebSocketController {

    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public RoomWebSocketController(RoomService roomService, SimpMessagingTemplate messagingTemplate) {
        this.roomService = roomService;
        this.messagingTemplate = messagingTemplate;
    }



    @MessageMapping("/rooms/{roomCode}/leave")  // 클라이언트에서 /app/rooms/{roomCode}/leave 로 보냄
    @SendTo("/topic/rooms/{roomCode}")
    public Room leaveRoom(@DestinationVariable String roomCode, String userId) {
        Room room = roomService.leaveRoom(roomCode, userId);
        return room;
    }


}
