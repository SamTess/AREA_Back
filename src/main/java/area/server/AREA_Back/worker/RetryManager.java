package area.server.AREA_Back.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
public class RetryManager {

    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long BASE_DELAY_SECONDS = 2;
    private static final long MAX_DELAY_SECONDS = 300;
    private static final double JITTER_FACTOR = 0.1;
    private static final double JITTER_BASE = 0.5;

    public LocalDateTime calculateNextRetryTime(final int attemptNumber) {
        if (attemptNumber >= MAX_RETRY_ATTEMPTS) {
            return null;
        }
        long delaySeconds = Math.min(
            BASE_DELAY_SECONDS * (1L << attemptNumber),
            MAX_DELAY_SECONDS
        );
        double jitter = (ThreadLocalRandom.current().nextDouble() - JITTER_BASE) * 2 * JITTER_FACTOR;
        double minFactor = 1 - JITTER_FACTOR;
        double maxFactor = 1 + JITTER_FACTOR;
        double factor = 1 + jitter;
        if (factor < minFactor) {
            factor = minFactor;
        }
        if (factor > maxFactor) {
            factor = maxFactor;
        }
        long finalDelay = Math.max(1L, Math.round(delaySeconds * factor));
        log.debug("Calculated retry delay for attempt {}: {} seconds", attemptNumber, finalDelay);
        return LocalDateTime.now().plusSeconds(finalDelay);
    }

    public boolean shouldRetry(final int attemptNumber, final Throwable error) {
        if (attemptNumber >= MAX_RETRY_ATTEMPTS) {
            log.info("Max retry attempts ({}) reached, not retrying", MAX_RETRY_ATTEMPTS);
            return false;
        }
        if (isNonRetryableError(error)) {
            log.info("Non-retryable error encountered: {}", error.getClass().getSimpleName());
            return false;
        }
        return true;
    }

    private boolean isNonRetryableError(final Throwable error) {
        if (error == null) {
            return false;
        }
        String errorMessage = error.getMessage();
        if (errorMessage != null) {
            String lowerMessage = errorMessage.toLowerCase();

            if (lowerMessage.contains("authentication")
                || lowerMessage.contains("authorization")
                || lowerMessage.contains("invalid credentials")
                || lowerMessage.contains("access denied")
                || lowerMessage.contains("forbidden")) {
                return true;
            }

            if (lowerMessage.contains("validation")
                || lowerMessage.contains("invalid request")
                || lowerMessage.contains("bad request")) {
                return true;
            }

            if (lowerMessage.contains("not found")
                || lowerMessage.contains("does not exist")) {
                return true;
            }
        }
        if (error instanceof IllegalArgumentException
            || error instanceof SecurityException) {
            return true;
        }
        return false;
    }

    public RetryStatistics getRetryStatistics(final int attemptNumber) {
        LocalDateTime nextRetry = calculateNextRetryTime(attemptNumber);
        boolean willRetry = shouldRetry(attemptNumber, null);

        return new RetryStatistics(
            attemptNumber,
            MAX_RETRY_ATTEMPTS,
            willRetry,
            nextRetry
        );
    }

    public static class RetryStatistics {
        private final int currentAttempt;
        private final int maxAttempts;
        private final boolean willRetry;
        private final LocalDateTime nextRetryTime;

        public RetryStatistics(final int currentAttempt, final int maxAttempts,
                             final boolean willRetry, final LocalDateTime nextRetryTime) {
            this.currentAttempt = currentAttempt;
            this.maxAttempts = maxAttempts;
            this.willRetry = willRetry;
            this.nextRetryTime = nextRetryTime;
        }

        public int getCurrentAttempt() {
            return currentAttempt;
        }
        public int getMaxAttempts() {
            return maxAttempts;
        }
        public boolean isWillRetry() {
            return willRetry;
        }
        public LocalDateTime getNextRetryTime() {
            return nextRetryTime;
        }
        public boolean hasReachedMaxAttempts() {
            return currentAttempt >= maxAttempts;
        }
    }
}