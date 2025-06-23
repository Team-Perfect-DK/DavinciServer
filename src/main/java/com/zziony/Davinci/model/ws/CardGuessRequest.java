package com.zziony.Davinci.model.ws;

import lombok.Getter;

@Getter
public class CardGuessRequest {
    private Long targetCardId;    // 상대 카드 id
    private int guessedNumber;    // 예측 숫자
    private String guessedColor;  // 예측 색 (BLACK / WHITE)
    private String userId;        // 요청한 사람
    private String roomCode;      // 방 코드
}
