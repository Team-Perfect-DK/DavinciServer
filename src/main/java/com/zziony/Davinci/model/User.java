package com.zziony.Davinci.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;
    private String sessionId;

    public User() {
        this.sessionId = UUID.randomUUID().toString(); // 랜덤 sessionId 생성
    }

    public User(String nickname) {
        this.nickname = nickname;
        this.sessionId = UUID.randomUUID().toString();
    }
}
