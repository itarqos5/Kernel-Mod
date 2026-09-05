package dev.kernel.fabric.render;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ReentrantThreadLocalPoolTest {
    @Test
    void reusesAndResetsThePrimaryValueAcrossSequentialCalls() {
        AtomicInteger creations = new AtomicInteger();
        AtomicInteger resets = new AtomicInteger();
        ReentrantThreadLocalPool<Object> pool = new ReentrantThreadLocalPool<>(
            () -> {
                creations.incrementAndGet();
                return new Object();
            },
            ignored -> resets.incrementAndGet()
        );

        Object first = pool.acquire();
        pool.release();
        Object second = pool.acquire();
        pool.release();

        assertSame(first, second);
        assertEquals(1, creations.get());
        assertEquals(2, resets.get());
    }

    @Test
    void nestedCallsReceiveFallbackValuesWithoutReplacingThePrimary() {
        AtomicInteger creations = new AtomicInteger();
        ReentrantThreadLocalPool<Object> pool = new ReentrantThreadLocalPool<>(
            () -> {
                creations.incrementAndGet();
                return new Object();
            },
            ignored -> {
            }
        );

        Object primary = pool.acquire();
        Object nested = pool.acquire();
        assertNotSame(primary, nested);
        pool.release();
        pool.release();

        Object reused = pool.acquire();
        pool.release();
        assertSame(primary, reused);
        assertEquals(2, creations.get());
    }

    @Test
    void primaryValuesAreIsolatedByThread() throws InterruptedException {
        ReentrantThreadLocalPool<Object> pool = new ReentrantThreadLocalPool<>(Object::new, ignored -> {
        });
        Object callerValue = pool.acquire();
        pool.release();
        AtomicReference<Object> workerValue = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            workerValue.set(pool.acquire());
            pool.release();
        });
        worker.start();
        worker.join();

        assertNotSame(callerValue, workerValue.get());
    }

    @Test
    void rejectsAnUnbalancedRelease() {
        ReentrantThreadLocalPool<Object> pool = new ReentrantThreadLocalPool<>(Object::new, ignored -> {
        });

        assertThrows(IllegalStateException.class, pool::release);
    }
}
