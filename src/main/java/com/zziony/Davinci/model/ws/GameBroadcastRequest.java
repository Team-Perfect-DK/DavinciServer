package com.zziony.Davinci.model.ws;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GameBroadcastRequest {
    private String action; // "CARD_OPENED", "TURN_CHANGED", "GAME_ENDED" 등 카드 상태
    private Long cardId;   // 공개된 카드 ID
    private String nextTurnUserId;
    private String winnerNickname;
}
