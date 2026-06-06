package com.zziony.Davinci.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomMultiplayerTest {

    @Test
    void roomAcceptsUpToFourPlayers() {
        Room room = new Room("multiplayer", "player-1", "one");

        assertTrue(room.addPlayer("player-2", "two", LocalDateTime.now()));
        assertTrue(room.addPlayer("player-3", "three", LocalDateTime.now()));
        assertTrue(room.addPlayer("player-4", "four", LocalDateTime.now()));
        assertFalse(room.addPlayer("player-5", "five", LocalDateTime.now()));

        assertEquals(4, room.getPlayerCount());
        assertTrue(room.isFull());
        assertEquals(4, room.getPlayers().size());
    }

    @Test
    void leavingHostPromotesTheNextSeat() {
        Room room = new Room("multiplayer", "player-1", "one");
        room.addPlayer("player-2", "two", LocalDateTime.now());
        room.addPlayer("player-3", "three", LocalDateTime.now());

        room.removePlayer("player-1");

        assertEquals("player-2", room.getHostId());
        assertEquals("two", room.getHostNickname());
        assertEquals(2, room.getPlayerCount());
    }
}
