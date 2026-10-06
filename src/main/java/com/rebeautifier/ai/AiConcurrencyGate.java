package com.rebeautifier.ai;

import com.rebeautifier.web.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AiConcurrencyGate {

    private final Semaphore semaphore;
    private final int maxConcurrent;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger rejected = new AtomicInteger();

    public AiConcurrencyGate(@Value("${rebeautifier.ollama.max-concurrent:1}") int maxConcurrent) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
        this.semaphore = new Semaphore(this.maxConcurrent, true);
    }

    public Permit acquire() {
        boolean ok;
        try {
            ok = semaphore.tryAcquire(0, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("AI_BUSY", "AI refine interrupted while waiting", HttpStatus.TOO_MANY_REQUESTS);
        }
        if (!ok) {
            rejected.incrementAndGet();
            throw new ApiException(
                    "AI_BUSY",
                    "Another refinement is already running. Try again shortly (concurrency limit "
                            + maxConcurrent + ").",
                    HttpStatus.TOO_MANY_REQUESTS
            );
        }
        inFlight.incrementAndGet();
        return new Permit();
    }

    public int inFlight() {
        return inFlight.get();
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public int rejectedCount() {
        return rejected.get();
    }

    public final class Permit implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                inFlight.decrementAndGet();
                semaphore.release();
            }
        }
    }
}
