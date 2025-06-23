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

@Controller
@RequiredArgsConstructor
public class GameWebSocketController {

    private final CardService cardService;
    private final RoomService roomService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/games/action") // 클라이언트: /app/games/action
    public void handleCardGuess(CardGuessRequest request) {

        // 1. 정답 체크 (맞췄는지, 어떤 카드가 열렸는지 반환)
        var result = cardService.checkGuess(request); // GuessResult 타입 가정

        // 2. 오답이면 턴 넘기기
        if (!result.isCorrect()) {
            roomService.passTurn(request.getRoomCode());
        }

        // 3. 게임 종료 여부 확인
        roomService.checkAndEndGameIfNeeded(request.getRoomCode());
        Room room = roomService.findRoomByCode(request.getRoomCode())
                .orElseThrow(() -> new IllegalArgumentException("방이 없습니다."));

        // 4. 클라이언트에 보낼 메시지 구성
        GameBroadcastRequest broadcast = new GameBroadcastRequest(
                room.getStatus().name().equals("ENDED") ? "GAME_ENDED" : "CARD_OPENED",
                result.getOpenedCardId(),
                room.getCurrentTurnUserId(),
                room.getWinnerNickname()
        );

        // 5. WebSocket으로 브로드캐스트
        messagingTemplate.convertAndSend(
                "/topic/games/" + request.getRoomCode(),
                broadcast
        );
    }
}
