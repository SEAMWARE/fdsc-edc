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
package org.seamware.edc.edr;

import java.util.function.BiFunction;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceStore;
import org.eclipse.edc.participantcontext.spi.types.ParticipantResource;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;

/**
 * {@code AuthorizationService} lookup for EDR entries: resolves an entry only within its owning
 * participant context. Returns {@code null} for a foreign or unknown id, which the service maps to
 * 404.
 */
public class EdrOwnershipLookup implements BiFunction<String, String, ParticipantResource> {

  static final String PARTICIPANT_CONTEXT_ID_PROPERTY = "participantContextId";
  static final String ID_PROPERTY = "id";
  static final String EQUALS_OPERATOR = "=";

  private final EndpointDataReferenceStore store;

  public EdrOwnershipLookup(EndpointDataReferenceStore store) {
    this.store = store;
  }

  @Override
  public ParticipantResource apply(String ownerId, String transferProcessId) {
    var query =
        QuerySpec.Builder.newInstance()
            .filter(Criterion.criterion(PARTICIPANT_CONTEXT_ID_PROPERTY, EQUALS_OPERATOR, ownerId))
            .filter(Criterion.criterion(ID_PROPERTY, EQUALS_OPERATOR, transferProcessId))
            .build();
    var result = store.query(query);
    if (result.failed()) {
      return null;
    }
    return result.getContent().stream().findFirst().orElse(null);
  }
}
