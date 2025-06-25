package com.zziony.Davinci.controller;

import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.ws.CardGuessRequest;
import com.zziony.Davinci.model.ws.GameBroadcastRequest;
import com.zziony.Davinci.service.CardService;
import com.zziony.Davinci.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final CardService cardService;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/games/action")
    public void handleCardGuess(CardGuessRequest request) {
        var result = cardService.checkGuess(request);

        if (!result.isCorrect()) {
            roomService.passTurn(request.getRoomCode());
        }

        roomService.checkAndEndGameIfNeeded(request.getRoomCode());
        Room room = roomService.findRoomByCode(request.getRoomCode())
                .orElseThrow();

        GameBroadcastRequest b = new GameBroadcastRequest(
                result.isCorrect() ? "CARD_OPENED" : "TURN_CHANGED",
                result.getOpenedCardId(),
                room.getCurrentTurnUserId(),
                room.getWinnerNickname()
        );

        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("action", b.getAction());
        Map<String, Object> payload = new HashMap<>();
        payload.put("cardId", b.getCardId());
        payload.put("nextTurnUserId", b.getNextTurnUserId());
        payload.put("winnerNickname", b.getWinnerNickname());
        wrapper.put("payload", payload);

        messagingTemplate.convertAndSend("/topic/games/" + request.getRoomCode(), wrapper);

        if (room.getStatus().name().equals("ENDED")) {
            wrapper.put("action", "GAME_ENDED");
            wrapper.put("payload", Map.of("winnerNickname", room.getWinnerNickname()));
            messagingTemplate.convertAndSend("/topic/games/" + request.getRoomCode(), wrapper);
        }
    }
}
