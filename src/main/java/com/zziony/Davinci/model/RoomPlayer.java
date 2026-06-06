package com.zziony.Davinci.model;

public record RoomPlayer(
        String id,
        String nickname,
        int seat,
        boolean host
) {
}
