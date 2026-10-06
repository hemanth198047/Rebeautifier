package com.rebeautifier.ai;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory AI refine counters for this process (Phase 9 observability).
 */
@Component
public class AiMetrics {

    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();

    public void recordSuccess(long latencyMs) {
        attempts.incrementAndGet();
        succeeded.incrementAndGet();
        totalLatencyMs.addAndGet(Math.max(0, latencyMs));
    }

    public void recordFailure(long latencyMs) {
        attempts.incrementAndGet();
        failed.incrementAndGet();
        totalLatencyMs.addAndGet(Math.max(0, latencyMs));
    }

    public Snapshot snapshot() {
        long ok = succeeded.get();
        long fail = failed.get();
        long done = ok + fail;
        long avg = done == 0 ? 0 : totalLatencyMs.get() / done;
        return new Snapshot(attempts.get(), ok, fail, avg);
    }

    public record Snapshot(long attempts, long succeeded, long failed, long averageLatencyMs) {
    }
}
