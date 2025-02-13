package com.zziony.Davinci.service;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.enums.RoomStatus;
import com.zziony.Davinci.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    @Autowired
    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    // 방 생성
    public Room createRoom(String title) {
        Room room = new Room(title);
        return roomRepository.save(room);
    }

    // 대기 중인 방 리스트 조회
    public List<Room> getWaitingRooms() {
        return roomRepository.findByStatus(RoomStatus.WAITING);
    }

    // 방 코드로 특정 방 찾기
    public Optional<Room> findRoomByCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode);
    }

    // 게임 시작 시 방 상태 변경
    public void startGame(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));
        room.setStatus(RoomStatus.PLAYING);
        roomRepository.save(room);
    }
}
