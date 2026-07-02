package com.zziony.Davinci.service;

import com.zziony.Davinci.model.Card;
import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.User;
import com.zziony.Davinci.model.enums.CardColor;
import com.zziony.Davinci.model.enums.CardStatus;
import com.zziony.Davinci.model.enums.RoomStatus;
import com.zziony.Davinci.repository.CardRepository;
import com.zziony.Davinci.repository.RoomRepository;
import com.zziony.Davinci.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RoomService {
    private static final long ROOM_CLEANUP_INTERVAL_MS = 1800000;
    private static final long ROOM_INACTIVITY_TIMEOUT_HOURS = 3;

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final CardService cardService;
    private final CardRepository cardRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public RoomService(RoomRepository roomRepository, UserRepository userRepository, CardService cardService, CardRepository cardRepository, SimpMessagingTemplate messagingTemplate) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.cardService = cardService;
        this.cardRepository = cardRepository;
        this.messagingTemplate = messagingTemplate;
    }

    // 방 생성
    public Room createRoom(String title, String hostId) {
        User user = userRepository.findBySessionId(hostId)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 사용자 세션입니다."));

        String hostNickname = user.getNickname();
        Room room = new Room(title, hostId, hostNickname);

        Room savedRoom = roomRepository.save(room);

        messagingTemplate.convertAndSend("/topic/rooms", savedRoom);
        notifyRoomUpdate();

        return savedRoom;
    }

    // 방 참가
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
        room.setGuestLastActiveAt(LocalDateTime.now());

        Room updatedRoom = roomRepository.save(room);
        roomRepository.saveAndFlush(room);

        Map<String, Object> message = Map.of(
                "action", "ROOM_UPDATED",
                "payload", updatedRoom
        );
        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);
        notifyRoomUpdate();

        return updatedRoom;
    }

    // 로비에 표시할 대기/진행 중인 방 조회
    public List<Room> getWaitingRooms() {
        return roomRepository.findAll().stream()
                .filter(room -> room.getStatus() == RoomStatus.WAITING
                        || room.getStatus() == RoomStatus.PLAYING)
                .toList();
    }

    // 특정 방 조회
    public Optional<Room> findRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode);
    }

    // 방 나가기
    public Room leaveRoom(String roomCode, String playerId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        boolean isHost = playerId.equals(room.getHostId());
        boolean isGuest = playerId.equals(room.getGuestId());

        if (isHost) {
            room.setHostId(null);
            room.setHostNickname(null);
            room.setHostLastActiveAt(null);
        } else if (isGuest) {
            room.setGuestId(null);
            room.setGuestNickname(null);
            room.setGuestLastActiveAt(null);
        }

        room.assignNewHostIfNeeded();

        // 게임 중이었고, 한명이라도 남아 있을 때 비정상 종료 처리
        if (room.getStatus() == RoomStatus.PLAYING && (!room.isEmpty())) {
            cardService.resetCardsForRoom(room.getId());
            room.setStatus(RoomStatus.WAITING);
            room.setWinnerNickname(null);
            room.setCurrentTurnPlayerId(null);
            room.setCurrentTurnHasDrawn(false);
            room.setCurrentTurnHasGuessed(false);

            // 남아있는 유저에게 알림 전송 (비정상 종료)
            Map<String, Object> message = Map.of(
                    "action", "GAME_RESET",
                    "payload", Map.of(
                            "reason", "상대방이 게임을 나가 게임이 리셋되었습니다.",
                            "roomCode", roomCode
                    )
            );
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);
        }

        if (room.isEmpty()) {
            roomRepository.delete(room);
            Map<String, Object> message = Map.of(
                    "action", "ROOM_DELETED",
                    "payload", Map.of("roomCode", roomCode)
            );
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);
            notifyRoomUpdate();
        } else {
            room.setStatus(RoomStatus.WAITING);
            room.setWinnerNickname(null);
            roomRepository.save(room);
            Map<String, Object> message = Map.of(
                    "action", "ROOM_UPDATED",
                    "payload", room
            );
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode, message);
            notifyRoomUpdate();
        }
        return room;
    }

    // 게임 시작 (host부터 턴)
    public void deleteRoom(String roomCode, String userId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        if (!room.isPlayer(userId)) {
            throw new IllegalArgumentException("방을 삭제할 권한이 없습니다.");
        }

        deleteRoom(room, true);
    }

    public List<Card> startGameAndGetCards(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        if (!room.canStartGame()) {
            throw new IllegalStateException("게임을 시작하려면 두 명의 플레이어가 필요합니다.");
        }
        cardService.resetCardsForRoom(room.getId());
        room.setStatus(RoomStatus.PLAYING);
        room.setCurrentTurnPlayerId(room.getHostId());
        room.setCurrentTurnHasDrawn(false);
        room.setCurrentTurnHasGuessed(false);

        List<Card> allCards = cardService.distributeAndFetchCardsForRoom(room.getId(), room.getHostId(), room.getGuestId());

        roomRepository.save(room);
        notifyRoomUpdate();
        return allCards;
    }

    // Copilot 방식: 맞췄는지/틀렸는지에 따라 턴 유지/전환
    public String processNextTurn(Room room, String userId, boolean correct) {
        if (correct) {
            room.setCurrentTurnHasGuessed(true);
            roomRepository.save(room);
            // 맞췄으면 같은 유저가 한 번 더
            return userId;
        } else {
            // 틀렸으면 상대방에게 턴
            String next = userId.equals(room.getHostId()) ? room.getGuestId() : room.getHostId();
            room.setCurrentTurnPlayerId(next);
            room.setCurrentTurnHasDrawn(false);
            room.setCurrentTurnHasGuessed(false);
            roomRepository.save(room);
            return next;
        }
    }

    // 카드 한 장 가져가기
    public void drawCard(String roomCode, String userId, String color) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        CardColor wantColor;
        try {
            wantColor = CardColor.valueOf(color);
        } catch (Exception e) {
            // 잘못된 색상
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode,
                    Map.of("action", "DRAW_FAILED", "payload", Map.of("reason", "색상 선택이 잘못되었습니다.")));
            return;
        }

        Optional<Card> drawn = cardService.drawFromDeckByColor(room.getId(), wantColor);

        if (drawn.isPresent()) {
            Card card = drawn.get();
            card.setUserId(userId);
            cardService.save(card);
            room.setCurrentTurnHasDrawn(true);
            roomRepository.save(room);

            boolean deckEmpty = cardService.isDeckEmpty(room.getId());

            // 카드 정보, 누가 뽑았는지, 남은 덱 상태 등을 broadcast
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode,
                    Map.of("action", "CARD_DRAWN",
                            "payload", Map.of(
                                    "card", card,
                                    "userId", userId,
                                    "color", color,
                                    "deckEmpty", deckEmpty
                            )));
        } else {
            // 해당 색상 카드 없음
            messagingTemplate.convertAndSend("/topic/rooms/" + roomCode,
                    Map.of("action", "DRAW_FAILED", "payload", Map.of(
                            "reason", "해당 색상의 카드가 더미에 남아있지 않습니다.",
                            "color", color
                    )));
        }
    }

    // 카드 맞추기 액션 처리 (카드 오픈, 턴 변경, 게임 종료 등)
    public void processGuess(String roomCode, String userId, Long targetCardId, int guessedNumber, String guessedColor) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        Card card = cardService.findCardById(targetCardId)
                .orElseThrow(() -> new IllegalArgumentException("카드를 찾을 수 없습니다."));
        Card openedCard = null;
        boolean correct = (card.getNumber() == guessedNumber);
        if (correct) {
            card.setStatus(CardStatus.OPEN);
            cardService.save(card);
            cardRepository.flush();
            openedCard = card;

            String opponentId = userId.equals(room.getHostId()) ? room.getGuestId() : room.getHostId();
            boolean opponentAllOpen = cardService.getCardsByUser(opponentId, card.getRoomId())
                    .stream().allMatch(c -> c.getStatus() == CardStatus.OPEN);

            if (opponentAllOpen) {
                Room updatedRoom = roomRepository.findByRoomCode(roomCode)
                        .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
                updatedRoom.setStatus(RoomStatus.ENDED);
                updatedRoom.setWinnerNickname(userRepository.findBySessionId(userId)
                        .map(User::getNickname).orElse("UNKNOWN"));
                roomRepository.save(updatedRoom);

                cardService.resetCardsForRoom(updatedRoom.getId());

                messagingTemplate.convertAndSend("/topic/rooms/" + roomCode,
                        Map.of("action", "GAME_ENDED", "payload", Map.of(
                                "winnerNickname", updatedRoom.getWinnerNickname()
                        ))
                );
                notifyRoomUpdate();
                return;
            }
        } else {
            // 내 카드 중 CLOSE인 것 하나 OPEN
            List<Card> myCards = cardService.getCardsByUser(userId, card.getRoomId());
            Optional<Card> myCloseCard = myCards.stream().filter(c -> c.getStatus() == CardStatus.CLOSE).findFirst();
            if (myCloseCard.isPresent()) {
                Card myCard = myCloseCard.get();
                myCard.setStatus(CardStatus.OPEN);
                cardService.save(myCard);
                openedCard = myCard; // 오픈된 카드 저장
            }
        }
        // 턴 관리
        String nextTurnUserId = processNextTurn(room, userId, correct);

        // 카드 맞추기 결과 broadcast
        Map<String, Object> result = new HashMap<>();
        result.put("cardId", card.getId());
        result.put("openedCardOwnerId", card.getUserId());
        result.put("openedCardOwnerNickname", userRepository.findBySessionId(card.getUserId())
                .map(User::getNickname).orElse("알 수 없음"));
        result.put("guessedNumber", guessedNumber);
        result.put("correct", correct);
        result.put("nextTurnUserId", nextTurnUserId);
        result.put("nextTurnUserNickname", userRepository.findBySessionId(nextTurnUserId)
                .map(User::getNickname).orElse("알 수 없음"));

        if (openedCard != null) {
            Map<String, Object> openedCardInfo = new HashMap<>();
            openedCardInfo.put("id", openedCard.getId());
            openedCardInfo.put("number", openedCard.getNumber());
            openedCardInfo.put("color", openedCard.getColor());
            openedCardInfo.put("status", openedCard.getStatus());
            openedCardInfo.put("userId", openedCard.getUserId());
            result.put("openedCardInfo", openedCardInfo);

            if (!correct) result.put("openedMyCardId", openedCard.getId());
        }

        messagingTemplate.convertAndSend("/topic/rooms/" + roomCode,
                Map.of("action", "CARD_OPENED", "payload", result)
        );
    }

    // 현재 턴 유저 조회
    public String getCurrentTurnPlayerId(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        return room.getCurrentTurnPlayerId();
    }

    public Map<String, Object> getGameState(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        Map<String, Object> state = new HashMap<>();
        state.put("room", room);
        state.put("cards", cardService.getCardsByRoom(room.getId()));
        state.put("deckEmpty", cardService.isDeckEmpty(room.getId()));
        return state;
    }

    public boolean touchRoom(String roomCode, String userId) {
        Optional<Room> roomResult = roomRepository.findByRoomCode(roomCode);
        if (roomResult.isEmpty()) {
            return false;
        }
        Room room = roomResult.get();

        boolean isMember = userId != null
                && (userId.equals(room.getHostId()) || userId.equals(room.getGuestId()));
        if (!isMember) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (userId.equals(room.getHostId())) {
            room.setHostLastActiveAt(now);
        } else {
            room.setGuestLastActiveAt(now);
        }
        roomRepository.save(room);
        return true;
    }

    // 룸 업데이트
    public void notifyRoomUpdate() {
        Map<String, Object> message = Map.of(
                "action", "ROOM_LIST_UPDATED",
                "payload", getWaitingRooms()
        );
        messagingTemplate.convertAndSend("/topic/rooms/update", message);
    }

    @Scheduled(fixedRate = ROOM_CLEANUP_INTERVAL_MS)
    @Transactional
    public void cleanupStaleRooms() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ROOM_INACTIVITY_TIMEOUT_HOURS);
        List<Room> roomsToDelete = new ArrayList<>();

        for (Room room : roomRepository.findAll()) {
            if (room.isEmpty() || hasInactivePlayer(room, cutoff)) {
                cardService.resetCardsForRoom(room.getId());
                roomsToDelete.add(room);
            }
        }

        if (roomsToDelete.isEmpty()) {
            return;
        }

        roomRepository.deleteAll(roomsToDelete);
        roomsToDelete.forEach(this::deleteUsersForRoom);
        roomsToDelete.forEach(room -> messagingTemplate.convertAndSend(
                "/topic/rooms/" + room.getRoomCode(),
                Map.of("action", "ROOM_DELETED", "payload", Map.of("roomCode", room.getRoomCode()))
        ));
        notifyRoomUpdate();
    }

    private boolean hasInactivePlayer(Room room, LocalDateTime cutoff) {
        LocalDateTime legacyLastActiveAt = room.getLastActiveAt() != null
                ? room.getLastActiveAt()
                : room.getUpdatedAt() != null ? room.getUpdatedAt() : room.getCreatedAt();

        return isInactivePlayer(room.getHostId(), room.getHostLastActiveAt(), legacyLastActiveAt, cutoff)
                || isInactivePlayer(room.getGuestId(), room.getGuestLastActiveAt(), legacyLastActiveAt, cutoff);
    }

    private boolean isInactivePlayer(
            String playerId,
            LocalDateTime playerLastActiveAt,
            LocalDateTime legacyLastActiveAt,
            LocalDateTime cutoff
    ) {
        if (playerId == null) {
            return false;
        }
        LocalDateTime lastActiveAt = playerLastActiveAt != null ? playerLastActiveAt : legacyLastActiveAt;
        return lastActiveAt != null && lastActiveAt.isBefore(cutoff);
    }

    private void deleteRoom(Room room, boolean deleteUsers) {
        cardService.resetCardsForRoom(room.getId());
        roomRepository.delete(room);
        if (deleteUsers) {
            deleteUsersForRoom(room);
        }
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + room.getRoomCode(),
                Map.of("action", "ROOM_DELETED", "payload", Map.of("roomCode", room.getRoomCode()))
        );
        notifyRoomUpdate();
    }

    private void deleteUsersForRoom(Room room) {
        deleteUserBySessionId(room.getHostId());
        deleteUserBySessionId(room.getGuestId());
    }

    private void deleteUserBySessionId(String sessionId) {
        if (sessionId == null) {
            return;
        }

        userRepository.findBySessionId(sessionId).ifPresent(userRepository::delete);
    }
}
