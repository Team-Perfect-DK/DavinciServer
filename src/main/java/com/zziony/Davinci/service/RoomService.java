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
import java.util.Map;
import java.util.Optional;

@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate; // WebSocket 메시지 전송을 위한 템플릿
    private final CardService cardService;


    @Autowired
    public RoomService(RoomRepository roomRepository, UserRepository userRepository, SimpMessagingTemplate messagingTemplate, CardService cardService) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.cardService = cardService;
    }

    // 방 생성 (host 설정)
    public Room createRoom(String title, String hostId) {
        User user = userRepository.findBySessionId(hostId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String hostNickname = user.getNickname(); // 방장의 닉네임 가져오기
        Room room = new Room(title, hostId, hostNickname);

        Room savedRoom = roomRepository.save(room);

        // WebSocket을 통해 새로운 방 생성 알림
        messagingTemplate.convertAndSend("/topic/rooms", savedRoom);
        notifyRoomUpdate();

        return savedRoom;
    }

    // 대기 중인 방 리스트 조회
    public List<Room> getWaitingRooms() {
        return roomRepository.findByStatus(RoomStatus.WAITING);
    }

    // 방 코드로 특정 방 찾기
    public Optional<Room> findRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode);
    }

    // 방에 게스트 참여 (WebSocket 알림 추가)
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

        // WebSocket을 통해 방 참가 알림
        Room updatedRoom = roomRepository.save(room);
        roomRepository.saveAndFlush(room);

        Map<String, Object> message = Map.of(
                "type", "ROOM_UPDATED",
                "payload", updatedRoom
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);

        return updatedRoom;
    }

    // 게임 시작 (WebSocket 알림 추가)
    public Room startGame(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        if (!room.canStartGame()) {
            throw new IllegalStateException("게임을 시작하려면 두 명의 플레이어가 필요합니다.");
        }

        room.setStatus(RoomStatus.PLAYING);
        Room updatedRoom = roomRepository.save(room);

        Map<String, Object> message = Map.of(
                "type", "GAME_STARTED",
                "payload", updatedRoom
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);

        cardService.distributeCards(room.getHostId(), room.getId());
        cardService.distributeCards(room.getGuestId(), room.getId());
        room.setCurrentTurnUserId(room.getHostId()); // 호스트부터 시작

        return room;
    }

    // 게임 턴 진행하는 id 가져오기
    public String getCurrentTurn(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방이 없습니다."));
        return room.getCurrentTurnUserId();
    }

    // 게임 턴 진행하기
    public void passTurn(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방이 없습니다."));

        String newTurn = room.getHostId().equals(room.getCurrentTurnUserId())
                ? room.getGuestId()
                : room.getHostId();

        room.setCurrentTurnUserId(newTurn);
        roomRepository.save(room);
    }

    // 게임 종료하기
    public void checkAndEndGameIfNeeded(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방이 없습니다."));

        boolean hostLost = cardService.hasUserLost(room.getHostId(), room.getId());
        boolean guestLost = cardService.hasUserLost(room.getGuestId(), room.getId());

        if (hostLost || guestLost) {
            room.setStatus(RoomStatus.ENDED);

            if (hostLost) {
                room.setWinnerId(room.getGuestId());
                room.setWinnerNickname(room.getGuestNickname());
            } else {
                room.setWinnerId(room.getHostId());
                room.setWinnerNickname(room.getHostNickname());
            }

            roomRepository.save(room);
        }
    }



    // 플레이어 나가기 (WebSocket 알림 추가)
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
            Map<String, Object> message = Map.of(
                    "type", "ROOM_DELETED",
                    "payload", Map.of("roomCode", roomCode)
            );
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);
        } else {
            Room updatedRoom = roomRepository.save(room);
            Map<String, Object> message = Map.of(
                    "type", "ROOM_UPDATED",
                    "payload", updatedRoom
            );
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);
        }

        notifyRoomUpdate();
        return room;
    }

    // 룸 업데이트
    public void notifyRoomUpdate() {
        Map<String, Object> message = Map.of(
                "type", "ROOM_LIST_UPDATED",
                "payload", getWaitingRooms()
        );
        messagingTemplate.convertAndSend("/topic/rooms/update", message);
    }
}
