package com.zziony.Davinci.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDTO {
    private Long userId;       // 유저 ID
    private String username;   // 유저 이름
    private boolean isReady;   // 준비 상태 (optional)

    public UserDTO(Long userId, String username, boolean isReady) {
        this.userId = userId;
        this.username = username;
        this.isReady = isReady;
    }
}
