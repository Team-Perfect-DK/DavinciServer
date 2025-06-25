package com.zziony.Davinci.model.ws;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
public class CardGuessRequest {
    private Long targetCardId;
    private int guessedNumber;
    private String guessedColor;
    private String userId;
    private String roomCode;
}
