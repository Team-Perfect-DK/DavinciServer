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
    private String roomCode; // 방 코드 (랜덤 생성)

    @Enumerated(EnumType.STRING)
    private RoomStatus status; // WAITING(대기중) / PLAYING(게임중)

    public Room() {
        this.roomCode = UUID.randomUUID().toString().substring(0, 8); // 랜덤 방 코드
        this.status = RoomStatus.WAITING;
    }

    public Room(String title) {
        this.title = title;
        this.roomCode = UUID.randomUUID().toString().substring(0, 8);
        this.status = RoomStatus.WAITING;
    }
}
