package com.zziony.Davinci.model;

import com.zziony.Davinci.model.enums.RoomStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoomResponse {
    private Long id;
    private String title;
    private String roomCode;
    private RoomStatus status;
    private String hostNickname;
    private String guestNickname;

    public RoomResponse(Room room) {
        this.id = room.getId();
        this.title = room.getTitle();
        this.roomCode = room.getRoomCode();
        this.status = room.getStatus();
        this.hostNickname = room.getHostNickname();
        this.guestNickname = room.getGuestNickname();
    }

}
