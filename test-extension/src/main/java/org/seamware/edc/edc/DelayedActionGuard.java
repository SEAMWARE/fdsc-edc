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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.edc.spi.entity.PendingGuard;
import org.eclipse.edc.spi.entity.StatefulEntity;
import org.eclipse.edc.spi.persistence.StateEntityStore;
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
                LOG.warning(
                    String.format(
                        "Guard background: playing step for entity=%s state=%d",
                        entry.entity.getId(), entry.entity.getState()));
                try {
                  action.accept(entry.entity);
                  LOG.warning(
                      String.format(
                          "Guard background: after step entity=%s state=%d",
                          entry.entity.getId(), entry.entity.getState()));
                } catch (Exception e) {
                  LOG.log(Level.WARNING, "Guard action failed", e);
                } finally {
                  entry.entity.setPending(false);
                  store.save(entry.entity);
                }
              }
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              break;
            }
          }
        });
  }

  /** Stops the guard's background processing loop. */
  public void stop() {
    active.set(false);
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
    final T entity;

    GuardDelay(T entity) {
      this.entity = entity;
      start = System.currentTimeMillis();
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
      return timeUnit.convert(GUARD_DELAY_MS - (System.currentTimeMillis() - start), MILLISECONDS);
    }
  }
}
