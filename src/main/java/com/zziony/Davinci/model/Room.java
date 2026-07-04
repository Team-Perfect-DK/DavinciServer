package com.zziony.Davinci.model;

import com.zziony.Davinci.model.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
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
    private String winnerId; // 위너 ID
    private String winnerNickname;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime hostLastActiveAt;
    private LocalDateTime guestLastActiveAt;
    @Setter
    @Getter
    private String currentTurnPlayerId;
    private boolean currentTurnHasDrawn;
    private boolean currentTurnHasGuessed;

    public Room() {
        LocalDateTime now = LocalDateTime.now();
        this.roomCode = UUID.randomUUID().toString().substring(0, 8); // 랜덤 방 코드
        this.status = RoomStatus.WAITING;
        this.createdAt = now;
        this.updatedAt = now;
        this.lastActiveAt = now;
        this.hostLastActiveAt = now;
    }

    public Room(String title, String hostId, String hostNickname) {
        LocalDateTime now = LocalDateTime.now();
        this.title = title;
        this.hostId = hostId;
        this.hostNickname = hostNickname;
        this.guestId = null;
        this.guestNickname = null;
        this.roomCode = UUID.randomUUID().toString().substring(0, 8);
        this.status = RoomStatus.WAITING;
        this.createdAt = now;
        this.updatedAt = now;
        this.lastActiveAt = now;
        this.hostLastActiveAt = now;
    }

    public boolean canStartGame() {
        return hostId != null && guestId != null; // 두 명이 있어야 시작 가능
    }

    public void assignNewHostIfNeeded() {
        if (this.hostId == null && this.guestId != null) {
            this.hostId = this.guestId;
            this.hostNickname = this.guestNickname;
            this.hostLastActiveAt = this.guestLastActiveAt;
            this.guestId = null;
            this.guestNickname = null;
            this.guestLastActiveAt = null;
        }
    }

    public boolean isEmpty() {
        return hostId == null && guestId == null;
    }

    public boolean isPlayer(String playerId) {
        return playerId != null && (playerId.equals(hostId) || playerId.equals(guestId));
    }

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.lastActiveAt == null) {
            this.lastActiveAt = now;
        }
        if (this.hostId != null && this.hostLastActiveAt == null) {
            this.hostLastActiveAt = now;
        }
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

}
