package com.zziony.Davinci.service;

import com.zziony.Davinci.model.Card;
import com.zziony.Davinci.model.CardGuessRequest;
import com.zziony.Davinci.model.enums.CardColor;
import com.zziony.Davinci.model.enums.CardStatus;
import com.zziony.Davinci.repository.CardRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CardService {

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public void distributeCards(String userId, Long roomId) {
        // 숫자: 0~11 중 4개 랜덤 선택
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i <= 11; i++) numbers.add(i);
        Collections.shuffle(numbers);
        List<Integer> selectedNumbers = numbers.subList(0, 4);

        // 흰/검 조합 만들기
        List<List<CardColor>> colorCombinations = Arrays.asList(
                Arrays.asList(CardColor.WHITE, CardColor.BLACK, CardColor.BLACK, CardColor.BLACK),
                Arrays.asList(CardColor.WHITE, CardColor.WHITE, CardColor.BLACK, CardColor.BLACK),
                Arrays.asList(CardColor.WHITE, CardColor.WHITE, CardColor.WHITE, CardColor.BLACK),
                Arrays.asList(CardColor.BLACK, CardColor.BLACK, CardColor.BLACK, CardColor.BLACK),
                Arrays.asList(CardColor.WHITE, CardColor.WHITE, CardColor.WHITE, CardColor.WHITE)
        );

        Collections.shuffle(colorCombinations);
        List<CardColor> selectedColors = colorCombinations.get(0);

        // 카드 생성 및 저장
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Card card = new Card();
            card.setNumber(selectedNumbers.get(i));
            card.setColor(selectedColors.get(i));
            card.setStatus(CardStatus.CLOSE);
            card.setUserId(userId);
            card.setRoomId(roomId);
            cards.add(card);
        }

        cardRepository.saveAll(cards);
    }

    public List<Card> getCardsByUser(String userId, Long roomId) {
        return cardRepository.findByUserIdAndRoomId(userId, roomId);
    }


    public class GuessResult {
        public boolean correct;
        public Long openedCardId; // 어떤 카드가 공개되었는지

        public GuessResult(boolean correct, Long openedCardId) {
            this.correct = correct;
            this.openedCardId = openedCardId;
        }
    }

    public GuessResult checkGuess(CardGuessRequest request) {
        Card target = cardRepository.findById(request.getTargetCardId())
                .orElseThrow(() -> new IllegalArgumentException("대상 카드 없음"));

        boolean isCorrect = target.getNumber() == request.getGuessedNumber()
                && target.getColor().name().equalsIgnoreCase(request.getGuessedColor());

        if (isCorrect) {
            target.setStatus(CardStatus.OPEN);
            cardRepository.save(target);
            return new GuessResult(true, target.getId());
        } else {
            // 틀렸다면 내 카드 중 CLOSE인 것 1장 OPEN
            List<Card> myCards = cardRepository.findByUserIdAndRoomId(request.getUserId(), target.getRoomId());
            Optional<Card> myClosed = myCards.stream()
                    .filter(c -> c.getStatus() == CardStatus.CLOSE)
                    .findFirst();

            if (myClosed.isPresent()) {
                Card opened = myClosed.get();
                opened.setStatus(CardStatus.OPEN);
                cardRepository.save(opened);
                return new GuessResult(false, opened.getId());
            } else {
                return new GuessResult(false, null); // 이미 다 열렸을 수도 있음
            }
        }
    }

    // 승패 확인하기
    public boolean hasUserLost(String userId, Long roomId) {
        List<Card> cards = cardRepository.findByUserIdAndRoomId(userId, roomId);
        return cards.stream().allMatch(card -> card.getStatus() == CardStatus.OPEN);
    }



}
