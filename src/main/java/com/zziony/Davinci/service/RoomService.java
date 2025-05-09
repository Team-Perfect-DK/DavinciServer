package com.zziony.Davinci.service;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.User;
import com.zziony.Davinci.model.enums.RoomStatus;
import com.zziony.Davinci.repository.RoomRepository;
import com.zziony.Davinci.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket 메시지 전송을 위한 템플릿

    @Autowired
    public RoomService(RoomRepository roomRepository, UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // 🔹 방 생성 (host 설정)
    public Room createRoom(String title, String hostId) {
        User user = userRepository.findBySessionId(hostId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String hostNickname = user.getNickname(); // 방장의 닉네임 가져오기
        Room room = new Room(title, hostId, hostNickname);

        Room savedRoom = roomRepository.save(room);

        // WebSocket을 통해 새로운 방 생성 알림
        messagingTemplate.convertAndSend("/topic/rooms", savedRoom);

        return savedRoom;
    }

    // 🔹 대기 중인 방 리스트 조회
    public List<Room> getWaitingRooms() {
        return roomRepository.findByStatus(RoomStatus.WAITING);
    }

    // 🔹 방 코드로 특정 방 찾기
    public Optional<Room> findRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode);
    }

    // 🔹 방에 게스트 참여 (WebSocket 알림 추가)
    public Room joinRoom(String roomCode, String guestId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        if (room.getGuestId() != null) {
            throw new RuntimeException("이미 방이 꽉 찼습니다.");
        }

        User guest = userRepository.findBySessionId(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));
        String guestNickname = guest.getNickname();

        room.setGuestId(guestId);
        room.setGuestNickname(guestNickname);
        Room updatedRoom = roomRepository.save(room);

        // WebSocket을 통해 방 참가 알림
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, updatedRoom);

        return updatedRoom;
    }

    // 🔹 게임 시작 (WebSocket 알림 추가)
    public void startGame(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        if (!room.canStartGame()) {
            throw new IllegalStateException("게임을 시작하려면 두 명의 플레이어가 필요합니다.");
        }

        room.setStatus(RoomStatus.PLAYING);
        roomRepository.save(room);

        // WebSocket을 통해 게임 시작 알림
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, "GAME_STARTED");
    }

    // 🔹 플레이어 나가기 (WebSocket 알림 추가)
    public Room leaveRoom(String roomCode, String playerId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        boolean isHost = playerId.equals(room.getHostId());
        boolean isGuest = playerId.equals(room.getGuestId());

        // 나가는 사람이 host인지 guest인지 확인
        if (isHost) {
            room.setHostId(null);
            room.setHostNickname(null);
        } else if (isGuest) {
            room.setGuestId(null);
            room.setGuestNickname(null);
        }

        // host가 없으면 guest를 host로 승격
        room.assignNewHostIfNeeded();

        // 방이 비어 있으면 삭제, 아니면 업데이트
        if (room.isEmpty()) {
            roomRepository.delete(room);
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, "ROOM_DELETED");
        } else {
            Room updatedRoom = roomRepository.save(room);
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, updatedRoom);
        }
        return room;
    }
}
