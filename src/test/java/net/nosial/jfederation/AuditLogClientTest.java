package net.nosial.jfederation;

import net.nosial.jfederation.enums.AuditLogType;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.AuditLog;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogClientTest extends FederationClientTestBase {

    private void generateSampleAuditLogs() {
        OperatorCreated operatorUuidCreated = client.createOperator("sample-audit-operator");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient operatorClient = new FederationClient(serverEndpoint, token);

        String entityUuid = operatorClient.pushEntity("sample-audit.com", "sample_user");
        createdEntities.add(entityUuid);
    }

    @Test
    void testListAuditLogs() {
        generateSampleAuditLogs();

        List<AuditLog> auditLogs = client.listAuditLogs(1, 10);
        assertNotNull(auditLogs);
        assertFalse(auditLogs.isEmpty());

        for (AuditLog log : auditLogs) {
            assertNotNull(log.uuid());
            assertNotNull(log.type());
            assertNotNull(log.message());
            assertTrue(log.timestamp() > 0);
        }
    }

    @Test
    void testListAuditLogsWithPagination() {
        generateSampleAuditLogs();

        List<AuditLog> page1 = client.listAuditLogs(1, 3);
        List<AuditLog> page2 = client.listAuditLogs(2, 3);

        assertNotNull(page1);
        assertNotNull(page2);
        assertTrue(page1.size() <= 3);
        assertTrue(page2.size() <= 3);

        if (!page1.isEmpty() && !page2.isEmpty()) {
            List<String> page1Uuids = page1.stream().map(AuditLog::uuid).toList();
            List<String> page2Uuids = page2.stream().map(AuditLog::uuid).toList();
            for (String uuid : page1Uuids) {
                assertFalse(page2Uuids.contains(uuid), "Pages should contain different records");
            }
        }
    }

    @Test
    void testListOperatorAuditLogs() {
        OperatorCreated operatorUuidCreated = client.createOperator("operator-audit-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient operatorClient = new FederationClient(serverEndpoint, token);

        String entityUuid = operatorClient.pushEntity("operator-audit-test.com", "audit_user");
        createdEntities.add(entityUuid);

        List<AuditLog> operatorLogs = client.listOperatorAuditLogs(operatorUuid, 1, 10);
        assertNotNull(operatorLogs);
        assertFalse(operatorLogs.isEmpty());

        for (AuditLog log : operatorLogs) {
            assertEquals(operatorUuid, log.operatorUuid());
        }
    }

    @Test
    void testListOperatorAuditLogsWithPagination() {
        OperatorCreated operatorUuidCreated = client.createOperator("paginated-audit-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);
        client.setManagementPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient operatorClient = new FederationClient(serverEndpoint, token);

        for (int i = 1; i <= 3; i++) {
            String entityUuid = operatorClient.pushEntity("paginated-audit-" + i + ".com", "user_" + i);
            createdEntities.add(entityUuid);
            String evidenceUuid = operatorClient.submitEvidence(entityUuid, "Evidence " + i, "Note " + i, "tag_" + i, false);
            createdEvidenceRecords.add(evidenceUuid);
        }

        List<AuditLog> page1 = client.listOperatorAuditLogs(operatorUuid, 1, 3);
        List<AuditLog> page2 = client.listOperatorAuditLogs(operatorUuid, 2, 3);

        assertNotNull(page1);
        assertNotNull(page2);
        assertTrue(page1.size() <= 3);

        for (AuditLog log : page1) {
            assertEquals(operatorUuid, log.operatorUuid());
        }
        for (AuditLog log : page2) {
            assertEquals(operatorUuid, log.operatorUuid());
        }
    }

    @Test
    void testListAuditLogsInvalidPage() {
        assertThrows(IllegalArgumentException.class, () -> client.listAuditLogs(-1, 10));
    }

    @Test
    void testListAuditLogsInvalidLimit() {
        assertThrows(IllegalArgumentException.class, () -> client.listAuditLogs(1, -1));
    }

    @Test
    void testGetAuditLogRecordInvalidUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.getAuditLogRecord(""));
    }

    @Test
    void testGetNonExistentAuditLogRecord() {
        String fakeUuid = "0198f41f-45c7-78eb-a2a7-86de4e99991a";
        assertThrows(FederationClientException.class, () -> client.getAuditLogRecord(fakeUuid));
    }

    @Test
    void testListOperatorAuditLogsInvalidOperatorUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.listOperatorAuditLogs("", 1, 10));
    }

    @Test
    void testListOperatorAuditLogsNonExistentOperator() {
        String fakeUuid = "0198f41f-45c7-78eb-a2a7-86de4e99991a";
        assertThrows(FederationClientException.class, () -> client.listOperatorAuditLogs(fakeUuid, 1, 10));
    }

    @Test
    void testListOperatorAuditLogsInvalidPage() {
        OperatorCreated operatorUuidCreated = client.createOperator("invalid-page-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        assertThrows(IllegalArgumentException.class, () -> client.listOperatorAuditLogs(operatorUuid, -1, 10));
    }

    @Test
    void testListOperatorAuditLogsInvalidLimit() {
        OperatorCreated operatorUuidCreated = client.createOperator("invalid-limit-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        assertThrows(IllegalArgumentException.class, () -> client.listOperatorAuditLogs(operatorUuid, 1, -1));
    }

    @Test
    void testAuditLogAccessLimitedOperator() {
        OperatorCreated operatorUuidCreated = client.createOperator("limited-audit-access");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient limitedClient = new FederationClient(serverEndpoint, token);

        List<AuditLog> logs = limitedClient.listOperatorAuditLogs(operatorUuid, 1, 10);
        assertNotNull(logs);
    }

    @Test
    void testAuditLogContentForEntityOperations() {
        OperatorCreated operatorUuidCreated = client.createOperator("entity-audit-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient operatorClient = new FederationClient(serverEndpoint, token);

        int initialLogCount = client.listOperatorAuditLogs(operatorUuid, 1, 100).size();

        String entityUuid = operatorClient.pushEntity("audit-entity-test.com", "audit_entity_user");
        createdEntities.add(entityUuid);

        List<AuditLog> operatorLogs = client.listOperatorAuditLogs(operatorUuid, 1, 100);
        int newLogCount = operatorLogs.size();

        assertTrue(newLogCount > initialLogCount);

        boolean foundEntityCreation = false;
        for (AuditLog log : operatorLogs) {
            if (entityUuid.equals(log.entityUuid()) && log.type() == AuditLogType.ENTITY_PUSHED) {
                foundEntityCreation = true;
                break;
            }
        }

        assertTrue(foundEntityCreation, "Should find entity creation audit log");
    }

    @Test
    void testAuditLogContentForBlacklistOperations() {
        OperatorCreated operatorUuidCreated = client.createOperator("blacklist-audit-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);
        client.setManagementPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient operatorClient = new FederationClient(serverEndpoint, token);

        String entityUuid = operatorClient.pushEntity("blacklist-audit-test.com", "blacklist_audit_user");
        createdEntities.add(entityUuid);

        String reportUuid = createReportForEntity(entityUuid, operatorClient);

        int initialLogCount = client.listOperatorAuditLogs(operatorUuid, 1, 100).size();

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = operatorClient.blacklistEntity(entityUuid, reportUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        operatorClient.liftBlacklistRecord(blacklistUuid);

        List<AuditLog> operatorLogs = client.listOperatorAuditLogs(operatorUuid, 1, 100);
        int newLogCount = operatorLogs.size();

        assertTrue(newLogCount > initialLogCount);

        boolean foundBlacklistCreation = false;
        boolean foundBlacklistLift = false;

        for (AuditLog log : operatorLogs) {
            String message = log.message() != null ? log.message().toLowerCase() : "";
            if (message.contains("blacklist") && message.contains("created")) {
                foundBlacklistCreation = true;
            }
            if (message.contains("blacklist") && (message.contains("lifted") || message.contains("removed"))) {
                foundBlacklistLift = true;
            }
        }

        assertTrue(foundBlacklistCreation, "Should find blacklist creation audit log");
        assertTrue(foundBlacklistLift, "Should find blacklist lift audit log");
    }

    @Test
    void testAuditLogConsistencyOverTime() throws InterruptedException {
        OperatorCreated operatorUuidCreated = client.createOperator("consistency-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        List<AuditLog> immediateLogs = client.listAuditLogs(1, 10);

        Thread.sleep(1000);
        List<AuditLog> delayedLogs = client.listAuditLogs(1, 10);

        assertEquals(immediateLogs.size(), delayedLogs.size());

        for (int i = 0; i < Math.min(immediateLogs.size(), delayedLogs.size()); i++) {
            assertEquals(immediateLogs.get(i).uuid(), delayedLogs.get(i).uuid());
            assertEquals(immediateLogs.get(i).message(), delayedLogs.get(i).message());
        }
    }

    @Test
    void testHighVolumeAuditLogRetrieval() {
        for (int i = 1; i <= 5; i++) {
            OperatorCreated operatorUuidCreated = client.createOperator("high-volume-test-" + i);
        String operatorUuid = operatorUuidCreated.uuid();
            createdOperators.add(operatorUuid);
        }

        List<AuditLog> auditLogs = client.listAuditLogs(1, 100);
        assertNotNull(auditLogs);
        assertTrue(auditLogs.size() >= 5);

        for (AuditLog log : auditLogs) {
            assertNotNull(log.uuid());
            assertNotNull(log.type());
            assertNotNull(log.message());
            assertTrue(log.timestamp() > 0);
        }
    }

    @Test
    void testSecurityUnauthenticatedAuditLogAccess() {
        FederationClient anonymousClient = createAnonymousClient();

        List<AuditLog> logs = anonymousClient.listAuditLogs(1, 10);
        assertNotNull(logs);

        assertThrows(FederationClientException.class,
            () -> anonymousClient.getAuditLogRecord("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void testAuditLogRecordsOperatorActorForEntityCreation() {
        OperatorCreated operatorUuidCreated = client.createOperator("actor-entity-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient operatorClient = new FederationClient(serverEndpoint, token);

        int beforeCount = client.listOperatorAuditLogs(operatorUuid, 1, 100).size();

        String entityUuid = operatorClient.pushEntity("actor-entity.com", "actor_user");
        createdEntities.add(entityUuid);

        List<AuditLog> afterLogs = client.listOperatorAuditLogs(operatorUuid, 1, 100);
        assertTrue(afterLogs.size() > beforeCount);

        boolean found = false;
        for (AuditLog log : afterLogs) {
            if (operatorUuid.equals(log.operatorUuid()) && entityUuid.equals(log.entityUuid())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Audit log should record the acting operator and affected entity");
    }

    @Test
    void testAuditLogRecordsEvidenceAndBlacklistActor() {
        OperatorCreated operatorUuidCreated = client.createOperator("actor-evidence-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);
        client.setManagementPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient operatorClient = new FederationClient(serverEndpoint, token);

        String entityUuid = operatorClient.pushEntity("actor-evidence.com", "actor_evidence_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = operatorClient.submitEvidence(entityUuid, "Actor evidence", "Note", "actor", false);
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String reportUuid = createReportForEntity(entityUuid, operatorClient);
        String blacklistUuid = operatorClient.blacklistEntity(entityUuid, reportUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        List<AuditLog> logs = client.listOperatorAuditLogs(operatorUuid, 1, 100);
        boolean foundEvidence = false;
        boolean foundBlacklist = false;

        for (AuditLog log : logs) {
            if (evidenceUuid.equals(log.evidenceUuid()) && operatorUuid.equals(log.operatorUuid())) {
                foundEvidence = true;
            }
            if (blacklistUuid.equals(log.blacklistUuid()) && operatorUuid.equals(log.operatorUuid())) {
                foundBlacklist = true;
            }
        }

        assertTrue(foundEvidence, "Audit log should record evidence submitted by operator");
        assertTrue(foundBlacklist, "Audit log should record blacklist created by operator");
    }

    @Test
    void testAuditLogFiltersByType() {
        OperatorCreated operatorUuidCreated = client.createOperator("type-filter-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient operatorClient = new FederationClient(serverEndpoint, token);

        String entityUuid = operatorClient.pushEntity("type-filter.com", "type_filter_user");
        createdEntities.add(entityUuid);

        List<AuditLog> allLogs = client.listAuditLogs(1, 100);
        boolean foundEntityPush = allLogs.stream()
            .anyMatch(log -> log.type() == AuditLogType.ENTITY_PUSHED && entityUuid.equals(log.entityUuid()));
        assertTrue(foundEntityPush);
    }

    @Test
    void testAuditLogEntryRetrievableByUuid() {
        OperatorCreated operatorUuidCreated = client.createOperator("uuid-audit-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);

        String token = operatorUuidCreated.accessToken();
        FederationClient operatorClient = new FederationClient(serverEndpoint, token);

        String entityUuid = operatorClient.pushEntity("uuid-audit.com", "uuid_audit_user");
        createdEntities.add(entityUuid);

        List<AuditLog> logs = client.listOperatorAuditLogs(operatorUuid, 1, 100);
        assertFalse(logs.isEmpty());

        AuditLog firstLog = logs.get(0);
        AuditLog retrieved = client.getAuditLogRecord(firstLog.uuid());
        assertEquals(firstLog.uuid(), retrieved.uuid());
        assertEquals(firstLog.message(), retrieved.message());
        assertEquals(firstLog.type(), retrieved.type());
    }

    @Test
    void testAuditLogRemainsAfterEntityDeletion() {
        String entityUuid = client.pushEntity("audit-survive.com", "audit_survive_user");
        createdEntities.add(entityUuid);

        List<AuditLog> logsBefore = client.listEntityAuditLogs(entityUuid, 1, 10);
        assertFalse(logsBefore.isEmpty());

        client.deleteEntity(entityUuid);
        removeFromCleanup(createdEntities, entityUuid);

        for (AuditLog log : logsBefore) {
            AuditLog retrieved = client.getAuditLogRecord(log.uuid());
            assertNotNull(retrieved);
            if (retrieved.entityUuid() != null) {
                assertEquals(entityUuid, retrieved.entityUuid());
            }
        }
    }

    @Test
    void testSecurityOperatorAuditLogsAreIsolated() {
        FederationClient manager = createLimitedOperator("audit_manager", false, true, true);
        FederationClient snooper = createLimitedOperator("audit_snooper", false, false, true);

        String snooperUuid = snooper.getSelf().uuid();
        OperatorCreated targetUuidCreated = client.createOperator("audit_target");
        String targetUuid = targetUuidCreated.uuid();
        createdOperators.add(targetUuid);

        expectRequestFailure(
            () -> snooper.listOperatorAuditLogs(targetUuid, 1, 10),
            new int[]{400, 403},
            "Client-only operator should not be able to list another operator's audit logs"
        );
    }
}
