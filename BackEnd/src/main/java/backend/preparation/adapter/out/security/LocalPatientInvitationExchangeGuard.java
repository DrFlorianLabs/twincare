package backend.preparation.adapter.out.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import backend.preparation.application.PatientInvitationUnavailableException;
import backend.preparation.application.port.out.PatientInvitationExchangeGuard;

@Component
public class LocalPatientInvitationExchangeGuard implements PatientInvitationExchangeGuard {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maximumAttempts;
    private final Deque<Instant> attempts = new ArrayDeque<>();

    public LocalPatientInvitationExchangeGuard(
            @Value("${app.preparation.exchange-rate-limit-per-minute:120}") int maximumAttempts) {
        if (maximumAttempts < 1 || maximumAttempts > 10_000) {
            throw new IllegalArgumentException("Patient invitation exchange rate limit is invalid");
        }
        this.maximumAttempts = maximumAttempts;
    }

    @Override
    public synchronized void requireAttemptAllowed(Instant occurredAt) {
        Instant threshold = occurredAt.minus(WINDOW);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(threshold)) {
            attempts.removeFirst();
        }
        if (attempts.size() >= maximumAttempts) {
            throw new PatientInvitationUnavailableException();
        }
        attempts.addLast(occurredAt);
    }
}
