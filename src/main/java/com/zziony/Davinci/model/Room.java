package com.zziony.Davinci.model;

import com.zziony.Davinci.model.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title; // 방 제목

    @Column(name = "room_code", unique = true, nullable = false)
    private String roomCode; // 방 코드 (랜덤 생성)

    @Column(name = "current_turn_user_id")
    private String currentTurnUserId; // 현재 턴인 유저아이디

    @Enumerated(EnumType.STRING)
    private RoomStatus status; // WAITING(대기중) / PLAYING(게임중) / ENDED(게임끝)

    private String hostId;  // 호스트 (유저 ID)
    private String hostNickname;
    private String guestId; // 게스트 (유저 ID, 없을 수도 있음)
    private String guestNickname;
    private String player3Id;
    private String player3Nickname;
    private String player4Id;
    private String player4Nickname;
    private String winnerId; // 위너 ID
    private String winnerNickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime hostLastActiveAt;
    private LocalDateTime guestLastActiveAt;
    private LocalDateTime player3LastActiveAt;
    private LocalDateTime player4LastActiveAt;
    @Setter
    @Getter
    private String currentTurnPlayerId;
    private boolean currentTurnHasDrawn;
    private boolean currentTurnHasGuessed;

    public Room() {
        this.roomCode = UUID.randomUUID().toString().substring(0, 8); // 랜덤 방 코드
        this.status = RoomStatus.WAITING;
    }

    public Room(String title, String hostId, String hostNickname) {
        this.title = title;
        this.hostId = hostId;
        this.hostNickname = hostNickname;
        this.guestId = null;
        this.guestNickname = null;
        this.roomCode = UUID.randomUUID().toString().substring(0, 8);
        this.status = RoomStatus.WAITING;
    }

    public boolean canStartGame() {
        return getPlayerCount() >= 2;
    }

    public void assignNewHostIfNeeded() {
        if (this.hostId != null) {
            return;
        }
        for (int seat = 2; seat <= 4; seat++) {
            if (getPlayerId(seat) != null) {
                setPlayer(1, getPlayerId(seat), getPlayerNickname(seat), getPlayerLastActiveAt(seat));
                clearPlayer(seat);
                return;
            }
        }
    }

    public boolean isEmpty() {
        return getPlayerCount() == 0;
    }

    public int getPlayerCount() {
        return getPlayerIds().size();
    }

    public boolean isFull() {
        return getPlayerCount() >= 4;
    }

    public boolean hasPlayer(String userId) {
        return userId != null && getPlayerIds().contains(userId);
    }

    @Transient
    public List<String> getPlayerIds() {
        List<String> players = new ArrayList<>();
        for (int seat = 1; seat <= 4; seat++) {
            String playerId = getPlayerId(seat);
            if (playerId != null) players.add(playerId);
        }
        return players;
    }

    @Transient
    public List<RoomPlayer> getPlayers() {
        List<RoomPlayer> players = new ArrayList<>();
        for (int seat = 1; seat <= 4; seat++) {
            String playerId = getPlayerId(seat);
            if (playerId != null) {
                players.add(new RoomPlayer(
                        playerId,
                        getPlayerNickname(seat),
                        seat,
                        seat == 1
                ));
            }
        }
        return players;
    }

    public boolean addPlayer(String userId, String nickname, LocalDateTime activeAt) {
        if (hasPlayer(userId)) return true;
        for (int seat = 2; seat <= 4; seat++) {
            if (getPlayerId(seat) == null) {
                setPlayer(seat, userId, nickname, activeAt);
                return true;
            }
        }
        return false;
    }

    public boolean removePlayer(String userId) {
        for (int seat = 1; seat <= 4; seat++) {
            if (userId != null && userId.equals(getPlayerId(seat))) {
                clearPlayer(seat);
                assignNewHostIfNeeded();
                return true;
            }
        }
        return false;
    }

    public String getNickname(String userId) {
        for (int seat = 1; seat <= 4; seat++) {
            if (userId != null && userId.equals(getPlayerId(seat))) {
                return getPlayerNickname(seat);
            }
        }
        return null;
    }

    public void touchPlayer(String userId, LocalDateTime activeAt) {
        for (int seat = 1; seat <= 4; seat++) {
            if (userId != null && userId.equals(getPlayerId(seat))) {
                setPlayerLastActiveAt(seat, activeAt);
                return;
            }
        }
    }

    public String getPlayerId(int seat) {
        return switch (seat) {
            case 1 -> hostId;
            case 2 -> guestId;
            case 3 -> player3Id;
            case 4 -> player4Id;
            default -> null;
        };
    }

    public String getPlayerNickname(int seat) {
        return switch (seat) {
            case 1 -> hostNickname;
            case 2 -> guestNickname;
            case 3 -> player3Nickname;
            case 4 -> player4Nickname;
            default -> null;
        };
    }

    public LocalDateTime getPlayerLastActiveAt(int seat) {
        return switch (seat) {
            case 1 -> hostLastActiveAt;
            case 2 -> guestLastActiveAt;
            case 3 -> player3LastActiveAt;
            case 4 -> player4LastActiveAt;
            default -> null;
        };
    }

    public void setPlayer(int seat, String id, String nickname, LocalDateTime activeAt) {
        switch (seat) {
            case 1 -> {
                hostId = id;
                hostNickname = nickname;
                hostLastActiveAt = activeAt;
            }
            case 2 -> {
                guestId = id;
                guestNickname = nickname;
                guestLastActiveAt = activeAt;
            }
            case 3 -> {
                player3Id = id;
                player3Nickname = nickname;
                player3LastActiveAt = activeAt;
            }
            case 4 -> {
                player4Id = id;
                player4Nickname = nickname;
                player4LastActiveAt = activeAt;
            }
            default -> throw new IllegalArgumentException("Invalid seat: " + seat);
        }
    }

    public void clearPlayer(int seat) {
        setPlayer(seat, null, null, null);
    }

    public void setPlayerLastActiveAt(int seat, LocalDateTime activeAt) {
        switch (seat) {
            case 1 -> hostLastActiveAt = activeAt;
            case 2 -> guestLastActiveAt = activeAt;
            case 3 -> player3LastActiveAt = activeAt;
            case 4 -> player4LastActiveAt = activeAt;
            default -> throw new IllegalArgumentException("Invalid seat: " + seat);
        }
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.lastActiveAt = now;
        this.hostLastActiveAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
