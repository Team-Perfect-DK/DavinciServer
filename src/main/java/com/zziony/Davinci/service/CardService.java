package com.zziony.Davinci.service;

import com.zziony.Davinci.model.Card;
import com.zziony.Davinci.model.enums.CardColor;
import com.zziony.Davinci.model.enums.CardStatus;
import com.zziony.Davinci.repository.CardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class CardService {

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public List<Card> distributeAndFetchCardsForRoom(Long roomId, String hostId, String guestId) {
        List<Card> allCards = new ArrayList<>();

        // 흰색 0~11
        for (int i = 0; i <= 11; i++) {
            allCards.add(buildCard(i, CardColor.WHITE, null, roomId));
        }
        // 검은색 0~11
        for (int i = 0; i <= 11; i++) {
            allCards.add(buildCard(i, CardColor.BLACK, null, roomId));
        }

        // 카드 무작위 셔플
        Collections.shuffle(allCards);

        // 4장씩 플레이어에게 할당
        List<Card> hostCards = allCards.subList(0, 4);
        List<Card> guestCards = allCards.subList(4, 8);

        for (Card c : hostCards) c.setUserId(hostId);
        for (Card c : guestCards) c.setUserId(guestId);

        // DB 저장
        cardRepository.saveAll(allCards);
        return cardRepository.findByRoomId(roomId);
    }

    private Card buildCard(int number, CardColor color, String userId, Long roomId) {
        Card c = new Card();
        c.setNumber(number);
        c.setColor(color);
        c.setStatus(CardStatus.CLOSE);
        c.setUserId(userId);
        c.setRoomId(roomId);
        return c;
    }

    // 카드 가져오기
    public Optional<Card> drawFromDeckByColor(Long roomId, CardColor color) {
        List<Card> deck = cardRepository.findByRoomIdAndUserIdAndColor(roomId, null, color);
        if (deck.isEmpty()) return Optional.empty();
        Collections.shuffle(deck);
        return Optional.of(deck.get(0));
    }

    public boolean isDeckEmpty(Long roomId) {
        return cardRepository.findByRoomIdAndUserId(roomId, null).isEmpty();
    }


    public List<Card> getCardsByRoom(Long roomId) {
        return cardRepository.findByRoomId(roomId);
    }

    public List<Card> getCardsByUser(String userId, Long roomId) {
        return cardRepository.findByUserIdAndRoomId(userId, roomId);
    }

    public Optional<Card> findCardById(Long id) {
        return cardRepository.findById(id);
    }

    public void save(Card card) {
        cardRepository.save(card);
    }

    public boolean hasUserLost(String userId, Long roomId) {
        return cardRepository.findByUserIdAndRoomId(userId, roomId)
                .stream().allMatch(c -> c.getStatus() == CardStatus.OPEN);
    }

    @Transactional
    public void resetCardsForRoom(Long roomId) {
        cardRepository.deleteByRoomId(roomId);
    }

    public void flush() {
        cardRepository.flush();
    }
}