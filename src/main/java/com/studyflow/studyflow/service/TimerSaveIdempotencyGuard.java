package com.studyflow.studyflow.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * In-memory idempotency guard for the Focus Timer persistence endpoint.
 *
 * Each completed timer run sends a fresh {@code clientKey} (UUID). The guard
 * remembers recently processed keys per authenticated user and refuses a
 * duplicate submission of the same run, so a double-click, a re-fired
 * completion event or a page retry can never create two StudySessions.
 *
 * Keys expire after one hour, which is far longer than any timer run, and
 * are released immediately when the save fails so a retry is always
 * possible. This is deliberately a memory-only guard: the endpoint is a
 * single-shot completion path and the StudySession domain stays untouched
 * (no extra column, per the phase constraints).
 */
@Component
public class TimerSaveIdempotencyGuard {

    private static final long TTL_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final int PRUNE_THRESHOLD = 64;

    private final ConcurrentHashMap<String, Long> processed = new ConcurrentHashMap<>();

    /**
     * Claim a client key for the given user.
     *
     * @return {@code true} when this is the first time the key is seen
     *         (the request may proceed), {@code false} when the same run
     *         was already processed or is currently in flight
     */
    public boolean acquire(String owner, String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        prune(now);
        Long previous = processed.putIfAbsent(key(owner, clientKey), now + TTL_MILLIS);
        return previous == null;
    }

    /**
     * Mark a claimed key as successfully persisted (extends the expiry).
     */
    public void commit(String owner, String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        processed.put(key(owner, clientKey), System.currentTimeMillis() + TTL_MILLIS);
    }

    /**
     * Release a claimed key after a failed save so a retry is allowed.
     */
    public void release(String owner, String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            return;
        }
        processed.remove(key(owner, clientKey));
    }

    private String key(String owner, String clientKey) {
        return owner + "|" + clientKey;
    }

    private void prune(long now) {
        if (processed.size() < PRUNE_THRESHOLD) {
            return;
        }
        processed.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}