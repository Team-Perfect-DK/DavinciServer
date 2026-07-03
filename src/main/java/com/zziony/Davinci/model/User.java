package com.zziony.Davinci.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nickname;
    private String sessionId;
    private LocalDateTime createdAt;

    public User() {
        this.sessionId = UUID.randomUUID().toString(); // 랜덤 sessionId 생성
        this.createdAt = LocalDateTime.now();
    }

    public User(String nickname) {
        this.nickname = nickname;
        this.sessionId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
