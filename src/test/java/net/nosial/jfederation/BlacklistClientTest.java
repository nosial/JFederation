package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.BlacklistRecord;
import net.nosial.jfederation.records.OperatorCreated;
import net.nosial.jfederation.records.OperatorRecord;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class BlacklistClientTest extends FederationClientTestBase {

    @Test
    void testBlacklistEntity() {
        String entityUuid = client.pushEntity("blacklist-test-" + randomUuid().substring(0, 8) + ".com", "john_test");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Subscribe to my free crypto exchange!", "Automated Spam Detection", "spam");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        assertNotNull(blacklistUuid);
        createdBlacklistRecords.add(blacklistUuid);

        BlacklistRecord rec = client.getBlacklistRecord(blacklistUuid);
        assertNotNull(rec);
        assertEquals(entityUuid, rec.entityUuid());
        assertEquals(evidenceUuid, rec.evidenceUuid());
        assertNotNull(rec.expires());
        assertEquals(expires, rec.expires().intValue());
        assertFalse(rec.lifted());
    }

    @Test
    void testBlacklistEntityPermanent() {
        String entityUuid = client.pushEntity("permanent-test-" + randomUuid().substring(0, 8) + ".org", "infected_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Detected malware distribution", "Automated Security Scan", "malware");
        createdEvidenceRecords.add(evidenceUuid);

        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.MALWARE, null);
        assertNotNull(blacklistUuid);
        createdBlacklistRecords.add(blacklistUuid);

        BlacklistRecord rec = client.getBlacklistRecord(blacklistUuid);
        assertNull(rec.expires());
        assertFalse(rec.lifted());
    }

    @Test
    void testBlacklistEntityInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
            () -> client.blacklistEntity("", "some-uuid", IncidentType.SPAM, 3600));
    }

    @Test
    void testBlacklistEntityInvalidEvidenceUuid() {
        assertThrows(IllegalArgumentException.class,
            () -> client.blacklistEntity("some-entity-uuid", "", IncidentType.SPAM, 3600));
    }

    @Test
    void testBlacklistEntityNegativeExpires() {
        assertThrows(IllegalArgumentException.class,
            () -> client.blacklistEntity("some-entity-uuid", "some-evidence-uuid", IncidentType.SPAM, -1));
    }

    @Test
    void testDeleteBlacklistRecord() {
        String entityUuid = client.pushEntity("delete-bl-" + randomUuid().substring(0, 8) + ".com", "user_to_delete");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test content for deletion", "Test note", "test");
        createdEvidenceRecords.add(evidenceUuid);

        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+3600));

        assertNotNull(client.getBlacklistRecord(blacklistUuid));
        client.deleteBlacklistRecord(blacklistUuid);

        try { client.getBlacklistRecord(blacklistUuid); fail("Expected FederationClientException"); }
        catch (FederationClientException e) { assertEquals(404, e.getStatusCode()); }
    }

    @Test
    void testDeleteBlacklistRecordInvalidUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.deleteBlacklistRecord(""));
    }

    @Test
    void testDeleteNonExistentBlacklistRecord() {
        try { client.deleteBlacklistRecord(randomUuid()); fail("Expected FederationClientException"); }
        catch (FederationClientException e) { assertEquals(404, e.getStatusCode()); }
    }

    @Test
    void testGetBlacklistRecordInvalidUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.getBlacklistRecord(""));
    }

    @Test
    void testLiftBlacklistRecord() {
        String entityUuid = client.pushEntity("lift-test-" + randomUuid().substring(0, 8) + ".com", "user_to_lift");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test content for lifting", "Test note", "test");
        createdEvidenceRecords.add(evidenceUuid);

        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+3600));
        createdBlacklistRecords.add(blacklistUuid);

        assertFalse(client.getBlacklistRecord(blacklistUuid).lifted());
        client.liftBlacklistRecord(blacklistUuid);
        assertTrue(client.getBlacklistRecord(blacklistUuid).lifted());
    }

    @Test
    void testLiftBlacklistRecordInvalidUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.liftBlacklistRecord(""));
    }

    @Test
    void testListBlacklistRecords() {
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String entityUuid = client.pushEntity("bl-list-" + i + "-" + randomUuid().substring(0, 8) + ".com", "user_" + i);
            createdEntities.add(entityUuid);

            String evidenceUuid = client.submitEvidence(entityUuid, "Test content " + i, "Test note " + i, "test");
            createdEvidenceRecords.add(evidenceUuid);

            String blUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+3600));
            createdBlacklistRecords.add(blUuid);
            created.add(blUuid);
        }

        List<BlacklistRecord> records = client.listBlacklistRecords(1, 100, true);
        Set<String> found = records.stream().map(BlacklistRecord::uuid).collect(Collectors.toSet());
        for (String uuid : created) {
            assertTrue(found.contains(uuid));
        }
    }

    @Test
    void testBlacklistEntityWithDifferentTypes() {
        IncidentType[] types = {IncidentType.SCAM, IncidentType.SERVICE_ABUSE, IncidentType.ILLEGAL_CONTENT,
                                IncidentType.PHISHING, IncidentType.OTHER};
        for (IncidentType type : types) {
            String entityUuid = client.pushEntity("type-test-" + randomUuid().substring(0, 8) + ".com", "user_" + type.getValue());
            createdEntities.add(entityUuid);

            String evidenceUuid = client.submitEvidence(entityUuid, "Test for " + type.getValue(), "Test note", type.getValue());
            createdEvidenceRecords.add(evidenceUuid);

            String blUuid = client.blacklistEntity(entityUuid, evidenceUuid, type, (int)(System.currentTimeMillis()/1000+3600));
            createdBlacklistRecords.add(blUuid);

            BlacklistRecord rec = client.getBlacklistRecord(blUuid);
            assertEquals(entityUuid, rec.entityUuid());
            assertEquals(evidenceUuid, rec.evidenceUuid());
        }
    }

    @Test
    void testBlacklistEntityUnauthorized() {
        OperatorCreated basicOpCreated = client.createOperator("unauth_bl_" + randomUuid().substring(0, 8));
        String basicOpUuid = basicOpCreated.uuid();
        createdOperators.add(basicOpUuid);
        client.setManagementPermissions(basicOpUuid, false);
        client.setOperatorPermissions(basicOpUuid, false);
        client.setClientPermissions(basicOpUuid, false);

        FederationClient basicClient = new FederationClient(serverEndpoint, basicOpCreated.accessToken());

        String entityUuid = createSecurityEntity();
        String evidenceUuid = createSecurityEvidence(entityUuid);

        try {
            basicClient.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+3600));
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        basicClient.close();
    }

    @Test
    void testBlacklistRecordLifecycleDurability() {
        List<String> blUuids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String entityUuid = client.pushEntity("dura-" + i + "-" + randomUuid().substring(0, 8) + ".com", "user_" + i);
            createdEntities.add(entityUuid);
            String evidenceUuid = client.submitEvidence(entityUuid, "Evidence " + i, "Note " + i, "dura");
            createdEvidenceRecords.add(evidenceUuid);
            String blUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+7200));
            createdBlacklistRecords.add(blUuid);
            blUuids.add(blUuid);
        }

        for (String uuid : blUuids) {
            assertFalse(client.getBlacklistRecord(uuid).lifted());
        }

        client.liftBlacklistRecord(blUuids.get(0));
        assertTrue(client.getBlacklistRecord(blUuids.get(0)).lifted());

        client.deleteBlacklistRecord(blUuids.get(2));
        removeFromCleanup(createdBlacklistRecords, blUuids.get(2));
        try { client.getBlacklistRecord(blUuids.get(2)); fail(); }
        catch (FederationClientException e) { assertEquals(404, e.getStatusCode()); }
    }

    @Test
    void testBlacklistRecordIntegrityAfterLift() {
        String entityUuid = createSecurityEntity();
        String evidenceUuid = createSecurityEvidence(entityUuid);
        int expires = (int) (System.currentTimeMillis() / 1000 + 7200);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        BlacklistRecord original = client.getBlacklistRecord(blacklistUuid);
        assertFalse(original.lifted());

        client.liftBlacklistRecord(blacklistUuid);
        BlacklistRecord lifted = client.getBlacklistRecord(blacklistUuid);
        assertTrue(lifted.lifted());
        assertEquals(original.entityUuid(), lifted.entityUuid());
        assertEquals(original.evidenceUuid(), lifted.evidenceUuid());
        assertEquals(original.expires(), lifted.expires());
    }

    @Test
    void testExtendBlacklistRecord() {
        String entityUuid = createSecurityEntity();
        String blacklistUuid = createSecurityBlacklist(entityUuid);

        BlacklistRecord original = client.getBlacklistRecord(blacklistUuid);
        Long originalExpires = original.expires();

        client.extendBlacklistRecord(blacklistUuid, 3600);
        BlacklistRecord extended = client.getBlacklistRecord(blacklistUuid);
        assertEquals(originalExpires + 3600, extended.expires().intValue());
    }

    @Test
    void testExtendBlacklistRecordInvalidUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.extendBlacklistRecord("", 3600));
    }

    @Test
    void testExtendBlacklistRecordInvalidSeconds() {
        assertThrows(IllegalArgumentException.class, () -> client.extendBlacklistRecord(randomUuid(), 0));
        assertThrows(IllegalArgumentException.class, () -> client.extendBlacklistRecord(randomUuid(), -1));
    }

    @Test
    void testExtendPermanentBlacklistRecord() {
        String entityUuid = createSecurityEntity();
        String evidenceUuid = createSecurityEvidence(entityUuid);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, null);
        createdBlacklistRecords.add(blacklistUuid);

        assertNull(client.getBlacklistRecord(blacklistUuid).expires());
        try { client.extendBlacklistRecord(blacklistUuid, 3600); fail(); }
        catch (FederationClientException e) { assertEquals(400, e.getStatusCode()); }
    }

    @Test
    void testExtendLiftedBlacklistRecord() {
        String entityUuid = createSecurityEntity();
        String blacklistUuid = createSecurityBlacklist(entityUuid);
        client.liftBlacklistRecord(blacklistUuid);

        try { client.extendBlacklistRecord(blacklistUuid, 3600); fail(); }
        catch (FederationClientException e) { assertEquals(400, e.getStatusCode()); }
    }

    @Test
    void testLiftedBlacklistNoLongerBlocksViaList() {
        String entityUuid = createSecurityEntity();
        String blacklistUuid = createSecurityBlacklist(entityUuid);

        List<BlacklistRecord> active = client.listEntityBlacklistRecords(entityUuid, 1, 100, false);
        assertTrue(active.stream().anyMatch(r -> r.uuid().equals(blacklistUuid)));

        client.liftBlacklistRecord(blacklistUuid);

        List<BlacklistRecord> activeAfter = client.listEntityBlacklistRecords(entityUuid, 1, 100, false);
        assertFalse(activeAfter.stream().anyMatch(r -> r.uuid().equals(blacklistUuid)));

        List<BlacklistRecord> allRecords = client.listEntityBlacklistRecords(entityUuid, 1, 100, true);
        assertTrue(allRecords.stream().anyMatch(r -> r.uuid().equals(blacklistUuid)));
    }

    @Test
    void testSecurityBlacklistRequiresManagementPermission() {
        String entityUuid = createSecurityEntity();
        String evidenceUuid = createSecurityEvidence(entityUuid);

        FederationClient clientOnly = createLimitedOperator("bl_create_client", false, false, true);
        try {
            clientOnly.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+3600));
            fail("Client-only should not create blacklists");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        clientOnly.close();
    }

    @Test
    void testBlacklistSameTypeMultipleTimesForSameEntity() {
        String entityUuid = client.pushEntity("same-type-multi-" + randomUuid().substring(0, 8) + ".com", "same_type_user");
        createdEntities.add(entityUuid);

        for (int i = 0; i < 3; i++) {
            String evidenceUuid = client.submitEvidence(entityUuid, "Same type evidence " + i, "Note " + i, "same_" + i);
            createdEvidenceRecords.add(evidenceUuid);
            String blUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+3600));
            createdBlacklistRecords.add(blUuid);
        }

        List<BlacklistRecord> entityBl = client.listEntityBlacklistRecords(entityUuid);
        assertTrue(entityBl.size() >= 3);
    }

    @Test
    void testBlacklistWithAllIncidentTypesIsRecorded() {
        String entityUuid = client.pushEntity("all-types-" + randomUuid().substring(0, 8) + ".com", "all_types_user");
        createdEntities.add(entityUuid);

        for (IncidentType type : IncidentType.values()) {
            String evidenceUuid = client.submitEvidence(entityUuid, "Evidence for " + type.getValue(), "Note", type.getValue());
            createdEvidenceRecords.add(evidenceUuid);
            String blUuid = client.blacklistEntity(entityUuid, evidenceUuid, type, (int)(System.currentTimeMillis()/1000+3600));
            createdBlacklistRecords.add(blUuid);
            assertEquals(type, client.getBlacklistRecord(blUuid).type());
        }
    }

    @Test
    void testBlacklistCreationUpdatesEntityBlacklistList() {
        String entityUuid = client.pushEntity("entity-bl-list-" + randomUuid().substring(0, 8) + ".com", "entity_bl_user");
        createdEntities.add(entityUuid);

        assertTrue(client.listEntityBlacklistRecords(entityUuid).isEmpty());

        String evidenceUuid = client.submitEvidence(entityUuid, "Entity blacklist evidence", "Note", "entity_bl");
        createdEvidenceRecords.add(evidenceUuid);
        String blUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, (int)(System.currentTimeMillis()/1000+3600));
        createdBlacklistRecords.add(blUuid);

        assertEquals(1, client.listEntityBlacklistRecords(entityUuid).size());
    }
}
