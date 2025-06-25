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

    // 1) 카드 배포 (호스트 + 게스트 4장씩 총 8장)
    public void distributeCardsForRoom(Long roomId, String hostId, String guestId) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i <= 11; i++) numbers.add(i);
        Collections.shuffle(numbers);
        List<Integer> selected = numbers.subList(0, 8);

        List<List<CardColor>> combos = Arrays.asList(
                Arrays.asList(CardColor.WHITE, CardColor.BLACK, CardColor.BLACK, CardColor.BLACK),
                Arrays.asList(CardColor.WHITE, CardColor.WHITE, CardColor.BLACK, CardColor.BLACK),
                Arrays.asList(CardColor.WHITE, CardColor.WHITE, CardColor.WHITE, CardColor.BLACK)
        );
        Collections.shuffle(combos);
        List<CardColor> hostColors = new ArrayList<>(combos.get(0));
        Collections.shuffle(combos);
        List<CardColor> guestColors = new ArrayList<>(combos.get(0));

        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cards.add(buildCard(selected.get(i), hostColors.get(i), hostId, roomId));
        }
        for (int i = 0; i < 4; i++) {
            cards.add(buildCard(selected.get(i + 4), guestColors.get(i), guestId, roomId));
        }

        cardRepository.saveAll(cards);
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

    // 2) 특정 방 전체 카드 조회 (8장)
    public List<Card> getCardsByRoom(Long roomId) {
        return cardRepository.findByRoomId(roomId);
    }

    // 3) 특정 유저 카드 조회
    public List<Card> getCardsByUser(String userId, Long roomId) {
        return cardRepository.findByUserIdAndRoomId(userId, roomId);
    }

    // 4) 추측 처리
    public static class GuessResult {
        private final boolean correct;
        private final Long openedCardId;
        public GuessResult(boolean correct, Long openedCardId) {
            this.correct = correct;
            this.openedCardId = openedCardId;
        }
        public boolean isCorrect() { return correct; }
        public Long getOpenedCardId() { return openedCardId; }
    }

    public GuessResult checkGuess(CardGuessRequest req) {
        Card target = cardRepository.findById(req.getTargetCardId())
                .orElseThrow(() -> new IllegalArgumentException("대상 카드 없음"));

        boolean isCorrect = target.getNumber() == req.getGuessedNumber()
                && target.getColor().name().equalsIgnoreCase(req.getGuessedColor());

        if (isCorrect) {
            target.setStatus(CardStatus.OPEN);
            cardRepository.save(target);
            return new GuessResult(true, target.getId());
        }

        List<Card> mine = cardRepository.findByUserIdAndRoomId(req.getUserId(), target.getRoomId());
        Optional<Card> closed = mine.stream()
                .filter(c -> c.getStatus() == CardStatus.CLOSE)
                .findFirst();

        if (closed.isPresent()) {
            Card open = closed.get();
            open.setStatus(CardStatus.OPEN);
            cardRepository.save(open);
            return new GuessResult(false, open.getId());
        }

        return new GuessResult(false, null);
    }

    public boolean hasUserLost(String userId, Long roomId) {
        return cardRepository.findByUserIdAndRoomId(userId, roomId)
                .stream().allMatch(c -> c.getStatus() == CardStatus.OPEN);
    }
}
