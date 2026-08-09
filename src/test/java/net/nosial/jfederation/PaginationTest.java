package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.*;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PaginationTest extends FederationClientTestBase {

    @Test
    void testEntitiesPaginationBasic() {
        int entityCount = 15;
        List<String> entityUuids = new ArrayList<>();

        for (int i = 0; i < entityCount; i++) {
            String entityUuid = client.pushEntity("pagination-test-" + i + "-" + randomUuid().substring(0, 4) + ".com", "pagination_user_" + i);
            createdEntities.add(entityUuid);
            entityUuids.add(entityUuid);
        }

        int pageSize = 5;
        List<String> allRetrievedUuids = new ArrayList<>();
        int page = 1;

        while (true) {
            List<EntityRecord> entitiesPage = client.listEntities(page, pageSize);
            assertNotNull(entitiesPage);
            assertTrue(entitiesPage.size() <= pageSize);

            for (EntityRecord entity : entitiesPage) {
                allRetrievedUuids.add(entity.uuid());
            }

            if (entitiesPage.size() < pageSize) break;
            page++;
            if (page > 10) break;
        }

        for (String uuid : entityUuids) {
            assertTrue(allRetrievedUuids.contains(uuid), "Entity " + uuid + " not found in paginated results");
        }
    }

    @Test
    void testEntitiesPaginationEdgeCases() {
        List<EntityRecord> entities = client.listEntities(1, 1);
        assertNotNull(entities);
        assertTrue(entities.size() <= 1);

        entities = client.listEntities(1, 100);
        assertNotNull(entities);
        assertTrue(entities.size() <= 100);

        try {
            client.listEntities(-1, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testEntitiesPaginationInvalidLimits() {
        try {
            client.listEntities(1, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testEntitiesPaginationConsistency() {
        int entityCount = 8;
        for (int i = 0; i < entityCount; i++) {
            String entityUuid = client.pushEntity("consistency-test-" + i + "-" + randomUuid().substring(0, 4) + ".com", "consistency_user_" + i);
            createdEntities.add(entityUuid);
        }

        List<EntityRecord> page1First = client.listEntities(1, 3);
        List<EntityRecord> page1Second = client.listEntities(1, 3);

        assertEquals(page1First.size(), page1Second.size());

        for (int i = 0; i < page1First.size(); i++) {
            assertEquals(page1First.get(i).uuid(), page1Second.get(i).uuid());
        }
    }

    @Test
    void testOperatorsPaginationBasic() {
        int operatorCount = 10;
        List<String> operatorUuids = new ArrayList<>();

        for (int i = 0; i < operatorCount; i++) {
            OperatorCreated operatorUuidCreated = client.createOperator("pagination_operator_" + i);
        String operatorUuid = operatorUuidCreated.uuid();
            createdOperators.add(operatorUuid);
            operatorUuids.add(operatorUuid);
        }

        int pageSize = 100;
        List<String> allRetrievedUuids = new ArrayList<>();
        int page = 1;

        while (true) {
            List<OperatorRecord> operatorsPage = client.listOperators(page, pageSize);
            assertNotNull(operatorsPage);
            assertTrue(operatorsPage.size() <= pageSize);

            for (OperatorRecord operator : operatorsPage) {
                allRetrievedUuids.add(operator.uuid());
            }

            if (operatorsPage.size() < pageSize) break;
            page++;
            if (page > 10) break;
        }

        for (String uuid : operatorUuids) {
            assertTrue(allRetrievedUuids.contains(uuid), "Operator " + uuid + " not found in paginated results");
        }
    }

    @Test
    void testOperatorsPaginationInvalidParameters() {
        try {
            client.listOperators(-1, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testEvidencePaginationBasic() {
        String entityUuid = client.pushEntity("evidence-pagination-" + randomUuid().substring(0, 8) + ".com", "evidence_user");
        createdEntities.add(entityUuid);

        int evidenceCount = 12;
        List<String> evidenceUuids = new ArrayList<>();

        for (int i = 0; i < evidenceCount; i++) {
            String evidenceUuid = client.submitEvidence(entityUuid, "Evidence content " + i, "Evidence note " + i, "pagination_tag_" + i);
            createdEvidenceRecords.add(evidenceUuid);
            evidenceUuids.add(evidenceUuid);
        }

        int pageSize = 5;
        List<String> allRetrievedUuids = new ArrayList<>();
        int page = 1;

        while (true) {
            List<EvidenceRecord> evidencePage = client.listEvidence(page, pageSize, true);
            assertNotNull(evidencePage);
            assertTrue(evidencePage.size() <= pageSize);

            for (EvidenceRecord evidence : evidencePage) {
                allRetrievedUuids.add(evidence.uuid());
            }

            if (evidencePage.size() < pageSize) break;
            page++;
            if (page > 10) break;
        }

        for (String uuid : evidenceUuids) {
            assertTrue(allRetrievedUuids.contains(uuid), "Evidence " + uuid + " not found in paginated results");
        }
    }

    @Test
    void testEntityEvidencePaginationBasic() {
        String entityUuid = client.pushEntity("entity-evidence-pagination-" + randomUuid().substring(0, 8) + ".com", "entity_evidence_user");
        createdEntities.add(entityUuid);

        int evidenceCount = 8;
        List<String> evidenceUuids = new ArrayList<>();

        for (int i = 0; i < evidenceCount; i++) {
            String evidenceUuid = client.submitEvidence(entityUuid, "Entity evidence content " + i, "Entity evidence note " + i, "entity_pagination_tag_" + i);
            createdEvidenceRecords.add(evidenceUuid);
            evidenceUuids.add(evidenceUuid);
        }

        int pageSize = 3;
        List<String> allRetrievedUuids = new ArrayList<>();
        int page = 1;

        while (true) {
            List<EvidenceRecord> evidencePage = client.listEntityEvidenceRecords(entityUuid, page, pageSize, false);
            assertNotNull(evidencePage);
            assertTrue(evidencePage.size() <= pageSize);

            for (EvidenceRecord evidence : evidencePage) {
                assertEquals(entityUuid, evidence.entityUuid());
                allRetrievedUuids.add(evidence.uuid());
            }

            if (evidencePage.size() < pageSize) break;
            page++;
            if (page > 10) break;
        }

        assertEquals(evidenceCount, allRetrievedUuids.size());
        for (String uuid : evidenceUuids) {
            assertTrue(allRetrievedUuids.contains(uuid));
        }
    }

    @Test
    void testBlacklistPaginationBasic() {
        int blacklistCount = 10;
        List<String> blacklistUuids = new ArrayList<>();

        for (int i = 0; i < blacklistCount; i++) {
            String entityUuid = client.pushEntity("blacklist-pagination-" + i + "-" + randomUuid().substring(0, 4) + ".com", "blacklist_user_" + i);
            createdEntities.add(entityUuid);

            String evidenceUuid = client.submitEvidence(entityUuid, "Blacklist evidence " + i, "Blacklist note " + i, "blacklist_pagination");
            createdEvidenceRecords.add(evidenceUuid);

            String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int) (System.currentTimeMillis() / 1000 + 3600));
            createdBlacklistRecords.add(blacklistUuid);
            blacklistUuids.add(blacklistUuid);
        }

        int pageSize = 100;
        List<String> allRetrievedUuids = new ArrayList<>();
        int page = 1;

        while (true) {
            List<BlacklistRecord> blacklistPage = client.listBlacklistRecords(page, pageSize, true);
            assertNotNull(blacklistPage);
            assertTrue(blacklistPage.size() <= pageSize);

            for (BlacklistRecord blacklistRecord : blacklistPage) {
                allRetrievedUuids.add(blacklistRecord.uuid());
            }

            if (blacklistPage.size() < pageSize) break;
            page++;
            if (page > 10) break;
        }

        for (String uuid : blacklistUuids) {
            assertTrue(allRetrievedUuids.contains(uuid), "Blacklist record " + uuid + " not found in paginated results");
        }
    }

    @Test
    void testAuditLogPaginationBasic() {
        for (int i = 0; i < 5; i++) {
            OperatorCreated operatorUuidCreated = client.createOperator("audit_pagination_operator_" + i);
        String operatorUuid = operatorUuidCreated.uuid();
            createdOperators.add(operatorUuid);
            client.deleteOperator(operatorUuid);
            removeFromCleanup(createdOperators, operatorUuid);
        }

        int pageSize = 3;
        int page = 1;
        int totalAuditLogs = 0;

        while (true) {
            List<AuditLog> auditLogsPage = client.listAuditLogs(page, pageSize);
            assertNotNull(auditLogsPage);
            assertTrue(auditLogsPage.size() <= pageSize);

            for (AuditLog auditLog : auditLogsPage) {
                assertNotNull(auditLog.uuid());
                assertNotNull(auditLog.type());
                assertNotNull(auditLog.message());
                assertTrue(auditLog.timestamp() > 0, "timestamp should be > 0 but was " + auditLog.timestamp());
                totalAuditLogs++;
            }

            if (auditLogsPage.size() < pageSize) break;
            page++;
            if (page > 5) break;
        }

        assertTrue(totalAuditLogs > 0);
    }

    @Test
    void testPaginationEmptyResults() {
        List<EntityRecord> entities = client.listEntities(1000, 10);
        assertNotNull(entities);
        assertTrue(entities.isEmpty());
    }

    @Test
    void testPaginationLargePageSizes() {
        List<EntityRecord> entities = client.listEntities(1, 1000);
        assertNotNull(entities);
    }

    @Test
    void testPaginationOrderConsistency() {
        for (int i = 0; i < 10; i++) {
            String entityUuid = client.pushEntity("order-test-" + i + "-" + randomUuid().substring(0, 4) + ".com", String.format("order_user_%03d", i));
            createdEntities.add(entityUuid);
        }

        List<EntityRecord> page1 = client.listEntities(1, 5);
        List<EntityRecord> page2 = client.listEntities(2, 5);

        List<String> page1Uuids = page1.stream().map(EntityRecord::uuid).collect(Collectors.toList());
        List<String> page2Uuids = page2.stream().map(EntityRecord::uuid).collect(Collectors.toList());

        List<String> intersection = new ArrayList<>(page1Uuids);
        intersection.retainAll(page2Uuids);
        assertTrue(intersection.isEmpty(), "Entities appeared in multiple pages: " + intersection);
    }

    @Test
    void testSecurityExcessivePaginationLimitIsTolerated() {
        List<EntityRecord> entities = client.listEntities(1, 10000);
        assertNotNull(entities);

        List<OperatorRecord> operators = client.listOperators(1, 10000);
        assertNotNull(operators);

        List<EvidenceRecord> evidence = client.listEvidence(1, 10000, true);
        assertNotNull(evidence);

        List<BlacklistRecord> blacklist = client.listBlacklistRecords(1, 10000, true);
        assertNotNull(blacklist);
    }
}
