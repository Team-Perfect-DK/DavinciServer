package com.zziony.Davinci.service;

import com.zziony.Davinci.model.Card;
import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.User;
import com.zziony.Davinci.model.enums.RoomStatus;
import com.zziony.Davinci.repository.CardRepository;
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
    private final SimpMessagingTemplate messagingTemplate;
    private final CardService cardService;
    private final CardRepository cardRepository;

    @Autowired
    public RoomService(RoomRepository roomRepository, UserRepository userRepository,
                       SimpMessagingTemplate messagingTemplate, CardService cardService,
                       CardRepository cardRepository) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.cardService = cardService;
        this.cardRepository = cardRepository;
    }

    public Room createRoom(String title, String hostId) {
        User user = userRepository.findBySessionId(hostId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String hostNickname = user.getNickname();
        Room room = new Room(title, hostId, hostNickname);
        Room savedRoom = roomRepository.save(room);

        messagingTemplate.convertAndSend("/topic/rooms", savedRoom);
        notifyRoomUpdate();

        return savedRoom;
    }

    public List<Room> getWaitingRooms() {
        return roomRepository.findByStatus(RoomStatus.WAITING);
    }

    public Optional<Room> findRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode);
    }

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
        roomRepository.saveAndFlush(room);

        Map<String, Object> message = Map.of(
                "type", "ROOM_UPDATED",
                "payload", updatedRoom
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);

        return updatedRoom;
    }

    public Room startGame(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        if (!room.canStartGame()) {
            throw new IllegalStateException("게임을 시작하려면 두 명의 플레이어가 필요합니다.");
        }

        room.setStatus(RoomStatus.PLAYING);
        Room updatedRoom = roomRepository.save(room);

        cardService.distributeCardsForRoom(room.getId(), room.getHostId(), room.getGuestId());
        room.setCurrentTurnUserId(room.getHostId());
        roomRepository.save(room);

        List<Card> allCards  = cardRepository.findByRoomId(room.getId());

        Map<String, Object> message = Map.of(
                "type", "GAME_STARTED",
                "payload", Map.of(
                        "cards", allCards,
                        "room", updatedRoom
                )
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);

        return room;
    }

    public String getCurrentTurn(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방이 없습니다."));
        return room.getCurrentTurnUserId();
    }

    public void passTurn(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방이 없습니다."));

        String newTurn = room.getHostId().equals(room.getCurrentTurnUserId())
                ? room.getGuestId()
                : room.getHostId();

        room.setCurrentTurnUserId(newTurn);
        roomRepository.save(room);
    }

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

    public Room leaveRoom(String roomCode, String playerId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        boolean isHost = playerId.equals(room.getHostId());
        boolean isGuest = playerId.equals(room.getGuestId());

        if (isHost) {
            room.setHostId(null);
            room.setHostNickname(null);
        } else if (isGuest) {
            room.setGuestId(null);
            room.setGuestNickname(null);
        }

        room.assignNewHostIfNeeded();

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

    public void notifyRoomUpdate() {
        Map<String, Object> message = Map.of(
                "type", "ROOM_LIST_UPDATED",
                "payload", getWaitingRooms()
        );
        messagingTemplate.convertAndSend("/topic/rooms/update", message);
    }
}
