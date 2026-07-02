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
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
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
        LocalDateTime hostLastActiveAt = LocalDateTime.now().minusHours(2);
        LocalDateTime guestLastActiveAt = LocalDateTime.now().minusHours(2);
        room.setHostLastActiveAt(hostLastActiveAt);
        room.setGuestLastActiveAt(guestLastActiveAt);
        when(roomRepository.findByRoomCode(room.getRoomCode())).thenReturn(Optional.of(room));

        assertTrue(roomService.touchRoom(room.getRoomCode(), room.getGuestId()));

        assertEquals(hostLastActiveAt, room.getHostLastActiveAt());
        assertTrue(room.getGuestLastActiveAt().isAfter(guestLastActiveAt));
        verify(roomRepository).save(room);
    }

    @Test
    void leaveRoomPromotesGuestAndKeepsUsersWhenHostLeaves() {
        Room room = roomWithTwoPlayers();
        when(roomRepository.findByRoomCode(room.getRoomCode())).thenReturn(Optional.of(room));

        roomService.leaveRoom(room.getRoomCode(), room.getHostId());

        assertEquals("guest-id", room.getHostId());
        assertEquals("guest", room.getHostNickname());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        verify(roomRepository).save(room);
        verify(roomRepository, never()).delete(room);
        verify(userRepository, never()).findBySessionId(room.getHostId());
    }

    @Test
    void leaveRoomKeepsHostWhenGuestLeaves() {
        Room room = roomWithTwoPlayers();
        when(roomRepository.findByRoomCode(room.getRoomCode())).thenReturn(Optional.of(room));

        roomService.leaveRoom(room.getRoomCode(), room.getGuestId());

        assertEquals("host-id", room.getHostId());
        assertEquals("host", room.getHostNickname());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        verify(roomRepository).save(room);
        verify(roomRepository, never()).delete(room);
        verify(userRepository, never()).findBySessionId(room.getHostId());
    }

    @Test
    void leaveRoomResetsPlayingRoomToWaitingForRemainingPlayer() {
        Room room = roomWithTwoPlayers();
        room.setStatus(RoomStatus.PLAYING);
        room.setCurrentTurnPlayerId(room.getGuestId());
        room.setCurrentTurnHasDrawn(true);
        room.setCurrentTurnHasGuessed(true);
        when(roomRepository.findByRoomCode(room.getRoomCode())).thenReturn(Optional.of(room));

        roomService.leaveRoom(room.getRoomCode(), room.getGuestId());

        assertEquals("host-id", room.getHostId());
        assertEquals(RoomStatus.WAITING, room.getStatus());
        assertEquals(null, room.getCurrentTurnPlayerId());
        assertEquals(false, room.isCurrentTurnHasDrawn());
        assertEquals(false, room.isCurrentTurnHasGuessed());
        verify(cardService).resetCardsForRoom(room.getId());
        verify(roomRepository).save(room);
        verify(roomRepository, never()).delete(room);
    }

    @Test
    void cleanupDeletesRoomWhenAnyPlayerIsInactive() {
        Room room = roomWithTwoPlayers();
        room.setStatus(RoomStatus.PLAYING);
        room.setHostLastActiveAt(LocalDateTime.now().minusHours(4));
        room.setGuestLastActiveAt(LocalDateTime.now().minusHours(1));
        when(roomRepository.findAll()).thenReturn(List.of(room));
        when(userRepository.findBySessionId(room.getHostId())).thenReturn(Optional.empty());
        when(userRepository.findBySessionId(room.getGuestId())).thenReturn(Optional.empty());

        roomService.cleanupStaleRooms();

        verify(cardService).resetCardsForRoom(room.getId());
        verify(roomRepository).deleteAll(List.of(room));
        verify(roomRepository, never()).save(room);
    }

    @Test
    void cleanupDeletesRoomWhenBothPlayersAreInactive() {
        Room room = roomWithTwoPlayers();
        room.setHostLastActiveAt(LocalDateTime.now().minusHours(4));
        room.setGuestLastActiveAt(LocalDateTime.now().minusHours(4));
        when(roomRepository.findAll()).thenReturn(List.of(room));
        when(userRepository.findBySessionId(room.getHostId())).thenReturn(Optional.empty());
        when(userRepository.findBySessionId(room.getGuestId())).thenReturn(Optional.empty());

        roomService.cleanupStaleRooms();

        verify(cardService).resetCardsForRoom(room.getId());
        verify(roomRepository).deleteAll(List.of(room));
        verify(roomRepository, never()).save(room);
    }

    @Test
    void cleanupRunsEveryThirtyMinutes() throws NoSuchMethodException {
        Method cleanupMethod = RoomService.class.getDeclaredMethod("cleanupStaleRooms");
        Scheduled scheduled = cleanupMethod.getAnnotation(Scheduled.class);

        assertEquals(1_800_000L, scheduled.fixedRate());
    }

    @Test
    void cleanupDoesNotDeleteRoomBeforeThreeHoursOfInactivity() {
        Room room = roomWithTwoPlayers();
        room.setHostLastActiveAt(LocalDateTime.now().minusMinutes(6));
        room.setGuestLastActiveAt(LocalDateTime.now().minusMinutes(6));
        when(roomRepository.findAll()).thenReturn(List.of(room));

        roomService.cleanupStaleRooms();

        verify(cardService, never()).resetCardsForRoom(room.getId());
        verify(roomRepository, never()).deleteAll(List.of(room));
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
