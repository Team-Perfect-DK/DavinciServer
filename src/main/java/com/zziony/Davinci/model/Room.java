package com.zziony.Davinci.model;

import com.zziony.Davinci.model.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
        return hostId != null && guestId != null; // 두 명이 있어야 시작 가능
    }

    public void assignNewHostIfNeeded() {
        if (hostId == null && guestId != null) {
            hostId = guestId;
            hostNickname = guestNickname;
            guestId = null;
            guestNickname = null;
        }
    }

    public boolean isEmpty() {
        return hostId == null && guestId == null;
    }
}
