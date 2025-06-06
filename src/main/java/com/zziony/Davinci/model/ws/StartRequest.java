package com.zziony.Davinci.model.ws;

public class StartRequest {
    private String roomCode;

    public StartRequest() {
        // 기본 생성자
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }
}
