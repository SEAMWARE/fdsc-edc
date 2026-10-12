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
package org.seamware.edc.edc;

/*-
 * #%L
 * test-extension
 * %%
 * Copyright (C) 2025 - 2026 Seamless Middleware Technologies S.L
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.edc.spi.entity.PendingGuard;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.persistence.StateEntityStore;
import org.eclipse.edc.spi.result.StoreFailure;
import org.eclipse.edc.spi.result.StoreResult;
import org.jetbrains.annotations.NotNull;

/**
 * A guard that performs actions on a stateful entity.
 *
 * <p>Note this implementation is not safe to use in a clustered environment since transitions are
 * not performed in the context of a command handler.
 */
public class DelayedActionGuard<T extends StatefulEntity<T>> implements PendingGuard<T> {
  private static final Logger LOG = Logger.getLogger(DelayedActionGuard.class.getName());

  /** Delay in milliseconds before a queued guard action is executed. */
  private static final long GUARD_DELAY_MS = 500;

  /** Delay before retrying a queued action whose entity is still leased by the state machine. */
  private static final long LEASE_RETRY_DELAY_MS = 200;

  /**
   * How long a queued action keeps retrying the lease before it is abandoned. The lease is released
   * when the state machine saves the entity, not by expiry, so this bounds a state-machine cycle
   * that never saves rather than waiting out a TTL.
   */
  private static final long LEASE_WAIT_MS = 60_000;

  /** Lease failures worth waiting out; any other reason will not resolve by retrying. */
  private static final Set<StoreFailure.Reason> RETRYABLE_LEASE_FAILURES =
      EnumSet.of(StoreFailure.Reason.ALREADY_LEASED, StoreFailure.Reason.GENERAL_ERROR);

  /** Poll timeout in milliseconds for the delay queue. */
  private static final long POLL_TIMEOUT_MS = 10;

  private final Predicate<T> filter;
  private final Consumer<T> action;
  private final DelayQueue<GuardDelay> queue;
  private final AtomicBoolean active = new AtomicBoolean();
  private final ExecutorService executor = Executors.newFixedThreadPool(1);
  private final StateEntityStore<T> store;

  public DelayedActionGuard(Predicate<T> filter, Consumer<T> action, StateEntityStore<T> store) {
    this.filter = filter;
    this.action = action;
    this.store = store;
    queue = new DelayQueue<>();
  }

  /** Starts the guard's background processing loop. */
  public void start() {
    active.set(true);
    executor.submit(
        () -> {
          while (active.get()) {
            try {
              var entry = queue.poll(POLL_TIMEOUT_MS, MILLISECONDS);
              if (entry != null) {
                // Re-read the entity from the store to avoid saving a stale copy.
                // A trigger subscriber may have already advanced the entity's state
                // (e.g., AGREED → TERMINATING) since it was queued.
                var result = store.findByIdAndLease(entry.entity.getId());
                if (result.failed()) {
                  handleLeaseFailure(entry, result);
                  continue;
                }
                var current = result.getContent();
                LOG.warning(
                    String.format(
                        "Guard background: processing entity=%s state=%d",
                        current.getId(), current.getState()));
                try {
                  if (filter.test(current)) {
                    action.accept(current);
                    LOG.warning(
                        String.format(
                            "Guard background: after step entity=%s state=%d",
                            current.getId(), current.getState()));
                  } else {
                    LOG.warning(
                        String.format(
                            "Guard background: filter no longer matches entity=%s state=%d, skipping action",
                            current.getId(), current.getState()));
                  }
                } catch (Exception e) {
                  LOG.log(Level.WARNING, "Guard action failed", e);
                } finally {
                  current.setPending(false);
                  store.save(current);
                }
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              break;
            }
          }
        });
  }

  /**
   * Re-queues an entry whose lease could not be taken, or drops it when retrying cannot help.
   *
   * <p>A dropped entry leaves the entity with {@code pending = true}, which stalls it for good —
   * hence the severe log on both give-up paths.
   */
  private void handleLeaseFailure(GuardDelay entry, StoreResult<T> result) {
    if (!RETRYABLE_LEASE_FAILURES.contains(result.reason())) {
      LOG.severe(
          String.format(
              "Guard background: dropping entity=%s, it stays pending: %s (%s)",
              entry.entity.getId(), result.getFailureDetail(), result.reason()));
      return;
    }
    if (entry.leaseWaitExpired()) {
      LOG.severe(
          String.format(
              "Guard background: giving up on entity=%s after %d lease attempts over %dms,"
                  + " it stays pending: %s (%s)",
              entry.entity.getId(),
              entry.attempt,
              LEASE_WAIT_MS,
              result.getFailureDetail(),
              result.reason()));
      return;
    }
    // The state machine usually still holds the lease for a second or two after test() queued
    // the entry. Dropping it here would strand the entity as pending and hang the TCK run.
    queue.put(entry.retry());
  }

  /** Stops the guard's background processing loop and releases its worker thread. */
  public void stop() {
    active.set(false);
    executor.shutdownNow();
  }

  @Override
  public boolean test(T entity) {
    var filterResult = filter.test(entity);
    LOG.warning(
        String.format(
            "Guard test: entity=%s state=%d pending=%b filterResult=%b",
            entity.getId(), entity.getState(), entity.isPending(), filterResult));
    if (filterResult) {
      queue.put(new GuardDelay(entity));
      return true;
    }
    return false;
  }

  /** A delayed entry that holds an entity for deferred guard processing. */
  protected class GuardDelay implements Delayed {
    private final long start;
    private final long delayMs;
    private final long firstQueuedAt;
    final int attempt;
    final T entity;

    GuardDelay(T entity) {
      this(entity, GUARD_DELAY_MS, 1, System.currentTimeMillis());
    }

    private GuardDelay(T entity, long delayMs, int attempt, long firstQueuedAt) {
      this.entity = entity;
      this.delayMs = delayMs;
      this.attempt = attempt;
      this.firstQueuedAt = firstQueuedAt;
      start = System.currentTimeMillis();
    }

    /** Re-queues this entry on the shorter lease-retry delay, keeping the original deadline. */
    GuardDelay retry() {
      return new GuardDelay(entity, LEASE_RETRY_DELAY_MS, attempt + 1, firstQueuedAt);
    }

    /** True once this entry has waited for its lease longer than the lease-wait budget. */
    boolean leaseWaitExpired() {
      return System.currentTimeMillis() - firstQueuedAt >= LEASE_WAIT_MS;
    }

    @Override
    public int compareTo(@NotNull Delayed delayed) {
      var millis = getDelay(MILLISECONDS) - delayed.getDelay(MILLISECONDS);
      millis = Math.min(millis, 1);
      millis = Math.max(millis, -1);
      return (int) millis;
    }

    @Override
    public long getDelay(@NotNull TimeUnit timeUnit) {
      return timeUnit.convert(delayMs - (System.currentTimeMillis() - start), MILLISECONDS);
    }
  }
}
