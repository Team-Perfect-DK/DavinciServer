package com.zziony.Davinci.controller;

import com.zziony.Davinci.model.CardGuessRequest;
import com.zziony.Davinci.model.Room;
import com.zziony.Davinci.model.enums.RoomStatus;
import com.zziony.Davinci.service.CardService;
import com.zziony.Davinci.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.HashMap;
import java.util.Map;

public class GameController {

    private final CardService cardService;
    private final RoomService roomService;

    @Autowired
    public GameController(CardService cardService, RoomService roomService) {
        this.cardService = cardService;
        this.roomService = roomService;
    }

    // 정답 확인
    @PostMapping("/guess")
    public ResponseEntity<Map<String, Object>> guessCard(@RequestBody CardGuessRequest request) {
        CardService.GuessResult result = cardService.checkGuess(request);

        if (!result.correct) {
            roomService.passTurn(request.getRoomCode());
        }

        // 게임 종료 조건 체크
        roomService.checkAndEndGameIfNeeded(request.getRoomCode());

        Room room = roomService.findRoomByCode(request.getRoomCode()).orElseThrow();

        Map<String, Object> response = new HashMap<>();
        response.put("correct", result.correct);
        response.put("openedCardId", result.openedCardId);
        response.put("nextTurnUserId", room.getCurrentTurnUserId());
        response.put("gameEnded", room.getStatus() == RoomStatus.ENDED);
        response.put("winnerNickname", room.getWinnerNickname());

        return ResponseEntity.ok(response);
    }


}
