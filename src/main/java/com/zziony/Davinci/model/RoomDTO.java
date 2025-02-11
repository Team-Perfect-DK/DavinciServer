package com.zziony.Davinci.model;

import com.zziony.Davinci.model.enums.RoomStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RoomDTO {
    private Long roomId; // 방 고유 ID
    private String roomName; // 방 이름
    private List<UserDTO> players; // 참여 유저 목록
    private RoomStatus roomStatus; // 게임 시작 여부


    // 생성자
    public RoomDTO(Long roomId, String roomName, RoomStatus roomStatus) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.players = new ArrayList<>();
        this.roomStatus = roomStatus;
    }

    // 유저 추가 메소드
    public void addUser(UserDTO user) {
        this.players.add(user);
    }

    // 유저 삭제 메소드
    public void removeUser(UserDTO user) {
        this.players.remove(user);
    }

}
