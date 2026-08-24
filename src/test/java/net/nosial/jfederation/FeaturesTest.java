package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.BlacklistRecord;
import net.nosial.jfederation.records.EvidenceRecord;
import net.nosial.jfederation.records.OperatorRecord;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FeaturesTest extends FederationClientTestBase {

    @Test
    void testOperatorAccessTokenRefresh() {
        OperatorCreated operatorUuidCreated = client.createOperator("access-token-refresh-test");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        String originalAccessToken = operatorUuidCreated.accessToken();
        assertNotNull(originalAccessToken);
        assertFalse(originalAccessToken.isEmpty());

        FederationClient testClient = new FederationClient(serverEndpoint, originalAccessToken);
        OperatorRecord selfOperator = testClient.getSelf();
        assertEquals(operatorUuid, selfOperator.uuid());

        String newAccessToken = client.generateOperatorAccessToken(operatorUuid);
        assertNotNull(newAccessToken);
        assertNotEquals(originalAccessToken, newAccessToken);

        FederationClient newTestClient = new FederationClient(serverEndpoint, newAccessToken);
        OperatorRecord newSelfOperator = newTestClient.getSelf();
        assertEquals(operatorUuid, newSelfOperator.uuid());

        try {
            testClient.getSelf();
            fail("Expected FederationClientException for revoked Access Token");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 401 || e.getStatusCode() == 403,
                "Expected 401/403 for revoked Access Token, got " + e.getStatusCode());
        }

        OperatorRecord updatedOperator = client.getOperator(operatorUuid);
        assertNull(updatedOperator.accessToken(), "Access token should be redacted in OperatorRecord");

        testClient.close();
        newTestClient.close();
    }

    @Test
    void testSelfAccessTokenRefresh() {
        assertThrows(FederationClientException.class, () -> client.generateAccessToken(false));
    }

    @Test
    void testEvidenceConfidentialityToggle() {
        String entityUuid = client.pushEntity("confidentiality-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "confidentiality_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Confidential test evidence", "Confidentiality test", "confidential");
        createdEvidenceRecords.add(evidenceUuid);

        EvidenceRecord record = client.getEvidenceRecord(evidenceUuid);
        assertFalse(record.confidential());

        client.updateEvidenceConfidentiality(evidenceUuid, true);
        EvidenceRecord confidentialRecord = client.getEvidenceRecord(evidenceUuid);
        assertTrue(confidentialRecord.confidential());

        client.updateEvidenceConfidentiality(evidenceUuid, false);
        EvidenceRecord nonConfidentialRecord = client.getEvidenceRecord(evidenceUuid);
        assertFalse(nonConfidentialRecord.confidential());

        assertEquals(record.textContent(), nonConfidentialRecord.textContent());
        assertEquals(record.note(), nonConfidentialRecord.note());
        assertEquals(record.tag(), nonConfidentialRecord.tag());
    }

    @Test
    void testConfidentialEvidenceAccessRestrictions() {
        String entityUuid = client.pushEntity("confidential-access-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "confidential_access_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Top secret evidence", "Confidential note", "secret", true);
        createdEvidenceRecords.add(evidenceUuid);

        assertTrue(client.getEvidenceRecord(evidenceUuid).confidential());

        FederationClient anon = createAnonymousClient();
        try {
            anon.getEvidenceRecord(evidenceUuid);
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        anon.close();
    }

    @Test
    void testEntityEvidenceRelationshipIntegrity() {
        String entityUuid = client.pushEntity("relationship-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "relationship_user");
        createdEntities.add(entityUuid);

        String[] evidenceUuids = new String[3];
        for (int i = 0; i < 3; i++) {
            evidenceUuids[i] = client.submitEvidence(entityUuid, "Evidence " + i, "Note " + i, "tag_" + i);
            createdEvidenceRecords.add(evidenceUuids[i]);
        }

        List<EvidenceRecord> entityEvidence = client.listEntityEvidenceRecords(entityUuid);
        assertNotNull(entityEvidence);
        assertTrue(entityEvidence.size() >= 3);

        for (String created : evidenceUuids) {
            boolean found = entityEvidence.stream().anyMatch(e -> e.uuid().equals(created));
            assertTrue(found, "Created evidence should be found in entity evidence list");
        }
    }

    @Test
    void testEntityBlacklistRelationshipIntegrity() {
        String entityUuid = client.pushEntity("blacklist-relationship-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "blacklist_relationship_user");
        createdEntities.add(entityUuid);

        String[] blacklistUuids = new String[2];
        for (int i = 0; i < 2; i++) {
            String reportUuid = createReportForEntity(entityUuid);
            IncidentType type = (i == 0) ? IncidentType.SPAM : IncidentType.MALWARE;
            int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
            blacklistUuids[i] = client.blacklistEntity(entityUuid, reportUuid, type, expires);
            createdBlacklistRecords.add(blacklistUuids[i]);
        }

        List<BlacklistRecord> entityBlacklists = client.listEntityBlacklistRecords(entityUuid);
        assertNotNull(entityBlacklists);
        assertTrue(entityBlacklists.size() >= 2);

        for (String created : blacklistUuids) {
            boolean found = entityBlacklists.stream().anyMatch(b -> b.uuid().equals(created));
            assertTrue(found, "Created blacklist should be found in entity blacklist list");
        }
    }

    @Test
    void testEvidenceWithVariousContentTypes() {
        String entityUuid = client.pushEntity("content-types-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "content_types_user");
        createdEntities.add(entityUuid);

        String[][] evidenceTypes = {
            {"Simple plain text evidence", "Plain text note", "plain"},
            {"{\"type\":\"json\",\"data\":{\"key\":\"value\"}}", "JSON data", "json"},
            {"Multi-line\nevidence\nwith\nnewlines", "Multi-line note", "multiline"},
            {"Evidence with special chars: ñ 中文 🚀", "Unicode test", "unicode"},
        };

        for (String[] data : evidenceTypes) {
            String evidenceUuid = client.submitEvidence(entityUuid, data[0], data[1], data[2]);
            createdEvidenceRecords.add(evidenceUuid);

            EvidenceRecord rec = client.getEvidenceRecord(evidenceUuid);
            assertNotNull(rec);
            assertEquals(data[0], rec.textContent());
            assertEquals(data[1], rec.note());
            assertEquals(data[2], rec.tag());
        }
    }

    @Test
    void testConcurrentClientOperations() {
        OperatorCreated operatorUuidCreated = client.createOperator("concurrent-test-operator");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setClientPermissions(operatorUuid, true);
        client.setManagementPermissions(operatorUuid, true);

        FederationClient concurrentClient = new FederationClient(serverEndpoint, operatorUuidCreated.accessToken());

        String entityUuid = client.pushEntity("concurrent-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com", "concurrent_user");
        createdEntities.add(entityUuid);

        String evidenceUuid1 = client.submitEvidence(entityUuid, "Evidence from main client", "Main client", "main");
        createdEvidenceRecords.add(evidenceUuid1);

        String evidenceUuid2 = concurrentClient.submitEvidence(entityUuid, "Evidence from concurrent client", "Concurrent client", "concurrent");
        createdEvidenceRecords.add(evidenceUuid2);

        EvidenceRecord ev1 = client.getEvidenceRecord(evidenceUuid1);
        EvidenceRecord ev2 = client.getEvidenceRecord(evidenceUuid2);

        assertNotEquals(evidenceUuid1, evidenceUuid2);
        assertEquals("Evidence from main client", ev1.textContent());
        assertEquals("Evidence from concurrent client", ev2.textContent());
        assertEquals(entityUuid, ev1.entityUuid());
        assertEquals(entityUuid, ev2.entityUuid());

        concurrentClient.close();
    }
}
