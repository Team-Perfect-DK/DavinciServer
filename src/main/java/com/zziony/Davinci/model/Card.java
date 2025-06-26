package com.zziony.Davinci.model;

import com.zziony.Davinci.model.enums.CardColor;
import com.zziony.Davinci.model.enums.CardStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "cards")
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int number; // 카드 숫자 (1~11)

    @Enumerated(EnumType.STRING)
    private CardColor color; // 흰색 or 검은색

    @Enumerated(EnumType.STRING)
    private CardStatus status = CardStatus.CLOSE; // 기본값은 닫힘

    private String userId; // 소유자 ID

    private Long roomId; // 어느 방에 속한 카드인지
}
