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

import java.util.ArrayList;
import java.util.List;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.transaction.spi.TransactionContext;

/**
 * TransactionContext implementation that uses saga-based compensation for multi-step operations
 * across independent TMForum REST APIs. Nested execute() calls join the existing saga context
 * rather than creating a new one.
 */
public class TMFTransactionContext implements TransactionContext {

  private final Monitor monitor;
  private final ThreadLocal<SagaContext> currentSaga = new ThreadLocal<>();
  private final ThreadLocal<List<TransactionSynchronization>> synchronizations =
      ThreadLocal.withInitial(ArrayList::new);

  public TMFTransactionContext(Monitor monitor) {
    this.monitor = monitor;
  }

  @Override
  public void execute(TransactionBlock block) {
    boolean isOutermost = currentSaga.get() == null;
    if (isOutermost) {
      monitor.debug("Starting new saga context");
      currentSaga.set(new SagaContext(monitor));
    }
    try {
      block.execute();
      if (isOutermost) {
        notifyAndClearSyncs();
      }
    } catch (Exception e) {
      if (isOutermost) {
        monitor.warning("Saga execution failed, running compensations", e);
        currentSaga.get().compensate();
      } else {
        monitor.warning("Nested saga execution failed", e);
      }
      throw e;
    } finally {
      if (isOutermost) {
        currentSaga.remove();
        synchronizations.remove();
      }
    }
  }

  @Override
  public <T> T execute(ResultTransactionBlock<T> block) {
    boolean isOutermost = currentSaga.get() == null;
    if (isOutermost) {
      monitor.debug("Starting new saga context");
      currentSaga.set(new SagaContext(monitor));
    }
    try {
      T result = block.execute();
      if (isOutermost) {
        notifyAndClearSyncs();
      }
      return result;
    } catch (Exception e) {
      if (isOutermost) {
        monitor.warning("Saga execution failed, running compensations", e);
        currentSaga.get().compensate();
      } else {
        monitor.warning("Nested saga execution failed", e);
      }
      throw e;
    } finally {
      if (isOutermost) {
        currentSaga.remove();
        synchronizations.remove();
      }
    }
  }

  @Override
  public void registerSynchronization(TransactionSynchronization sync) {
    synchronizations.get().add(sync);
  }

  /** Returns the current saga context for the calling thread, or null if none is active. */
  public SagaContext currentSaga() {
    return currentSaga.get();
  }

  private void notifyAndClearSyncs() {
    var syncList = synchronizations.get();
    syncList.forEach(TransactionSynchronization::beforeCompletion);
    syncList.clear();
  }
}
