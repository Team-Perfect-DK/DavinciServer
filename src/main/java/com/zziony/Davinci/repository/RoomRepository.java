package com.zziony.Davinci.repository;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.enums.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

    @Repository
    public interface RoomRepository extends JpaRepository<Room, Long> {
        List<Room> findByStatus(RoomStatus status);
        @Query("SELECT r FROM Room r WHERE r.roomCode = :roomCode")
        Optional<Room> findByRoomCode(@Param("roomCode") String roomCode);

    }
