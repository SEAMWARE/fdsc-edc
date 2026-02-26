package org.seamware.edc.tmf;

import org.eclipse.edc.spi.monitor.Monitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.seamware.tmforum.party.model.OrganizationVO;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

        when(organizationApiClient.getOrganization(eq(TEST_ORGANIZATION_ID))).thenReturn(testOrganization);
        assertEquals(testOrganization, participantResolver.getOrganization(TEST_ORGANIZATION_ID).get(), "The correct organization should have been returned.");
    }

    @Test
    public void testGetOrganization_success_no_such_org() {
        when(organizationApiClient.getOrganization(eq(TEST_ORGANIZATION_ID))).thenReturn(null);
        assertTrue(participantResolver.getOrganization(TEST_ORGANIZATION_ID).isEmpty(), "If no such org exists, nothing should be returned.");
    }

    @Test
    public void testGetOrganization_success_resolve_error() {
        when(organizationApiClient.getOrganization(eq(TEST_ORGANIZATION_ID))).thenThrow(new RuntimeException("Something bad"));
        assertTrue(participantResolver.getOrganization(TEST_ORGANIZATION_ID).isEmpty(), "RuntimeExceptions should be mapped to empty orgs.");
    }

    @Test
    public void testGetTmfId_success() {
        OrganizationVO testOrganization = getValidOrganization(TEST_ORGANIZATION_ID);

        when(organizationApiClient.getByDid(eq(TEST_ORGANIZATION_ID))).thenReturn(Optional.of(testOrganization));
        assertEquals(TEST_ORGANIZATION_ID, participantResolver.getTmfId(TEST_ORGANIZATION_ID), "The correct organization id should have been returned.");
    }

    @Test
    public void testGetTmfId_success_create_org() {
        when(organizationApiClient.getByDid(eq(TEST_ORGANIZATION_ID))).thenReturn(Optional.empty());
        when(organizationApiClient.createOrganization(any())).thenReturn(getValidOrganization(TEST_ORGANIZATION_ID));
        assertEquals(TEST_ORGANIZATION_ID, participantResolver.getTmfId(TEST_ORGANIZATION_ID), "If no such org exists, a new one should be created.");
    }

    @Test
    public void testGetTmfId_resolve_error() {
        when(organizationApiClient.getByDid(eq(TEST_ORGANIZATION_ID))).thenThrow(new RuntimeException("Something bad"));

        assertThrows(RuntimeException.class, () -> participantResolver.getTmfId(TEST_ORGANIZATION_ID), "Exceptions are expected to bubble.");
    }


    private OrganizationVO getValidOrganization() {
        return new OrganizationVO();
    }

    private OrganizationVO getValidOrganization(String id) {
        return new OrganizationVO().id(id);
    }


}