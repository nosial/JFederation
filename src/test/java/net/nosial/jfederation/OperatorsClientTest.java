package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.OperatorRecord;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OperatorsClientTest extends FederationClientTestBase {

    @Test
    void testCreateOperatorNoPermissions() {
        String name = "test_operator_" + UUID.randomUUID().toString().substring(0, 8);
        OperatorCreated uuidCreated = client.createOperator(name);
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);
        assertNotNull(uuid);

        OperatorRecord rec = client.getOperator(uuid);
        assertNotNull(rec);
        assertFalse(rec.managementPermissions());
        assertFalse(rec.operatorPermissions());
        assertFalse(rec.clientPermissions());
        assertNotNull(uuidCreated.accessToken());
        assertNull(rec.accessToken(), "Access token should be redacted in OperatorRecord");
    }

    @Test
    void testCreateOperatorWithManagementPermission() {
        String uuid = client.createOperator("mgmt_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);
        client.setManagementPermissions(uuid, true);

        OperatorRecord rec = client.getOperator(uuid);
        assertTrue(rec.managementPermissions());
        assertFalse(rec.operatorPermissions());
        assertFalse(rec.clientPermissions());
    }

    @Test
    void testCreateOperatorWithOperatorPermission() {
        String uuid = client.createOperator("op_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);
        client.setOperatorPermissions(uuid, true);

        OperatorRecord rec = client.getOperator(uuid);
        assertFalse(rec.managementPermissions());
        assertTrue(rec.operatorPermissions());
        assertFalse(rec.clientPermissions());
    }

    @Test
    void testCreateOperatorWithClientPermission() {
        String uuid = client.createOperator("client_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);
        client.setClientPermissions(uuid, true);

        OperatorRecord rec = client.getOperator(uuid);
        assertFalse(rec.managementPermissions());
        assertFalse(rec.operatorPermissions());
        assertTrue(rec.clientPermissions());
    }

    @Test
    void testCreateOperatorWithAllPermissions() {
        String uuid = client.createOperator("all_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);
        client.setManagementPermissions(uuid, true);
        client.setOperatorPermissions(uuid, true);
        client.setClientPermissions(uuid, true);

        OperatorRecord rec = client.getOperator(uuid);
        assertTrue(rec.managementPermissions());
        assertTrue(rec.operatorPermissions());
        assertTrue(rec.clientPermissions());
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

        FederationClient opClient = new FederationClient(serverEndpoint, uuidCreated.accessToken());
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

        FederationClient opClient = new FederationClient(serverEndpoint, uuidCreated.accessToken());
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
        assertFalse(op.managementPermissions());
        assertFalse(op.operatorPermissions());
        assertFalse(op.clientPermissions());
        assertFalse(op.disabled());
        assertNotNull(uuidCreated.accessToken());
        assertNull(op.accessToken(), "Access token should be redacted in OperatorRecord");

        client.setManagementPermissions(uuid, true);
        client.setOperatorPermissions(uuid, true);
        client.setClientPermissions(uuid, true);
        OperatorRecord updated = client.getOperator(uuid);
        assertTrue(updated.managementPermissions());
        assertTrue(updated.operatorPermissions());
        assertTrue(updated.clientPermissions());

        client.disableOperator(uuid);
        assertTrue(client.getOperator(uuid).disabled());
        client.enableOperator(uuid);
        assertFalse(client.getOperator(uuid).disabled());

        String origToken = uuidCreated.accessToken();
        String newToken = client.generateOperatorAccessToken(uuid);
        assertNotEquals(origToken, newToken);

        FederationClient newTokenClient = new FederationClient(serverEndpoint, newToken);
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

        FederationClient opClient = new FederationClient(serverEndpoint, uuidCreated.accessToken());

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

        FederationClient opClient = new FederationClient(serverEndpoint, uuidCreated.accessToken());

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

        FederationClient opClient = new FederationClient(serverEndpoint, uuidCreated.accessToken());

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

        FederationClient opClient = new FederationClient(serverEndpoint, uuidCreated.accessToken());

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
        assertTrue(client.getOperator(uuid).managementPermissions());
        client.setManagementPermissions(uuid, false);
        assertFalse(client.getOperator(uuid).managementPermissions());

        client.setOperatorPermissions(uuid, true);
        assertTrue(client.getOperator(uuid).operatorPermissions());
        client.setOperatorPermissions(uuid, false);
        assertFalse(client.getOperator(uuid).operatorPermissions());

        client.setClientPermissions(uuid, true);
        assertTrue(client.getOperator(uuid).clientPermissions());
        client.setClientPermissions(uuid, false);
        assertFalse(client.getOperator(uuid).clientPermissions());
    }

    @Test
    void testOperatorAccessTokenIntegrity() {
        OperatorCreated uuidCreated = client.createOperator("token_test_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);

        String origToken = uuidCreated.accessToken();
        assertNotNull(origToken);

        FederationClient opClient = new FederationClient(serverEndpoint, origToken);
        assertEquals(uuid, opClient.getSelf().uuid());
        opClient.close();

        String newToken = client.generateOperatorAccessToken(uuid);
        assertNotEquals(origToken, newToken);

        try {
            FederationClient oldClient = new FederationClient(serverEndpoint, origToken);
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

        expectRequestFailure(() -> anon.getSelf(), new int[]{401, 403});
        expectRequestFailure(() -> { anon.createOperator("unauthorized"); }, new int[]{401, 403});
        expectRequestFailure(() -> anon.disableOperator(fakeUuid), new int[]{401, 403});
        expectRequestFailure(() -> anon.deleteOperator(fakeUuid), new int[]{401, 403});
        expectRequestFailure(() -> anon.setOperatorPermissions(fakeUuid, true), new int[]{401, 403});
        expectRequestFailure(() -> anon.setManagementPermissions(fakeUuid, true), new int[]{401, 403});
        expectRequestFailure(() -> anon.setClientPermissions(fakeUuid, true), new int[]{401, 403});
        expectRequestFailure(() -> anon.generateOperatorAccessToken(fakeUuid), new int[]{401, 403});
        expectRequestFailure(() -> anon.listOperators(1, 10), new int[]{401, 403});
        anon.close();
    }

    @Test
    void testMasterAccessTokenResolvesToRootOperator() {
        FederationClient master = new FederationClient(serverEndpoint, serverAccessToken);
        OperatorRecord root = master.getSelf();
        assertEquals("root", root.name());
        assertTrue(root.managementPermissions());
        assertTrue(root.operatorPermissions());
        assertTrue(root.clientPermissions());
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

        FederationClient opClient = new FederationClient(serverEndpoint, uuidCreated.accessToken());
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
        String evidenceUuid = createSecurityEvidence(entityUuid);
        String blacklistUuid = createSecurityBlacklist(entityUuid);

        expectRequestFailure(() -> { clientOnly.createOperator("child"); }, 403);
        expectRequestFailure(() -> clientOnly.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+3600)), 403);
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
        expectRequestFailure(() -> attacker.generateOperatorAccessToken(root.uuid()), 403);
        attacker.close();
    }

    @Test
    void testSecurityMalformedAccessTokensAreRejected() {
        FederationClient shortClient = new FederationClient(serverEndpoint, "short");
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
        assertNull(client.getOperator(targetUuid).accessToken());
    }

    @Test
    void testPermissionChangeIsEffectiveAcrossMultipleClients() {
        OperatorCreated uuidCreated = client.createOperator("cross_client_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);
        client.setClientPermissions(uuid, true);
        String token = uuidCreated.accessToken();

        FederationClient clientA = new FederationClient(serverEndpoint, token);
        FederationClient clientB = new FederationClient(serverEndpoint, token);

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
}
