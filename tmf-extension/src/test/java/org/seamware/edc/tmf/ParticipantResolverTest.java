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
package org.seamware.edc.tmf;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.seamware.tmforum.party.model.OrganizationVO;

public class ParticipantResolverTest {

  private static final String TEST_ORGANIZATION_ID = "test-organization";

  private OrganizationApiClient organizationApiClient;
  private ParticipantResolver participantResolver;

  @BeforeEach
  public void setup() {
    organizationApiClient = mock(OrganizationApiClient.class);
    participantResolver = new ParticipantResolver(mock(Monitor.class), organizationApiClient);
  }

  @Test
  public void testGetOrganization_success() {
    OrganizationVO testOrganization = getValidOrganization();

    when(organizationApiClient.getOrganization(eq(TEST_ORGANIZATION_ID)))
        .thenReturn(testOrganization);
    assertEquals(
        testOrganization,
        participantResolver.getOrganization(TEST_ORGANIZATION_ID).get(),
        "The correct organization should have been returned.");
  }

  @Test
  public void testGetOrganization_success_no_such_org() {
    when(organizationApiClient.getOrganization(eq(TEST_ORGANIZATION_ID))).thenReturn(null);
    assertTrue(
        participantResolver.getOrganization(TEST_ORGANIZATION_ID).isEmpty(),
        "If no such org exists, nothing should be returned.");
  }

  @Test
  public void testGetOrganization_success_resolve_error() {
    when(organizationApiClient.getOrganization(eq(TEST_ORGANIZATION_ID)))
        .thenThrow(new RuntimeException("Something bad"));
    assertTrue(
        participantResolver.getOrganization(TEST_ORGANIZATION_ID).isEmpty(),
        "RuntimeExceptions should be mapped to empty orgs.");
  }

  @Test
  public void testGetTmfId_success() {
    OrganizationVO testOrganization = getValidOrganization(TEST_ORGANIZATION_ID);

    when(organizationApiClient.getByDid(eq(TEST_ORGANIZATION_ID)))
        .thenReturn(Optional.of(testOrganization));
    assertEquals(
        TEST_ORGANIZATION_ID,
        participantResolver.getTmfId(TEST_ORGANIZATION_ID),
        "The correct organization id should have been returned.");
  }

  @Test
  public void testGetTmfId_success_create_org() {
    when(organizationApiClient.getByDid(eq(TEST_ORGANIZATION_ID))).thenReturn(Optional.empty());
    when(organizationApiClient.createOrganization(any()))
        .thenReturn(getValidOrganization(TEST_ORGANIZATION_ID));
    assertEquals(
        TEST_ORGANIZATION_ID,
        participantResolver.getTmfId(TEST_ORGANIZATION_ID),
        "If no such org exists, a new one should be created.");
  }

  @Test
  public void testGetTmfId_resolve_error() {
    when(organizationApiClient.getByDid(eq(TEST_ORGANIZATION_ID)))
        .thenThrow(new RuntimeException("Something bad"));

    assertThrows(
        RuntimeException.class,
        () -> participantResolver.getTmfId(TEST_ORGANIZATION_ID),
        "Exceptions are expected to bubble.");
  }

  private OrganizationVO getValidOrganization() {
    return new OrganizationVO();
  }

  private OrganizationVO getValidOrganization(String id) {
    return new OrganizationVO().id(id);
  }
}
