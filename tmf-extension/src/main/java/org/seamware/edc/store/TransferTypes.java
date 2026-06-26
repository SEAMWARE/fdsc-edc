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

import java.util.Set;

/**
 * Single source of truth for the DSP transfer types supported by the FDSC connector. The catalog
 * advertises these as distribution formats and the data plane registers them as allowed transfer
 * types. To support a new type, add it to {@link #SUPPORTED} and register a matching data plane.
 */
public final class TransferTypes {

  public static final String TYPE_HTTP_DATA = "HttpData";
  public static final String HTTP_DATA_PULL = "HttpData-PULL";

  /** Transfer types the data plane can currently fulfil. */
  public static final Set<String> SUPPORTED = Set.of(HTTP_DATA_PULL);

  private TransferTypes() {}
}
