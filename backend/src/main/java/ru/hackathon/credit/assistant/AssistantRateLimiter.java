package ru.hackathon.credit.assistant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ru.hackathon.credit.common.TooManyRequestsException;

/** Не больше N обращений к помощнику в минуту на пользователя. Состояние в памяти, как у ограничения попыток входа. */
@Component
public class AssistantRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int limit;
    private final Clock clock;
    private final Map<Long, Deque<Instant>> calls = new ConcurrentHashMap<>();

    @Autowired
    public AssistantRateLimiter(AssistantProperties properties) {
        this(properties.ratePerMinute(), Clock.systemUTC());
    }

    AssistantRateLimiter(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    public void check(Long userId) {
        Deque<Instant> deque = calls.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (deque) {
            Instant now = clock.instant();
            while (!deque.isEmpty() && deque.peekFirst().isBefore(now.minus(WINDOW))) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                Duration wait = Duration.between(now, deque.peekFirst().plus(WINDOW));
                throw new TooManyRequestsException("Слишком много сообщений помощнику. Подождите немного и повторите", wait);
            }
            deque.addLast(now);
        }
    }
}
