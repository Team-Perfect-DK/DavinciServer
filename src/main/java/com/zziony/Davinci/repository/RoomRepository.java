package com.zziony.Davinci.repository;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    List<Room> findByStatus(RoomStatus status); // 대기 중인 방 리스트 가져오기
    Optional<Room> findByRoomCode(String roomCode); // 방 코드로 방 찾기
}
