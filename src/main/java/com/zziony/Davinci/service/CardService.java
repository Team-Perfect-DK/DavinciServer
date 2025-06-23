package com.zziony.Davinci.service;

import com.zziony.Davinci.model.Card;
import com.zziony.Davinci.model.enums.CardColor;
import com.zziony.Davinci.model.enums.CardStatus;
import com.zziony.Davinci.model.ws.CardGuessRequest;
import com.zziony.Davinci.repository.CardRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CardService {

    private final CardRepository cardRepository;

    public CardService(CardRepository cardRepository) {
        this.cardRepository = cardRepository;
    }

    public void distributeCardsForRoom(Long roomId, String hostId, String guestId) {
        // 0~11 중 8개 숫자만 추출
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i <= 11; i++) numbers.add(i);
        Collections.shuffle(numbers);
        List<Integer> selectedNumbers = numbers.subList(0, 8); // 총 8장

        // 색 조합은 2명 각각 따로 생성하되, 중복 제거
        List<List<CardColor>> colorCombinations = Arrays.asList(
                Arrays.asList(CardColor.WHITE, CardColor.BLACK, CardColor.BLACK, CardColor.BLACK),
                Arrays.asList(CardColor.WHITE, CardColor.WHITE, CardColor.BLACK, CardColor.BLACK),
                Arrays.asList(CardColor.WHITE, CardColor.WHITE, CardColor.WHITE, CardColor.BLACK)
        );

        Collections.shuffle(colorCombinations);
        List<CardColor> hostColors = new ArrayList<>(colorCombinations.get(0));
        Collections.shuffle(colorCombinations);
        List<CardColor> guestColors = new ArrayList<>(colorCombinations.get(0));

        List<Card> cards = new ArrayList<>();

        // host
        for (int i = 0; i < 4; i++) {
            Card card = new Card();
            card.setNumber(selectedNumbers.get(i));
            card.setColor(hostColors.get(i));
            card.setStatus(CardStatus.CLOSE);
            card.setUserId(hostId);
            card.setRoomId(roomId);
            cards.add(card);
        }

        // guest
        for (int i = 4; i < 8; i++) {
            Card card = new Card();
            card.setNumber(selectedNumbers.get(i));
            card.setColor(guestColors.get(i - 4));
            card.setStatus(CardStatus.CLOSE);
            card.setUserId(guestId);
            card.setRoomId(roomId);
            cards.add(card);
        }

        cardRepository.saveAll(cards);
    }


    public List<Card> getCardsByUser(String userId, Long roomId) {
        return cardRepository.findByUserIdAndRoomId(userId, roomId);
    }


    public class GuessResult {
        private boolean correct;
        private Long openedCardId;

        public GuessResult(boolean correct, Long openedCardId) {
            this.correct = correct;
            this.openedCardId = openedCardId;
        }

        public boolean isCorrect() { return correct; }
        public Long getOpenedCardId() { return openedCardId; }
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
