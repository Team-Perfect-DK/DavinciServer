package com.zziony.Davinci.controller;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.RoomResponse;
import com.zziony.Davinci.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final RoomService roomService;
    @Autowired
    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    // 1. 방 생성
    @PostMapping("/create")
    public Room createRoom(@RequestBody Map<String, String> request) {
        String title = request.get("title");
        String hostId = request.get("hostId"); // 방장의 유저 ID
        return roomService.createRoom(title, hostId);
    }

    // 2. 방 참가
    @PostMapping("/{roomCode}/join")
    public ResponseEntity<Room> joinRoom(@PathVariable("roomCode") String roomCode, @RequestBody Map<String, String> request) {
        String guestId = request.get("guestId");
        return ResponseEntity.ok(roomService.joinRoom(roomCode, guestId));
    }

    // 3. 대기 중인 방 리스트
    @GetMapping("/waiting")
    public List<RoomResponse> getWaitingRooms() {
        return roomService.getWaitingRooms().stream()
                .map(RoomResponse::new)
                .collect(Collectors.toList());
    }

    // 4. 특정 방 조회
    @GetMapping("/{roomCode}")
    public Room getRoom(@PathVariable String roomCode) {
        return roomService.findRoomByCode(roomCode)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));
    }

    // 5. 방 나가기
    @PostMapping("/{roomCode}/leave")
    public ResponseEntity<String> leaveRoom(@PathVariable("roomCode") String roomCode, @RequestBody Map<String, String> request) {
        String userId = request.get("userId");
        roomService.leaveRoom(roomCode, userId);
        return ResponseEntity.ok("플레이어가 방에서 나갔습니다.");
    }

}