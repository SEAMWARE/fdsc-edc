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
package org.seamware.edc.services;

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

import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.security.Vault;

/**
 * In-memory implementation of the EDC {@link Vault} interface for test environments. Stores secrets
 * in a thread-safe in-memory map, eliminating the need for an external vault service (such as
 * HashiCorp Vault) during TCK conformance testing.
 */
public class InMemoryVault implements Vault {

  private final ConcurrentHashMap<String, String> secrets = new ConcurrentHashMap<>();

  @Override
  public String resolveSecret(String key) {
    return secrets.get(key);
  }

  @Override
  public Result<Void> storeSecret(String key, String value) {
    secrets.put(key, value);
    return Result.success();
  }

  @Override
  public Result<Void> deleteSecret(String key) {
    secrets.remove(key);
    return Result.success();
  }
}
