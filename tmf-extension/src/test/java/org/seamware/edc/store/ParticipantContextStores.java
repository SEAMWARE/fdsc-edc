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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.edc.participantcontext.spi.store.ParticipantContextStore;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.result.StoreResult;

/** Mocked {@link ParticipantContextStore}s for the multi-context store tests. */
final class ParticipantContextStores {

  private ParticipantContextStores() {}

  /** A store holding exactly the given contexts. */
  static ParticipantContextStore holding(String... participantContextIds) {
    ParticipantContextStore store = mock(ParticipantContextStore.class);
    when(store.findById(anyString())).thenAnswer(i -> StoreResult.notFound(i.getArgument(0)));
    for (String participantContextId : participantContextIds) {
      when(store.findById(participantContextId))
          .thenReturn(StoreResult.success(mock(ParticipantContext.class)));
    }
    return store;
  }
}
