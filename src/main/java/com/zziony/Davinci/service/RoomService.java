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

    // 방 생성 (host 설정)
    public Room createRoom(String title, String host) {
        Room room = new Room(title, host);
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

    // 방에 게스트 참여
    public Room joinRoom(String roomCode, String guestId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        if (room.getGuest() != null) {
            throw new RuntimeException("이미 방이 꽉 찼습니다.");
        }

        room.setGuest(guestId);
        return roomRepository.save(room);
    }

    // 게임 시작 (host, guest가 있어야 가능)
    public void startGame(String roomCode) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new IllegalArgumentException("방을 찾을 수 없습니다."));

        if (!room.canStartGame()) {
            throw new IllegalStateException("게임을 시작하려면 두 명의 플레이어가 필요합니다.");
        }

        room.setStatus(RoomStatus.PLAYING);
        roomRepository.save(room);
    }

    // 플레이어 나가기
    public void leaveRoom(String roomCode, String playerId) {
        Room room = roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RuntimeException("방을 찾을 수 없습니다."));

        // 나가는 사람이 host인지 guest인지 확인
        if (playerId.equals(room.getHost())) {
            room.setHost(null);
        } else if (playerId.equals(room.getGuest())) {
            room.setGuest(null);
        }

        // host가 없으면 guest를 host로 승격
        room.assignNewHostIfNeeded();

        // 방이 비어 있으면 삭제
        if (room.isEmpty()) {
            roomRepository.delete(room);
        } else {
            roomRepository.save(room);
        }
    }
}
