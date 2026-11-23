package tn.supcom.smartparking.core.model;

import java.time.Instant;

/**
 * Simulated payment of a reservation (contract §2.8). No bank data:
 * {@code method} is always "simulated".
 */
public record Payment(String method, Instant paidAt, String receipt) {
    public Payment {
        if (method == null || method.isBlank()) throw new IllegalArgumentException("payment: missing method");
        if (paidAt == null) throw new IllegalArgumentException("payment: missing paid_at");
    }
}
