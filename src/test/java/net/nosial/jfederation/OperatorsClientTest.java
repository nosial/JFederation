package net.nosial.jfederation;

import net.nosial.jfederation.classes.Json;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.OperatorConfiguration;
import net.nosial.jfederation.records.OperatorCreated;
import net.nosial.jfederation.records.OperatorPermissions;
import net.nosial.jfederation.records.OperatorRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperatorsClientTest extends FederationClientTestBase {

    private String createOperatorUuid(String prefix) {
        String uuid = client.createOperator(prefix + "_" + UUID.randomUUID().toString().substring(0, 5)).uuid();
        createdOperators.add(uuid);
        return uuid;
    }

    @Test
    void testCreateOperatorNoPermissions() {
        String name = "test_operator_" + UUID.randomUUID().toString().substring(0, 8);
        OperatorCreated uuidCreated = client.createOperator(name);
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);
        assertNotNull(uuid);

        OperatorRecord rec = client.getOperator(uuid);
        assertNotNull(rec);
        assertFalse(rec.hasManagementPermissions());
        assertFalse(rec.hasOperatorPermissions());
        assertFalse(rec.hasClientPermissions());
        assertFalse(rec.isAutoAssigned());
        assertFalse(rec.isBot());
        assertNotNull(rec.configuration(), "The configuration is disclosed to operators with operator permissions");
        assertNotNull(uuidCreated.accessToken());
    }

    @Test
    void testCreateOperatorWithManagementPermission() {
        String uuid = client.createOperator("mgmt_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);
        client.setManagementPermissions(uuid, true);

        OperatorRecord rec = client.getOperator(uuid);
        assertTrue(rec.hasManagementPermissions());
        assertFalse(rec.hasOperatorPermissions());
        assertTrue(rec.hasClientPermissions(), "Management permissions inherit client permissions");
    }

    @Test
    void testCreateOperatorWithOperatorPermission() {
        String uuid = client.createOperator("op_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);
        client.setOperatorPermissions(uuid, true);

        OperatorRecord rec = client.getOperator(uuid);
        assertFalse(rec.hasManagementPermissions());
        assertTrue(rec.hasOperatorPermissions());
        assertFalse(rec.hasClientPermissions());
    }

    @Test
    void testCreateOperatorWithClientPermission() {
        String uuid = client.createOperator("client_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);
        client.setClientPermissions(uuid, true);

        OperatorRecord rec = client.getOperator(uuid);
        assertFalse(rec.hasManagementPermissions());
        assertFalse(rec.hasOperatorPermissions());
        assertTrue(rec.hasClientPermissions());
    }

    @Test
    void testCreateOperatorWithAllPermissions() {
        String uuid = client.createOperator("all_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);
        client.setManagementPermissions(uuid, true);
        client.setOperatorPermissions(uuid, true);
        client.setClientPermissions(uuid, true);

        OperatorRecord rec = client.getOperator(uuid);
        assertTrue(rec.hasManagementPermissions());
        assertTrue(rec.hasOperatorPermissions());
        assertTrue(rec.hasClientPermissions());
    }

    @Test
    void testDeleteOperator() {
        String uuid = client.createOperator("delete_test_" + randomUuid().substring(0, 8)).uuid();
        assertNotNull(uuid);
        assertNotNull(client.getOperator(uuid));

        client.deleteOperator(uuid);

        try {
            client.getOperator(uuid);
            fail("Expected FederationClientException for deleted operator");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testCreateInvalidOperatorName() {
        String name = "a".repeat(256);
        try {
            client.createOperator(name);
            fail("Expected FederationClientException for long name");
        } catch (FederationClientException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    @Test
    void testDeleteNonExistentOperator() {
        try {
            client.deleteOperator(UUID.randomUUID().toString());
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testGetNonExistentOperator() {
        try {
            client.getOperator(UUID.randomUUID().toString());
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testDisabledOperator() {
        OperatorCreated uuidCreated = client.createOperator("disable_test_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);

        client.disableOperator(uuid);
        OperatorRecord rec = client.getOperator(uuid);
        assertTrue(rec.disabled());

        FederationClient opClient = track(new FederationClient(serverEndpoint, uuidCreated.accessToken()));
        try {
            opClient.getSelf();
            fail("Expected FederationClientException for disabled operator");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        opClient.close();
    }

    @Test
    void testEnableDisabledOperator() {
        OperatorCreated uuidCreated = client.createOperator("enable_test_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);

        client.disableOperator(uuid);
        assertTrue(client.getOperator(uuid).disabled());

        FederationClient opClient = track(new FederationClient(serverEndpoint, uuidCreated.accessToken()));
        try {
            opClient.getSelf();
            fail("Expected exception for disabled operator");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }

        client.enableOperator(uuid);
        assertFalse(client.getOperator(uuid).disabled());
        opClient.close();
    }

    @Test
    void testListOperators() {
        for (int i = 0; i < 5; i++) {
            OperatorCreated uuidCreated = client.createOperator("list_test_" + i);
        String uuid = uuidCreated.uuid();
            createdOperators.add(uuid);
        }

        List<OperatorRecord> operators = client.listOperators(1, 100);
        assertNotNull(operators);
        assertTrue(operators.size() >= 5);
        for (OperatorRecord op : operators) {
            assertNotNull(op.uuid());
            assertNotNull(op.name());
        }
    }

    @Test
    void testOperatorLifecycleIntegrity() {
        String name = "lifecycle_" + randomUuid().substring(0, 8);
        OperatorCreated uuidCreated = client.createOperator(name);
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);

        OperatorRecord op = client.getOperator(uuid);
        assertEquals(name, op.name());
        assertFalse(op.hasManagementPermissions());
        assertFalse(op.hasOperatorPermissions());
        assertFalse(op.hasClientPermissions());
        assertFalse(op.isAutoAssigned());
        assertFalse(op.disabled());
        assertNotNull(uuidCreated.accessToken());

        client.setManagementPermissions(uuid, true);
        client.setOperatorPermissions(uuid, true);
        client.setClientPermissions(uuid, true);
        client.setAutoAssign(uuid, true);
        OperatorRecord updated = client.getOperator(uuid);
        assertTrue(updated.hasManagementPermissions());
        assertTrue(updated.hasOperatorPermissions());
        assertTrue(updated.hasClientPermissions());
        assertTrue(updated.isAutoAssignReports());
        assertTrue(updated.isAutoAssignAutomatedReports());

        client.setAutoAssign(uuid, false);
        assertFalse(client.getOperator(uuid).isAutoAssigned());

        client.disableOperator(uuid);
        assertTrue(client.getOperator(uuid).disabled());
        client.enableOperator(uuid);
        assertFalse(client.getOperator(uuid).disabled());

        String origToken = uuidCreated.accessToken();
        String newToken = client.generateOperatorAccessToken(uuid);
        assertNotEquals(origToken, newToken);

        FederationClient newTokenClient = track(new FederationClient(serverEndpoint, newToken));
        assertEquals(uuid, newTokenClient.getSelf().uuid());
        newTokenClient.close();

        client.deleteOperator(uuid);
        removeFromCleanup(createdOperators, uuid);
        try {
            client.getOperator(uuid);
            fail("Expected FederationClientException for deleted operator");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testOperatorClientPermissionAuthorized() {
        OperatorCreated uuidCreated = client.createOperator("client_auth_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);
        client.setClientPermissions(uuid, true);

        FederationClient opClient = track(new FederationClient(serverEndpoint, uuidCreated.accessToken()));

        String entityUuid = opClient.pushEntity("example.com", "client_auth_user_" + randomUuid().substring(0, 8));
        assertNotNull(entityUuid);
        client.deleteEntity(entityUuid);
        opClient.close();
    }

    @Test
    void testOperatorClientPermissionUnauthorized() {
        OperatorCreated uuidCreated = client.createOperator("client_unauth_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);

        FederationClient opClient = track(new FederationClient(serverEndpoint, uuidCreated.accessToken()));

        try {
            opClient.pushEntity("example.com", "unauth_user");
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        opClient.close();
    }

    @Test
    void testOperatorManageOperatorsPermissionAuthorized() {
        OperatorCreated uuidCreated = client.createOperator("manager_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);
        client.setOperatorPermissions(uuid, true);

        FederationClient opClient = track(new FederationClient(serverEndpoint, uuidCreated.accessToken()));

        String childUuid = opClient.createOperator("child_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(childUuid);
        assertNotNull(childUuid);

        OperatorRecord child = client.getOperator(childUuid);
        assertNotNull(child);
        opClient.close();
    }

    @Test
    void testOperatorManageOperatorPermissionUnauthorized() {
        OperatorCreated uuidCreated = client.createOperator("no_mgr_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);

        FederationClient opClient = track(new FederationClient(serverEndpoint, uuidCreated.accessToken()));

        try {
            opClient.createOperator("child");
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        opClient.close();
    }

    @Test
    void testOperatorPermissionConsistency() {
        String uuid = client.createOperator("perm_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);

        client.setManagementPermissions(uuid, true);
        assertTrue(client.getOperator(uuid).hasManagementPermissions());
        client.setManagementPermissions(uuid, false);
        assertFalse(client.getOperator(uuid).hasManagementPermissions());
        // Revoking management permissions leaves the inherited client permissions in place
        assertTrue(client.getOperator(uuid).hasClientPermissions());
        client.setClientPermissions(uuid, false);

        client.setOperatorPermissions(uuid, true);
        assertTrue(client.getOperator(uuid).hasOperatorPermissions());
        client.setOperatorPermissions(uuid, false);
        assertFalse(client.getOperator(uuid).hasOperatorPermissions());

        client.setClientPermissions(uuid, true);
        assertTrue(client.getOperator(uuid).hasClientPermissions());
        client.setClientPermissions(uuid, false);
        assertFalse(client.getOperator(uuid).hasClientPermissions());
    }

    @Test
    void testSetAutoAssign() {
        String uuid = client.createOperator("auto_assign_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);

        assertFalse(client.getOperator(uuid).isAutoAssigned());

        client.setAutoAssign(uuid, true);
        OperatorRecord assigned = client.getOperator(uuid);
        assertTrue(assigned.isAutoAssignReports());
        assertTrue(assigned.isAutoAssignAutomatedReports());

        client.setAutoAssign(uuid, false);
        assertFalse(client.getOperator(uuid).isAutoAssigned());
    }

    @Test
    void testSetAutoAssignRequiresOperatorPermissions() {
        FederationClient clientOnly = createLimitedOperator("auto_assign_client_only", true);
        String fakeUuid = "00000000-0000-0000-0000-000000000000";

        expectRequestFailure(() -> clientOnly.setAutoAssign(fakeUuid, true), 403);
        clientOnly.close();
    }

    @Test
    void testSetAutoAssignEmptyUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.setAutoAssign("", true));
    }

    @Test
    void testSetAutoAssignNonExistentOperator() {
        assertThrows(FederationClientException.class,
            () -> client.setAutoAssign("00000000-0000-0000-0000-000000000000", true));
    }

    @Test
    void testOperatorAccessTokenIntegrity() {
        OperatorCreated uuidCreated = client.createOperator("token_test_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);

        String origToken = uuidCreated.accessToken();
        assertNotNull(origToken);

        FederationClient opClient = track(new FederationClient(serverEndpoint, origToken));
        assertEquals(uuid, opClient.getSelf().uuid());
        opClient.close();

        String newToken = client.generateOperatorAccessToken(uuid);
        assertNotEquals(origToken, newToken);

        try {
            FederationClient oldClient = track(new FederationClient(serverEndpoint, origToken));
            oldClient.getSelf();
            fail("Expected FederationClientException for old token");
        } catch (FederationClientException e) {
            assertEquals(401, e.getStatusCode());
        }
    }

    @Test
    void testSecurityUnauthenticatedRequestsAreRejected() {
        FederationClient anon = createAnonymousClient();
        String fakeUuid = "00000000-0000-0000-0000-000000000000";

        expectRequestFailure(anon::getSelf, new int[]{401, 403});
        expectRequestFailure(() -> anon.createOperator("unauthorized"), new int[]{401, 403});
        expectRequestFailure(() -> anon.disableOperator(fakeUuid), new int[]{401, 403});
        expectRequestFailure(() -> anon.deleteOperator(fakeUuid), new int[]{401, 403});
        expectRequestFailure(() -> anon.setOperatorPermissions(fakeUuid, true), new int[]{401, 403});
        expectRequestFailure(() -> anon.setManagementPermissions(fakeUuid, true), new int[]{401, 403});
        expectRequestFailure(() -> anon.setClientPermissions(fakeUuid, true), new int[]{401, 403});
        expectRequestFailure(() -> anon.setAutoAssign(fakeUuid, true), new int[]{401, 403});
        expectRequestFailure(() -> anon.generateOperatorAccessToken(fakeUuid), new int[]{401, 403});
        anon.close();
    }

    @Test
    void testUnauthenticatedRequestsCanReadOperatorRecords() {
        // Operator records are public since v1.0-R2 of the OFD Specification
        String rootUuid = client.getSelf().uuid();
        FederationClient anon = createAnonymousClient();

        List<OperatorRecord> operators = anon.listOperators(1, 100);
        assertTrue(operators.stream().anyMatch(o -> o.uuid().equals(rootUuid)), "Anonymous operator list should include the root operator");
        assertEquals(rootUuid, anon.getOperator(rootUuid).uuid());
        anon.close();
    }

    @Test
    void testMasterAccessTokenResolvesToRootOperator() {
        FederationClient master = track(new FederationClient(serverEndpoint, serverAccessToken));
        OperatorRecord root = master.getSelf();
        assertEquals("root", root.name());
        assertTrue(root.hasManagementPermissions());
        assertTrue(root.hasOperatorPermissions());
        assertTrue(root.hasClientPermissions());
        assertFalse(root.disabled());
        master.close();
    }

    @Test
    void testSecurityReservedOperatorNamesAreRejected() {
        String[] reserved = {"root", "system", "ROOT", "System"};
        for (String name : reserved) {
            try {
                client.createOperator(name);
                fail("Reserved name '" + name + "' should be rejected");
            } catch (FederationClientException e) {
                assertEquals(400, e.getStatusCode());
            }
        }
    }

    @Test
    void testSecurityPermissionRevocationIsEffective() {
        FederationClient clientOp = createLimitedOperator("revocation_test", true);
        String opUuid = clientOp.getSelf().uuid();
        createdOperators.add(opUuid);

        String entity1 = clientOp.pushEntity("revocation-test.com", "user1");
        createdEntities.add(entity1);
        assertNotNull(entity1);

        client.setClientPermissions(opUuid, false);

        try {
            clientOp.pushEntity("revocation-test2.com", "user2");
            fail("Revoked permission should prevent pushing entities");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }

        client.setClientPermissions(opUuid, true);
        String entity3 = clientOp.pushEntity("revocation-test3.com", "user3");
        createdEntities.add(entity3);
        assertNotNull(entity3);
        clientOp.close();
    }

    @Test
    void testSecurityDisabledOperatorCannotAuthenticate() {
        OperatorCreated uuidCreated = client.createOperator("disabled_auth_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);
        client.setClientPermissions(uuid, true);

        FederationClient opClient = track(new FederationClient(serverEndpoint, uuidCreated.accessToken()));
        assertNotNull(opClient.getSelf().uuid());

        client.disableOperator(uuid);

        try {
            opClient.getSelf();
            fail("Disabled operator should not be able to call getSelf");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        opClient.close();
    }

    @Test
    void testOperatorCannotDisableOrDeleteSelf() {
        FederationClient manager = createLimitedOperator("self_protection", true, true, false);
        String selfUuid = manager.getSelf().uuid();
        createdOperators.add(selfUuid);

        try {
            manager.disableOperator(selfUuid);
            fail("Operator should not disable themselves");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 403);
        }

        try {
            manager.deleteOperator(selfUuid);
            fail("Operator should not delete themselves");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 403 || e.getStatusCode() == 500);
        }
        manager.close();
    }

    @Test
    void testSecurityClientOnlyOperatorCannotPerformPrivilegedActions() {
        FederationClient clientOnly = createLimitedOperator("client_only_priv", true);
        String entityUuid = createSecurityEntity();
        String reportUuid = createReportForEntity(entityUuid);
        String blacklistUuid = createSecurityBlacklist(entityUuid);

        expectRequestFailure(() -> clientOnly.createOperator("child"), 403);
        expectRequestFailure(() -> clientOnly.blacklistEntity(entityUuid, reportUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+3600)), 403);
        expectRequestFailure(() -> clientOnly.deleteEntity(entityUuid), 403);
        expectRequestFailure(() -> clientOnly.deleteBlacklistRecord(blacklistUuid), 403);
        expectRequestFailure(() -> clientOnly.liftBlacklistRecord(blacklistUuid), 403);
        clientOnly.close();
    }

    @Test
    void testSecurityRootOperatorCannotBeModified() {
        OperatorRecord root = client.getSelf();
        FederationClient attacker = createLimitedOperator("root_attacker", true, true, true);

        expectRequestFailure(() -> attacker.disableOperator(root.uuid()), 403);
        expectRequestFailure(() -> attacker.deleteOperator(root.uuid()), 403);
        expectRequestFailure(() -> attacker.setOperatorPermissions(root.uuid(), false), 403);
        expectRequestFailure(() -> attacker.setManagementPermissions(root.uuid(), false), 403);
        expectRequestFailure(() -> attacker.setClientPermissions(root.uuid(), false), 403);
        expectRequestFailure(() -> attacker.setAutoAssign(root.uuid(), false), 403);
        expectRequestFailure(() -> attacker.generateOperatorAccessToken(root.uuid()), 403);
        attacker.close();
    }

    @Test
    void testSecurityMalformedAccessTokensAreRejected() {
        FederationClient shortClient = track(new FederationClient(serverEndpoint, "short"));
        try {
            shortClient.getSelf();
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401);
        }
        shortClient.close();
    }

    @Test
    void testAccessTokenRedactedInOperatorRecord() {
        OperatorCreated targetCreated = client.createOperator("redaction_test_" + randomUuid().substring(0, 8));
        String targetUuid = targetCreated.uuid();
        createdOperators.add(targetUuid);

        // The raw access token is only exposed at operator creation time (and by refresh endpoints),
        // never on OperatorRecord.
        assertNotNull(targetCreated.accessToken());
    }

    @Test
    void testPermissionChangeIsEffectiveAcrossMultipleClients() {
        OperatorCreated uuidCreated = client.createOperator("cross_client_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);
        client.setClientPermissions(uuid, true);
        String token = uuidCreated.accessToken();

        FederationClient clientA = track(new FederationClient(serverEndpoint, token));
        FederationClient clientB = track(new FederationClient(serverEndpoint, token));

        String entityUuid = clientA.pushEntity("cross-client-perm.com", "user");
        createdEntities.add(entityUuid);

        client.setClientPermissions(uuid, false);

        try {
            clientB.pushEntity("cross-client-perm-denied.com", "user2");
            fail("Revoked permission should be seen by second client");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }

        client.setClientPermissions(uuid, true);
        clientA.close();
        clientB.close();
    }

    @Test
    void testUpdateOperatorPermissionsReturnsTheUpdatedPermissions() {
        String uuid = createOperatorUuid("ofd2_perms");

        OperatorPermissions permissions = client.updateOperatorPermissions(uuid, null, true, null);
        assertEquals(new OperatorPermissions(true, true, false), permissions, "Management permissions inherit client permissions");

        // Groups given as null are left unchanged
        permissions = client.updateOperatorPermissions(uuid, null, null, true);
        assertEquals(new OperatorPermissions(true, true, true), permissions);
        assertEquals(permissions, client.getOperator(uuid).permissions());

        // Revoking both at once is not a conflict
        assertEquals(new OperatorPermissions(false, false, true), client.updateOperatorPermissions(uuid, false, false, null));
    }

    @Test
    void testRevokingClientPermissionsFromManagerConflicts() {
        String uuid = createOperatorUuid("ofd2_conflict");
        client.setManagementPermissions(uuid, true);

        expectRequestFailure(() -> client.updateOperatorPermissions(uuid, false, null, null), 409);
        expectRequestFailure(() -> client.setClientPermissions(uuid, false), 409);
        assertTrue(client.getOperator(uuid).hasManagementPermissions(), "A rejected update leaves the permissions unchanged");
    }

    @Test
    void testUpdatePermissionsRequiresOperatorPermissions() {
        String target = createOperatorUuid("ofd2_perm_target");
        FederationClient manager = createLimitedOperator("ofd2_perm_mgr", true, false, true);

        expectRequestFailure(() -> manager.updateOperatorPermissions(target, true, null, null), 403);
        expectRequestFailure(() -> createAnonymousClient().updateOperatorPermissions(target, true, null, null), new int[]{401, 403});
    }

    @Test
    void testUpdateOperatorConfigurationLeavesUnsuppliedSettingsUnchanged() {
        String uuid = createOperatorUuid("ofd2_config");

        OperatorConfiguration configuration = client.updateOperatorConfiguration(uuid, true, null, null);
        assertEquals(new OperatorConfiguration(true, false, false), configuration);

        configuration = client.updateOperatorConfiguration(uuid, null, true, null);
        assertEquals(new OperatorConfiguration(true, true, false), configuration);
        assertEquals(configuration, client.getOperator(uuid).configuration());

        client.setAutoAssign(uuid, false);
        assertEquals(new OperatorConfiguration(false, false, false), client.getOperator(uuid).configuration());
    }

    @Test
    void testOperatorChangesItsOwnConfiguration() {
        FederationClient self = createLimitedOperator("ofd2_self_config", false, false, true);
        String selfUuid = self.getSelf().uuid();

        assertEquals(new OperatorConfiguration(true, false, false), self.updateOperatorConfiguration(selfUuid, true, null, null));
        assertNotNull(self.getSelf().configuration(), "An operator receives its own configuration");

        // Changing the configuration of another operator requires operator permissions
        String other = createOperatorUuid("ofd2_other_config");
        expectRequestFailure(() -> self.updateOperatorConfiguration(other, true, null, null), 403);
    }

    @Test
    void testConfigurationIsHiddenFromOtherOperators() {
        String target = createOperatorUuid("ofd2_hidden");
        client.setAutoAssign(target, true);

        FederationClient viewer = createLimitedOperator("ofd2_viewer", false, false, true);
        OperatorRecord seen = viewer.getOperator(target);
        assertNull(seen.configuration(), "The configuration is only disclosed to the operator and to operators with operator permissions");
        assertFalse(seen.isConfigurationVisible());
        assertNull(createAnonymousClient().getOperator(target).configuration());

        assertTrue(client.getOperator(target).isAutoAssigned());
    }

    @Test
    void testRootConfigurationCannotBeModified() {
        String rootUuid = client.getSelf().uuid();
        expectRequestFailure(() -> client.setBot(rootUuid, true), 403);
    }

    @Test
    void testOperatorRecordDeserializesPermissionsAndConfiguration() {
        OperatorRecord operator = Json.readValue("""
            {
              "uuid": "01a15854-9ea9-73ca-bcf3-b8ad74f9bb40",
              "name": "bot_operator",
              "disabled": false,
              "permissions": {"client": true, "management": true, "operator": false},
              "configuration": {"auto_assign_reports": false, "auto_assign_automated_reports": true, "is_bot": true},
              "created": 1760000000,
              "updated": 1760000001
            }
            """, OperatorRecord.class);

        assertEquals(new OperatorPermissions(true, true, false), operator.permissions());
        assertTrue(operator.hasClientPermissions());
        assertTrue(operator.hasManagementPermissions());
        assertFalse(operator.hasOperatorPermissions());

        assertTrue(operator.isConfigurationVisible());
        assertFalse(operator.isAutoAssignReports());
        assertTrue(operator.isAutoAssignAutomatedReports());
        assertTrue(operator.isAutoAssigned());
        assertTrue(operator.isBot());
    }

    @Test
    void testOperatorRecordWithUndisclosedConfiguration() {
        OperatorRecord operator = Json.readValue("""
            {"uuid": "u", "name": "n", "disabled": false,
             "permissions": {"client": true, "management": false, "operator": false},
             "configuration": null, "created": 0, "updated": 0}
            """, OperatorRecord.class);

        assertNull(operator.configuration());
        assertFalse(operator.isConfigurationVisible());
        assertFalse(operator.isAutoAssigned());
        assertFalse(operator.isBot());

        // A record without permissions holds none
        OperatorRecord withoutPermissions = Json.readValue("{\"uuid\": \"u\", \"name\": \"n\"}", OperatorRecord.class);
        assertEquals(new OperatorPermissions(false, false, false), withoutPermissions.permissions());
    }

    @Test
    void testUpdateOperatorArgumentsAreValidatedClientSide() {
        assertThrows(IllegalArgumentException.class, () -> client.updateOperatorPermissions("uuid", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> client.updateOperatorPermissions("", true, null, null));
        assertThrows(IllegalArgumentException.class, () -> client.updateOperatorConfiguration("uuid", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> client.updateOperatorConfiguration("", true, null, null));
        assertThrows(IllegalArgumentException.class, () -> client.setBot("", true));
    }
}
