package ru.hackathon.credit.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Защита от подбора пароля: не больше N неудачных попыток входа за окно времени на пару «логин и адрес».
 * Состояние хранится в памяти экземпляра; при нескольких экземплярах backend счётчик выносится в Redis или на шлюз
 * (см. docs/PRODUCTION_ROADMAP.md).
 */
@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public LoginAttemptService(@Value("${app.security.login.max-attempts:5}") int maxAttempts,
                               @Value("${app.security.login.window-minutes:15}") long windowMinutes) {
        this(maxAttempts, Duration.ofMinutes(windowMinutes), Clock.systemUTC());
    }

    LoginAttemptService(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    /** Сколько осталось ждать до следующей попытки; ноль, если вход разрешён. */
    public Duration blockedFor(String login, String clientAddress) {
        Deque<Instant> attempts = failures.get(key(login, clientAddress));
        if (attempts == null) {
            return Duration.ZERO;
        }
        synchronized (attempts) {
            prune(attempts);
            if (attempts.size() < maxAttempts) {
                return Duration.ZERO;
            }
            Duration left = Duration.between(clock.instant(), attempts.peekFirst().plus(window));
            return left.isNegative() ? Duration.ZERO : left;
        }
    }

    public void recordFailure(String login, String clientAddress) {
        Deque<Instant> attempts = failures.computeIfAbsent(key(login, clientAddress), k -> new ArrayDeque<>());
        synchronized (attempts) {
            prune(attempts);
            attempts.addLast(clock.instant());
        }
    }

    public void recordSuccess(String login, String clientAddress) {
        failures.remove(key(login, clientAddress));
    }

    private void prune(Deque<Instant> attempts) {
        Instant border = clock.instant().minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(border)) {
            attempts.pollFirst();
        }
    }

    private static String key(String login, String clientAddress) {
        return (login == null ? "" : login.trim().toLowerCase(Locale.ROOT)) + "|" + clientAddress;
    }
}
