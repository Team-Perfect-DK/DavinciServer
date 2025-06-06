package com.zziony.Davinci.model.ws;

public class LeaveRequest {
    private String roomCode;
    private String userId;

    public LeaveRequest() {
        // 기본 생성자
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
