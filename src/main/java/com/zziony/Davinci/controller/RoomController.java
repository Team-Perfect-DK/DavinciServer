package com.zziony.Davinci.controller;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    @Autowired
    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // 방 생성
    @PostMapping("/create")
    public Room createRoom(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String hostId = request.get("hostId"); // 방장의 유저 ID
        return roomService.createRoom(title, hostId);
    }

    // 방 참가 (guest 추가)
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<Room> joinRoom(@PathVariable("roomCode") String roomCode, @RequestBody Map<String, String> request) {
        String guestId = request.get("guestId");
        return ResponseEntity.ok(roomService.joinRoom(roomCode, guestId));
    }

    // 대기 중인 방 조회
    @GetMapping("/waiting")
    public List<Room> getWaitingRooms() {
        return roomService.getWaitingRooms();
    }

    // 특정 방 조회
    @GetMapping("/{roomCode}")
    public ResponseEntity<Room> findRoomByCode(@PathVariable("roomCode") String roomCode) {
        return roomService.findRoomByCode(roomCode)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }



    // 게임 시작
    @PostMapping("/{roomCode}/start")
    public ResponseEntity<String> startGame(@PathVariable("roomCode") String roomCode) {
        roomService.startGame(roomCode);
        return ResponseEntity.ok("게임이 시작되었습니다!");
    }

    // 방에서 나가기
    @PostMapping("/{roomCode}/leave")
    public ResponseEntity<String> leaveRoom(@PathVariable("roomCode") String roomCode, @RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        roomService.leaveRoom(roomCode, userId);
        return ResponseEntity.ok("플레이어가 방에서 나갔습니다.");
    }
}
