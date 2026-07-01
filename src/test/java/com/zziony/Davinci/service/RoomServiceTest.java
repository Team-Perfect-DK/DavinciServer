package com.zziony.Davinci.service;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.enums.RoomStatus;
import com.zziony.Davinci.repository.CardRepository;
import com.zziony.Davinci.repository.RoomRepository;
import com.zziony.Davinci.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CardService cardService;
    @Mock
    private CardRepository cardRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(
                roomRepository,
                userRepository,
                cardService,
                cardRepository,
                messagingTemplate
        );
    }

    @Test
    void heartbeatUpdatesOnlyTheSendingPlayer() {
        Room room = roomWithTwoPlayers();
        LocalDateTime hostLastActiveAt = LocalDateTime.now().minusHours(3);
        LocalDateTime guestLastActiveAt = LocalDateTime.now().minusHours(3);
        room.setHostLastActiveAt(hostLastActiveAt);
        room.setGuestLastActiveAt(guestLastActiveAt);
        when(roomRepository.findByRoomCode(room.getRoomCode())).thenReturn(Optional.of(room));

        assertTrue(roomService.touchRoom(room.getRoomCode(), room.getGuestId()));

        assertEquals(hostLastActiveAt, room.getHostLastActiveAt());
        assertTrue(room.getGuestLastActiveAt().isAfter(guestLastActiveAt));
        verify(roomRepository).save(room);
    }

    @Test
    void cleanupDeletesRoomWhenAnyPlayerIsInactive() {
        Room room = roomWithTwoPlayers();
        LocalDateTime guestLastActiveAt = LocalDateTime.now().minusHours(1);
        room.setStatus(RoomStatus.PLAYING);
        room.setHostLastActiveAt(LocalDateTime.now().minusHours(7));
        room.setGuestLastActiveAt(guestLastActiveAt);
        when(roomRepository.findAll()).thenReturn(List.of(room));

        roomService.cleanupStaleRooms();

        verify(cardService).resetCardsForRoom(room.getId());
        verify(roomRepository).deleteAll(List.of(room));
        verify(roomRepository, never()).save(room);
    }

    @Test
    void cleanupDeletesRoomWhenBothPlayersAreInactive() {
        Room room = roomWithTwoPlayers();
        room.setHostLastActiveAt(LocalDateTime.now().minusHours(7));
        room.setGuestLastActiveAt(LocalDateTime.now().minusHours(7));
        when(roomRepository.findAll()).thenReturn(List.of(room));

        roomService.cleanupStaleRooms();

        verify(cardService).resetCardsForRoom(room.getId());
        verify(roomRepository).deleteAll(List.of(room));
        verify(roomRepository, never()).save(room);
    }

    @Test
    void leaveRoomDeletesTheRoom() {
        Room room = roomWithTwoPlayers();
        when(roomRepository.findByRoomCode(room.getRoomCode())).thenReturn(Optional.of(room));

        roomService.leaveRoom(room.getRoomCode(), room.getHostId());

        verify(cardService).resetCardsForRoom(room.getId());
        verify(roomRepository).delete(room);
        verify(roomRepository, never()).save(room);
    }

    @Test
    void deleteRoomDeletesTheRoomWhenUserIsMember() {
        Room room = roomWithTwoPlayers();
        when(roomRepository.findByRoomCode(room.getRoomCode())).thenReturn(Optional.of(room));

        assertTrue(roomService.deleteRoom(room.getRoomCode(), room.getGuestId()));

        verify(cardService).resetCardsForRoom(room.getId());
        verify(roomRepository).delete(room);
        verify(roomRepository, never()).save(room);
    }

    @Test
    void passTurnSkipsAnEliminatedPlayer() {
        Room room = roomWithTwoPlayers();
        room.addPlayer("player-3", "three", LocalDateTime.now());
        room.setStatus(RoomStatus.PLAYING);
        room.setCurrentTurnPlayerId("host-id");
        when(roomRepository.findByRoomCode(room.getRoomCode())).thenReturn(Optional.of(room));
        when(cardService.hasUserLost("guest-id", room.getId())).thenReturn(true);
        when(cardService.hasUserLost("player-3", room.getId())).thenReturn(false);

        String nextPlayer = roomService.moveToNextTurn(room.getRoomCode(), "host-id");

        assertEquals("player-3", nextPlayer);
        assertEquals("player-3", room.getCurrentTurnPlayerId());
        verify(roomRepository).save(room);
    }

    @Test
    void correctGuessMovesTurnWhenCurrentPlayerIsEliminated() {
        Room room = roomWithTwoPlayers();
        room.addPlayer("player-3", "three", LocalDateTime.now());
        room.setStatus(RoomStatus.PLAYING);
        room.setCurrentTurnPlayerId("host-id");
        when(cardService.hasUserLost("host-id", room.getId())).thenReturn(true);
        when(cardService.hasUserLost("guest-id", room.getId())).thenReturn(false);

        String nextPlayer = roomService.processNextTurn(room, "host-id", true);

        assertEquals("guest-id", nextPlayer);
        assertEquals("guest-id", room.getCurrentTurnPlayerId());
        verify(roomRepository).save(room);
    }

    private Room roomWithTwoPlayers() {
        Room room = new Room("test room", "host-id", "host");
        room.setId(1L);
        room.setRoomCode("room-code");
        room.setGuestId("guest-id");
        room.setGuestNickname("guest");
        room.setStatus(RoomStatus.WAITING);
        room.setLastActiveAt(LocalDateTime.now());
        return room;
    }
}
