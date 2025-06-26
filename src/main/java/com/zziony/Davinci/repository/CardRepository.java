package com.zziony.Davinci.repository;

import com.zziony.Davinci.model.Card;
import com.zziony.Davinci.model.enums.CardColor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findByUserIdAndRoomId(String userId, Long roomId);
    List<Card> findByRoomId(Long roomId);
    List<Card> findByRoomIdAndUserId(Long roomId, String userId);
    List<Card> findByRoomIdAndUserIdAndColor(Long roomId, String userId, CardColor color);
}
