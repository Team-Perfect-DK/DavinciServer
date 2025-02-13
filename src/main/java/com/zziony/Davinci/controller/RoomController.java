package com.zziony.Davinci.controller;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    @Autowired
    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // 방 생성
    @PostMapping("/create")
    public Room createRoom(@RequestParam String title) {
        return roomService.createRoom(title);
    }

    // 대기 중인 방 조회
    @GetMapping("/waiting")
    public List<Room> getWaitingRooms() {
        System.out.println("ㅎㅇㅎㅇ");
        return roomService.getWaitingRooms();
    }

    // 특정 방 조회
    @GetMapping("/{roomCode}")
    public Optional<Room> getRoomByCode(@PathVariable String roomCode) {
        return roomService.findRoomByCode(roomCode);
    }

    // 게임 시작
    @PostMapping("/{roomCode}/start")
    public String startGame(@PathVariable String roomCode) {
        roomService.startGame(roomCode);
        return "게임이 시작되었습니다!";
    }
}
