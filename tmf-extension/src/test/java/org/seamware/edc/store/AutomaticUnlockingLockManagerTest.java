/*
 * Copyright 2025 Seamless Middleware Technologies S.L and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.seamware.edc.store;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.util.concurrency.LockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AutomaticUnlockingLockManagerTest {

  private Monitor monitor;

  @BeforeEach
  void setUp() {
    monitor = mock(Monitor.class);
  }

  @Test
  void readLock_executes_work_and_returns_result() {
    AutomaticUnlockingLockManager lockManager =
        new AutomaticUnlockingLockManager(new ReentrantReadWriteLock(true), monitor);

    String result = lockManager.readLock(() -> "hello");

    assertEquals("hello", result);
  }

  @Test
  void writeLock_executes_work_and_returns_result() {
    AutomaticUnlockingLockManager lockManager =
        new AutomaticUnlockingLockManager(new ReentrantReadWriteLock(true), monitor);

    Integer result = lockManager.writeLock(() -> 42);

    assertEquals(42, result);
  }

  @Test
  void readLock_throws_LockException_on_timeout() throws Exception {
    ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    AutomaticUnlockingLockManager lockManager =
        new AutomaticUnlockingLockManager(rwLock, 50, monitor);

    // Hold the write lock from another thread so the read lock times out
    CountDownLatch writeLockHeld = new CountDownLatch(1);
    CountDownLatch testDone = new CountDownLatch(1);
    Thread blocker =
        new Thread(
            () -> {
              rwLock.writeLock().lock();
              writeLockHeld.countDown();
              try {
                testDone.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                rwLock.writeLock().unlock();
              }
            });
    blocker.start();
    writeLockHeld.await(1, TimeUnit.SECONDS);

    try {
      assertThrows(LockException.class, () -> lockManager.readLock(() -> "should not reach"));
    } finally {
      testDone.countDown();
      blocker.join(2000);
    }
  }

  @Test
  void writeLock_throws_LockException_on_timeout() throws Exception {
    ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    AutomaticUnlockingLockManager lockManager =
        new AutomaticUnlockingLockManager(rwLock, 50, monitor);

    // Hold the write lock from another thread
    CountDownLatch writeLockHeld = new CountDownLatch(1);
    CountDownLatch testDone = new CountDownLatch(1);
    Thread blocker =
        new Thread(
            () -> {
              rwLock.writeLock().lock();
              writeLockHeld.countDown();
              try {
                testDone.await(5, TimeUnit.SECONDS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                rwLock.writeLock().unlock();
              }
            });
    blocker.start();
    writeLockHeld.await(1, TimeUnit.SECONDS);

    try {
      assertThrows(LockException.class, () -> lockManager.writeLock(() -> "should not reach"));
    } finally {
      testDone.countDown();
      blocker.join(2000);
    }
  }

  @Test
  void readLock_allows_concurrent_readers() throws Exception {
    AutomaticUnlockingLockManager lockManager =
        new AutomaticUnlockingLockManager(new ReentrantReadWriteLock(true), 5000, monitor);

    AtomicInteger concurrentReaders = new AtomicInteger(0);
    AtomicInteger maxConcurrentReaders = new AtomicInteger(0);
    CountDownLatch allStarted = new CountDownLatch(2);
    CountDownLatch proceed = new CountDownLatch(1);

    Callable<String> reader =
        () ->
            lockManager.readLock(
                () -> {
                  int current = concurrentReaders.incrementAndGet();
                  maxConcurrentReaders.updateAndGet(max -> Math.max(max, current));
                  allStarted.countDown();
                  try {
                    proceed.await(2, TimeUnit.SECONDS);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  concurrentReaders.decrementAndGet();
                  return "done";
                });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<String> f1 = executor.submit(reader);
    Future<String> f2 = executor.submit(reader);

    allStarted.await(2, TimeUnit.SECONDS);
    proceed.countDown();

    assertEquals("done", f1.get(3, TimeUnit.SECONDS));
    assertEquals("done", f2.get(3, TimeUnit.SECONDS));
    assertEquals(2, maxConcurrentReaders.get(), "Two readers should run concurrently.");

    executor.shutdownNow();
  }

  @Test
  void writeLock_blocks_concurrent_writers() throws Exception {
    AutomaticUnlockingLockManager lockManager =
        new AutomaticUnlockingLockManager(new ReentrantReadWriteLock(true), 5000, monitor);

    AtomicInteger concurrentWriters = new AtomicInteger(0);
    AtomicBoolean overlap = new AtomicBoolean(false);
    CountDownLatch bothDone = new CountDownLatch(2);

    Runnable writer =
        () -> {
          lockManager.writeLock(
              () -> {
                int current = concurrentWriters.incrementAndGet();
                if (current > 1) {
                  overlap.set(true);
                }
                try {
                  Thread.sleep(50);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                concurrentWriters.decrementAndGet();
                bothDone.countDown();
                return null;
              });
        };

    ExecutorService executor = Executors.newFixedThreadPool(2);
    executor.submit(writer);
    executor.submit(writer);

    assertTrue(bothDone.await(5, TimeUnit.SECONDS), "Both writers should complete.");
    assertFalse(overlap.get(), "Writers must not overlap.");

    executor.shutdownNow();
  }

  @Test
  void writeLock_blocks_concurrent_readers() throws Exception {
    ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    AutomaticUnlockingLockManager lockManager =
        new AutomaticUnlockingLockManager(rwLock, 5000, monitor);

    CountDownLatch writerInside = new CountDownLatch(1);
    CountDownLatch writerRelease = new CountDownLatch(1);
    AtomicBoolean readerRanDuringWrite = new AtomicBoolean(false);

    ExecutorService executor = Executors.newFixedThreadPool(2);

    // Writer holds the lock
    Future<?> writerFuture =
        executor.submit(
            () ->
                lockManager.writeLock(
                    () -> {
                      writerInside.countDown();
                      try {
                        writerRelease.await(3, TimeUnit.SECONDS);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                      return "written";
                    }));

    writerInside.await(1, TimeUnit.SECONDS);

    // Reader tries to acquire while writer holds
    Future<String> readerFuture =
        executor.submit(
            () ->
                lockManager.readLock(
                    () -> {
                      readerRanDuringWrite.set(true);
                      return "read";
                    }));

    // Give reader time to attempt the lock
    Thread.sleep(100);
    assertFalse(
        readerFuture.isDone(), "Reader should be blocked while writer holds the write lock.");

    writerRelease.countDown();
    assertEquals("read", readerFuture.get(3, TimeUnit.SECONDS));

    executor.shutdownNow();
  }

  @Test
  void emergency_unlock_cancelled_on_normal_completion() throws Exception {
    AutomaticUnlockingLockManager lockManager =
        new AutomaticUnlockingLockManager(new ReentrantReadWriteLock(true), 5000, monitor);

    // Run a quick operation — the emergency unlock should be cancelled
    String result = lockManager.writeLock(() -> "quick");
    assertEquals("quick", result);

    // If the emergency unlock fires anyway, subsequent locks would fail.
    // Verify a second operation works fine.
    String result2 = lockManager.writeLock(() -> "still works");
    assertEquals("still works", result2);
  }
}
