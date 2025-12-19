package org.seamware.edc;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.util.concurrency.LockException;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Supplier;

/**
 * Implementation of the lock manager, that unlocks after a given timeout to resolve potential deadlocks
 */
public class AutomaticUnlockingLockManager {

    private static final int DEFAULT_TIMEOUT = 1000;

    private final ReadWriteLock lock;
    private final int timeout;

    private final Monitor monitor;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public AutomaticUnlockingLockManager(ReadWriteLock lock, Monitor monitor) {
        this.lock = lock;
        this.monitor = monitor;
        this.timeout = DEFAULT_TIMEOUT;
    }

    public AutomaticUnlockingLockManager(ReadWriteLock lock, int timeout, Monitor monitor) {
        this.lock = lock;
        this.timeout = timeout;
        this.monitor = monitor;
    }

    /**
     * Attempts to obtain a read lock.
     */
    public <T> T readLock(Supplier<T> work) {
        ScheduledFuture unlockFuture = executorService.schedule(() -> {
            monitor.warning("Unlock stalled read-lock.");
            lock.readLock().unlock();
        }, 10, TimeUnit.SECONDS);
        try {
            if (!lock.readLock().tryLock(timeout, TimeUnit.MILLISECONDS)) {
                throw new LockException("Timeout acquiring read lock");
            }
            try {
                return work.get();
            } finally {
                unlockFuture.cancel(true);
                lock.readLock().unlock();
            }
        } catch (InterruptedException e) {
            unlockFuture.cancel(true);
            Thread.interrupted();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Attempts to obtain a write lock.
     */
    public <T> T writeLock(Supplier<T> work) {
        ScheduledFuture unlockFuture = executorService.schedule(() -> {
            monitor.warning("Unlock stalled write-lock.");
            lock.readLock().unlock();
        }, 10, TimeUnit.SECONDS);
        try {
            if (!lock.writeLock().tryLock(timeout, TimeUnit.MILLISECONDS)) {
                throw new LockException("Timeout acquiring write lock");
            }
            try {
                return work.get();
            } finally {
                unlockFuture.cancel(true);
                lock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            unlockFuture.cancel(true);
            Thread.interrupted();
            throw new LockException(e);
        }
    }
}
