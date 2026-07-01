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

        if (room.hasPlayer(guestId)) {
            return room;
        }
        if (room.getStatus() != RoomStatus.WAITING || room.isFull()) {
            throw new RuntimeException("입장할 수 없는 방입니다.");
        }

        User guest = userRepository.findBySessionId(guestId)
                .orElseThrow(() -> new RuntimeException("Guest not found"));
        String guestNickname = guest.getNickname();

        if (!room.addPlayer(guestId, guestNickname, LocalDateTime.now())) {
            throw new RuntimeException("이미 방이 꽉 찼습니다.");
        }

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

        if (!room.hasPlayer(playerId)) {
            throw new RuntimeException("Player is not in the room.");
        }

        deleteRoom(room);
        return room;
    }

    // 게임 시작 (host부터 턴)
    public boolean deleteRoom(String roomCode, String userId) {
        Optional<Room> roomResult = roomRepository.findByRoomCode(roomCode);
        if (roomResult.isEmpty()) {
            return false;
        }

        Room room = roomResult.get();
        if (!room.hasPlayer(userId)) {
            return false;
        }

        deleteRoom(room);
        return true;
    }

    public List<Card> startGameAndGetCards(String roomCode, String userId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        if (!room.getHostId().equals(userId)) {
            throw new IllegalStateException("방장만 게임을 시작할 수 있습니다.");
        }
        if (!room.canStartGame()) {
            throw new IllegalStateException("게임을 시작하려면 두 명 이상의 플레이어가 필요합니다.");
        }
        cardService.resetCardsForRoom(room.getId());
        room.setStatus(RoomStatus.PLAYING);
        room.setCurrentTurnPlayerId(room.getHostId());
        room.setCurrentTurnHasDrawn(false);
        room.setCurrentTurnHasGuessed(false);
        room.resetEliminatedPlayers();

        List<Card> allCards = cardService.distributeAndFetchCardsForRoom(room.getId(), room.getPlayerIds());

        roomRepository.save(room);
        notifyRoomUpdate();
        return allCards;
    }

    // Copilot 방식: 맞췄는지/틀렸는지에 따라 턴 유지/전환
    public String processNextTurn(Room room, String userId, boolean correct) {
        if (correct && !cardService.hasUserLost(userId, room.getId())) {
            room.setCurrentTurnHasGuessed(true);
            roomRepository.save(room);
            // 맞췄으면 같은 유저가 한 번 더
            return userId;
        }

        String next = getNextActivePlayer(room, userId);
        room.setCurrentTurnPlayerId(next);
        room.setCurrentTurnHasDrawn(false);
        room.setCurrentTurnHasGuessed(false);
        roomRepository.save(room);
        return next;
    }

    // 카드 한 장 가져가기
    public void drawCard(String roomCode, String userId, String color) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        validateTurn(room, userId);

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
        validateTurn(room, userId);
        if (card.getUserId() == null || card.getUserId().equals(userId) || !room.hasPlayer(card.getUserId())) {
            throw new IllegalArgumentException("상대 플레이어의 타일만 추측할 수 있습니다.");
        }
        Card openedCard = null;
        boolean correct = (card.getNumber() == guessedNumber);
        if (correct) {
            card.setStatus(CardStatus.OPEN);
            cardService.save(card);
            cardRepository.flush();
            openedCard = card;

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
        List<Map<String, Object>> eliminatedPlayers = recordNewlyEliminatedPlayers(room);
        if (finishGameIfOnlyOneActive(room, card.getRoomId())) {
            return;
        }
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
        result.put("eliminatedPlayers", eliminatedPlayers);
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

        if (!room.hasPlayer(userId)) {
            return false;
        }

        room.touchPlayer(userId, LocalDateTime.now());
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

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cleanupStaleRooms() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(6);
        List<Room> roomsToDelete = new ArrayList<>();
        for (Room room : roomRepository.findAll()) {
            if (room.isEmpty() || hasInactivePlayer(room, cutoff)) {
                cardService.resetCardsForRoom(room.getId());
                roomsToDelete.add(room);
            }
        }

        if (!roomsToDelete.isEmpty()) {
            roomRepository.deleteAll(roomsToDelete);
            roomsToDelete.forEach(room -> messagingTemplate.convertAndSend(
                    "/topic/rooms/" + room.getRoomCode(),
                    Map.of("action", "ROOM_DELETED", "payload", Map.of("roomCode", room.getRoomCode()))
            ));
        }

        if (!roomsToDelete.isEmpty()) {
            notifyRoomUpdate();
        }
    }

    private boolean hasInactivePlayer(Room room, LocalDateTime cutoff) {
        LocalDateTime legacyLastActiveAt = room.getLastActiveAt() != null
                ? room.getLastActiveAt()
                : room.getUpdatedAt() != null ? room.getUpdatedAt() : room.getCreatedAt();

        for (int seat = 1; seat <= 4; seat++) {
            if (isInactivePlayer(room.getPlayerId(seat), room.getPlayerLastActiveAt(seat), legacyLastActiveAt, cutoff)) {
                return true;
            }
        }

        return false;
    }

    public String moveToNextTurn(String roomCode, String userId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        validateTurn(room, userId);
        String next = getNextActivePlayer(room, userId);
        room.setCurrentTurnPlayerId(next);
        room.setCurrentTurnHasDrawn(false);
        room.setCurrentTurnHasGuessed(false);
        roomRepository.save(room);
        return next;
    }

    public String getPlayerNickname(String roomCode, String userId) {
        return roomRepository.findByRoomCode(roomCode)
                .map(room -> room.getNickname(userId))
                .orElse(null);
    }

    private String getNextActivePlayer(Room room, String currentUserId) {
        List<String> players = room.getPlayerIds();
        if (players.isEmpty()) return null;

        int currentIndex = players.indexOf(currentUserId);
        for (int offset = 1; offset <= players.size(); offset++) {
            String candidate = players.get((Math.max(currentIndex, 0) + offset) % players.size());
            if (!cardService.hasUserLost(candidate, room.getId())) {
                return candidate;
            }
        }
        return currentUserId;
    }

    private void validateTurn(Room room, String userId) {
        if (room.getStatus() != RoomStatus.PLAYING
                || userId == null
                || !userId.equals(room.getCurrentTurnPlayerId())) {
            throw new IllegalStateException("현재 턴인 플레이어만 행동할 수 있습니다.");
        }
    }

    private boolean finishGameIfOnlyOneActive(Room room, Long roomId) {
        List<String> activePlayers = room.getPlayerIds().stream()
                .filter(playerId -> !cardService.hasUserLost(playerId, roomId))
                .toList();
        if (activePlayers.size() != 1) {
            return false;
        }

        String winnerId = activePlayers.get(0);
        room.setStatus(RoomStatus.ENDED);
        room.setWinnerId(winnerId);
        room.setWinnerNickname(room.getNickname(winnerId));
        List<Map<String, Object>> rankings = buildRankings(room, winnerId);
        roomRepository.save(room);
        cardService.resetCardsForRoom(room.getId());
        messagingTemplate.convertAndSend("/topic/rooms/" + room.getRoomCode(),
                Map.of("action", "GAME_ENDED", "payload", Map.of(
                        "winnerNickname", room.getWinnerNickname(),
                        "rankings", rankings
                ))
        );
        notifyRoomUpdate();
        return true;
    }

    private List<Map<String, Object>> recordNewlyEliminatedPlayers(Room room) {
        List<Map<String, Object>> eliminatedPlayers = new ArrayList<>();

        for (String playerId : room.getPlayerIds()) {
            if (cardService.hasUserLost(playerId, room.getId()) && room.addEliminatedPlayer(playerId)) {
                eliminatedPlayers.add(buildRankingEntry(0, playerId, room.getNickname(playerId)));
            }
        }

        return eliminatedPlayers;
    }

    private List<Map<String, Object>> buildRankings(Room room, String winnerId) {
        List<Map<String, Object>> rankings = new ArrayList<>();
        int rank = 1;

        rankings.add(buildRankingEntry(rank++, winnerId, room.getNickname(winnerId)));

        List<String> eliminatedPlayerIds = room.getEliminatedPlayerIdsList();
        for (int index = eliminatedPlayerIds.size() - 1; index >= 0; index--) {
            String playerId = eliminatedPlayerIds.get(index);
            if (!playerId.equals(winnerId)) {
                rankings.add(buildRankingEntry(rank++, playerId, room.getNickname(playerId)));
            }
        }

        for (String playerId : room.getPlayerIds()) {
            boolean alreadyRanked = rankings.stream()
                    .anyMatch(ranking -> playerId.equals(ranking.get("userId")));
            if (!alreadyRanked) {
                rankings.add(buildRankingEntry(rank++, playerId, room.getNickname(playerId)));
            }
        }

        return rankings;
    }

    private Map<String, Object> buildRankingEntry(int rank, String userId, String nickname) {
        Map<String, Object> ranking = new HashMap<>();
        ranking.put("rank", rank);
        ranking.put("userId", userId);
        ranking.put("nickname", nickname != null ? nickname : "알 수 없음");
        return ranking;
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

    private void deleteRoom(Room room) {
        cardService.resetCardsForRoom(room.getId());
        roomRepository.delete(room);
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + room.getRoomCode(),
                Map.of("action", "ROOM_DELETED", "payload", Map.of("roomCode", room.getRoomCode()))
        );
        notifyRoomUpdate();
    }
}
