package com.zziony.Davinci.model;

import com.zziony.Davinci.model.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "room")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title; // 방 제목

    @Column(name = "room_code", unique = true, nullable = false)
    private String roomCode; // 방 코드 (랜덤 생성)

    @Enumerated(EnumType.STRING)
    private RoomStatus status; // WAITING(대기중) / PLAYING(게임중)

    private String host;  // 호스트 (유저 ID)
    private String guest; // 게스트 (유저 ID, 없을 수도 있음)

    public Room() {
        this.roomCode = UUID.randomUUID().toString().substring(0, 8); // 랜덤 방 코드
        this.status = RoomStatus.WAITING;
    }

    public Room(String title, String host) {
        this.title = title;
        this.host = host;
        this.roomCode = UUID.randomUUID().toString().substring(0, 8);
        this.status = RoomStatus.WAITING;
    }

    public boolean canStartGame() {
        return host != null && guest != null; // 두 명이 있어야 시작 가능
    }

    public void assignNewHostIfNeeded() {
        if (host == null && guest != null) {
            host = guest;
            guest = null;
        }
    }

    public boolean isEmpty() {
        return host == null && guest == null;
    }
}
