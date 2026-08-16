package net.nosial.jfederation;

import net.nosial.jfederation.enums.EntityRelationshipType;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.EntityRecord;
import net.nosial.jfederation.records.EntityQueryResult;
import net.nosial.jfederation.records.EvidenceRecord;
import net.nosial.jfederation.records.ServerInformation;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EntitiesClientTest extends FederationClientTestBase {

    @Test
    void testPushEntity() {
        String userEntityUuid = client.pushEntity("example.com", "john123_" + randomUuid().substring(0, 8));
        createdEntities.add(userEntityUuid);
        assertNotNull(userEntityUuid);

        EntityRecord rec = client.getEntityRecord(userEntityUuid);
        assertEquals(rec.uuid(), rec.uuid());
        assertNotNull(rec.id());
        assertEquals("example.com", rec.host());

        String globalEntityUuid = client.pushEntity("example.com");
        createdEntities.add(globalEntityUuid);
        assertNotNull(globalEntityUuid);

        EntityRecord globalRec = client.getEntityRecord(globalEntityUuid);
        assertEquals("example.com", globalRec.host());

        String ipEntityUuid = client.pushEntity("127.0.0.1");
        createdEntities.add(ipEntityUuid);
        assertNotNull(ipEntityUuid);

        EntityRecord ipRec = client.getEntityRecord(ipEntityUuid);
        assertEquals("127.0.0.1", ipRec.host());
    }

    @Test
    void testPushInvalidIpAddressEntity() {
        try {
            client.pushEntity("999.999.999.999 2");
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    @Test
    void testPushInvalidDomainEntity() {
        try {
            client.pushEntity("invalid_domain@");
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    @Test
    void testPushEntityMissingHost() {
        assertThrows(IllegalArgumentException.class, () -> client.pushEntity(""));
    }

    @Test
    void testPushEntityWithMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "integration_test");
        metadata.put("importance", "high");
        String uuid = client.pushEntity("metadata-test.com", "meta_user", metadata);
        createdEntities.add(uuid);

        EntityRecord rec = client.getEntityRecord(uuid);
        assertNotNull(rec);
    }

    @Test
    void testDeleteEntity() {
        String uuid = client.pushEntity("delete-example.com", "delete_user_" + randomUuid().substring(0, 8));
        createdEntities.add(uuid);

        assertNotNull(client.getEntityRecord(uuid));
        client.deleteEntity(uuid);
        removeFromCleanup(createdEntities, uuid);

        try {
            client.getEntityRecord(uuid);
            fail("Expected FederationClientException for deleted entity");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testDeleteNonExistentEntity() {
        try {
            client.deleteEntity(UUID.randomUUID().toString());
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testListEntities() {
        String[] uuids = new String[5];
        for (int i = 0; i < 5; i++) {
            uuids[i] = client.pushEntity("list-test-" + i + ".com", "user" + i);
            createdEntities.add(uuids[i]);
        }

        List<EntityRecord> all = new ArrayList<>();
        int page = 1;
        List<EntityRecord> pageResults;
        do {
            pageResults = client.listEntities(page, 2);
            all.addAll(pageResults);
            page++;
        } while (!pageResults.isEmpty());

        for (String uuid : uuids) {
            boolean found = all.stream().anyMatch(e -> e.uuid().equals(uuid));
            assertTrue(found, "Created entity " + uuid + " should be found in list");
        }
    }

    @Test
    void testListEntitiesInvalidPage() {
        assertThrows(IllegalArgumentException.class, () -> client.listEntities(-10000, 10));
    }

    @Test
    void testListEntitiesInvalidLimit() {
        assertThrows(IllegalArgumentException.class, () -> client.listEntities(1, -1));
    }

    @Test
    void testPushEmptyEntity() {
        assertThrows(IllegalArgumentException.class, () -> client.pushEntity("", ""));
    }

    @Test
    void testPushEmptyEntityHost() {
        assertThrows(IllegalArgumentException.class, () -> client.pushEntity("", "someid"));
    }

    @Test
    void testPushEmptyEntityId() {
        assertThrows(IllegalArgumentException.class, () -> client.pushEntity("example.com", ""));
    }

    @Test
    void testGetEntityAsAnonymousClient() {
        String uuid = client.pushEntity("anon-get-" + randomUuid().substring(0, 8) + ".com", "john123");
        createdEntities.add(uuid);

        ServerInformation info = client.getServerInformation();
        FederationClient anon = createAnonymousClient();

        if (info.publicEntities()) {
            EntityRecord rec = anon.getEntityRecord(uuid);
            assertEquals(uuid, rec.uuid());
        } else {
            try {
                anon.getEntityRecord(uuid);
                fail("Expected FederationClientException for non-public entities");
            } catch (FederationClientException e) {
                assertTrue(e.getStatusCode() == 401 || e.getStatusCode() == 403);
            }
        }
        anon.close();
    }

    @Test
    void testPushEntityAsAnonymousClient() {
        FederationClient anon = createAnonymousClient();
        try {
            anon.pushEntity("example.com", "john123");
            fail("Expected FederationClientException for unauthenticated pushEntity");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401);
        }
        anon.close();
    }

    @Test
    void testEntityDuplicationHandling() {
        String host = "duplication-test.com";
        String id = "duplicate_user_" + randomUuid().substring(0, 8);
        String firstUuid = client.pushEntity(host, id);
        createdEntities.add(firstUuid);

        for (int i = 0; i < 3; i++) {
            String dup = client.pushEntity(host, id);
            assertEquals(firstUuid, dup);
        }
    }

    @Test
    void testEntityLifecycleIntegrity() {
        String uuid = client.pushEntity("lifecycle-test.com", "lifecycle_user_" + randomUuid().substring(0, 8));
        createdEntities.add(uuid);

        assertNotNull(client.getEntityRecord(uuid));

        String evidenceUuid = client.submitEvidence(uuid, "Lifecycle test evidence", "Test note", "lifecycle");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> entityEvidence = client.listEntityEvidenceRecords(uuid);
        boolean found = entityEvidence.stream().anyMatch(e -> e.uuid().equals(evidenceUuid));
        assertTrue(found);

        client.deleteEvidence(evidenceUuid);
        removeFromCleanup(createdEvidenceRecords, evidenceUuid);
        client.deleteEntity(uuid);
        removeFromCleanup(createdEntities, uuid);

        try {
            client.getEntityRecord(uuid);
            fail("Expected FederationClientException for deleted entity");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testSecurityEntityRelationshipAbuse() {
        String entityA = createSecurityEntity();
        String entityB = createSecurityEntity();

        client.setEntityRelationship(entityA, entityA, EntityRelationshipType.ALTERNATIVE);
        client.setEntityRelationship(entityA, entityB, EntityRelationshipType.PROXY);
        client.setEntityRelationship(entityB, entityA, EntityRelationshipType.PROXY);

        EntityRecord recordB = client.getEntityRecord(entityB);
        assertEquals(entityA, recordB.relationshipEntity());

        // Relationship to non-existent target
        try {
            client.setEntityRelationship(entityA, randomUuid(), EntityRelationshipType.ALTERNATIVE);
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 404);
        }
    }

    @Test
    void testSecurityDeleteEntityCascade() {
        String entityUuid = createSecurityEntity();
        String evidenceUuid = createSecurityEvidence(entityUuid);
        String blacklistUuid = createSecurityBlacklist(entityUuid);

        client.deleteEntity(entityUuid);
        removeFromCleanup(createdEntities, entityUuid);
        removeFromCleanup(createdEvidenceRecords, evidenceUuid);
        removeFromCleanup(createdBlacklistRecords, blacklistUuid);

        try { client.getEntityRecord(entityUuid); fail("Entity should be gone"); }
        catch (FederationClientException e) { assertEquals(404, e.getStatusCode()); }

        try { client.getEvidenceRecord(evidenceUuid); fail("Evidence should be gone"); }
        catch (FederationClientException e) { assertEquals(404, e.getStatusCode()); }

        try { client.getBlacklistRecord(blacklistUuid); fail("Blacklist should be gone"); }
        catch (FederationClientException e) { assertEquals(404, e.getStatusCode()); }
    }

    @Test
    void testSecurityClearReputationRequiresManagementPermission() {
        String entityUuid = createSecurityEntity();
        FederationClient clientOnly = createLimitedOperator("reputation_client", true);

        try {
            clientOnly.clearEntityReputation(entityUuid);
            fail("Client-only operator should not clear reputation");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }

        try {
            client.clearEntityReputation(randomUuid());
            fail("Non-existent entity should fail");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 404 || e.getStatusCode() == 400);
        }
        clientOnly.close();
    }

    @Test
    void testGetTopThreatsBasic() {
        for (int i = 0; i < 5; i++) {
            String uuid = client.pushEntity("top-threats-basic-" + i + ".com", "user_" + i);
            createdEntities.add(uuid);
        }

        List<EntityRecord> topThreats = client.getTopThreats(10);
        assertNotNull(topThreats);
        assertFalse(topThreats.isEmpty());
    }

    @Test
    void testGetTopThreatsWithCustomLimit() {
        for (int i = 0; i < 10; i++) {
            String uuid = client.pushEntity("top-threats-limit-" + i + ".com", "user_" + i);
            createdEntities.add(uuid);
        }

        List<EntityRecord> topThreats = client.getTopThreats(3);
        assertNotNull(topThreats);
        assertEquals(3, topThreats.size());
    }

    @Test
    void testGetTopThreatsInvalidLimit() {
        assertThrows(IllegalArgumentException.class, () -> client.getTopThreats(0));
        assertThrows(IllegalArgumentException.class, () -> client.getTopThreats(-1));
    }

    @Test
    void testSecurityEntityRelationshipRequiresOperatorPermissions() {
        String entityA = createSecurityEntity();
        String entityB = createSecurityEntity();

        FederationClient clientOnly = createLimitedOperator("entity_rel_client", false, false, true);
        try {
            clientOnly.setEntityRelationship(entityA, entityB, EntityRelationshipType.ALTERNATIVE);
            fail("Client-only should not set entity relationships");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        clientOnly.close();
    }

    @Test
    void testEntityCreationWithAllValidHosts() {
        String[] hosts = {"example.com", "sub.example.co.uk", "192.168.1.1", "localhost", "a-b-c.example.org"};
        for (String host : hosts) {
            String uuid = client.pushEntity(host, "host_test_user_" + randomUuid().substring(0, 8));
            createdEntities.add(uuid);
            assertEquals(host, client.getEntityRecord(uuid).host());
        }
    }

    @Test
    void testEntityQueryIncludesEvidenceAndBlacklist() {
        String entityUuid = client.pushEntity("query-test-" + randomUuid().substring(0, 8) + ".com", "query_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Query evidence", "Note", "query");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        assertNotNull(client.getEntityRecord(entityUuid));
        assertFalse(client.listEntityEvidenceRecords(entityUuid).isEmpty());
        assertFalse(client.listEntityBlacklistRecords(entityUuid).isEmpty());
    }
    @Test
    void testQueryEntityIncludesRelationshipAndBlacklistState() {
        String parentUuid = createSecurityEntity();
        String childUuid = createSecurityEntity();
        client.setEntityRelationship(childUuid, parentUuid, EntityRelationshipType.CHILD);

        String evidenceUuid = createSecurityEvidence(childUuid);
        String blacklistUuid = client.blacklistEntity(childUuid, evidenceUuid, IncidentType.SPAM,
            (int) (System.currentTimeMillis() / 1000 + 3600));
        createdBlacklistRecords.add(blacklistUuid);

        EntityQueryResult result = client.queryEntity(childUuid);

        assertEquals(childUuid, result.entityRecord().uuid());
        assertTrue(result.relatedEntities().stream().anyMatch(entity -> entity.uuid().equals(parentUuid)));
        assertTrue(result.activeBlacklists().stream().anyMatch(record -> record.uuid().equals(blacklistUuid)));
        assertNotNull(result.suggestedAction());
        assertNotNull(result.suggestedLiftTimestamp());
    }

    @Test
    void testQueryEntityValidation() {
        assertThrows(IllegalArgumentException.class, () -> client.queryEntity(""));
        assertThrows(IllegalArgumentException.class, () -> client.queryEntity(null));
    }


    @Test
    void testDuplicateEntityPushMergesMetadata() {
        String host = "duplicate-metadata-" + randomUuid().substring(0, 8) + ".com";
        String id = "duplicate_user";

        Map<String, Object> m1 = new HashMap<>();
        m1.put("stage", 1);
        String firstUuid = client.pushEntity(host, id, m1);
        createdEntities.add(firstUuid);

        Map<String, Object> m2 = new HashMap<>();
        m2.put("stage", 2);
        m2.put("extra", "value");
        String secondUuid = client.pushEntity(host, id, m2);
        assertEquals(firstUuid, secondUuid);
    }

    @Test
    void testEntityRelationshipCyclePersists() {
        String entityA = createSecurityEntity();
        String entityB = createSecurityEntity();
        String entityC = createSecurityEntity();

        client.setEntityRelationship(entityA, entityB, EntityRelationshipType.PROXY);
        client.setEntityRelationship(entityB, entityC, EntityRelationshipType.PROXY);
        client.setEntityRelationship(entityC, entityA, EntityRelationshipType.PROXY);

        assertEquals(entityB, client.getEntityRecord(entityA).relationshipEntity());
        assertEquals(entityC, client.getEntityRecord(entityB).relationshipEntity());
        assertEquals(entityA, client.getEntityRecord(entityC).relationshipEntity());
    }

    @Test
    void testEntityRelationshipOverwritesPreviousRelationship() {
        String entityA = createSecurityEntity();
        String entityB = createSecurityEntity();
        String entityC = createSecurityEntity();

        client.setEntityRelationship(entityA, entityB, EntityRelationshipType.ALTERNATIVE);
        assertEquals(entityB, client.getEntityRecord(entityA).relationshipEntity());

        client.setEntityRelationship(entityA, entityC, EntityRelationshipType.CHILD);
        assertEquals(entityC, client.getEntityRecord(entityA).relationshipEntity());

        client.clearEntityRelationship(entityA);
        assertNull(client.getEntityRecord(entityA).relationshipEntity());
    }

    @Test
    void testUpdateEntityMetadata() {
        String entityUuid = client.pushEntity("update-metadata-" + randomUuid().substring(0, 8) + ".com", "update_meta_user");
        createdEntities.add(entityUuid);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "integration_test");
        metadata.put("priority", "high");
        client.updateEntity(entityUuid, metadata);

        EntityRecord record = client.getEntityRecord(entityUuid);
        assertEquals("integration_test", record.getMetadata().get("source"));
        assertEquals("high", record.getMetadata().get("priority"));
    }

    @Test
    void testUpdateEntityMergesExistingMetadata() {
        Map<String, Object> initial = new HashMap<>();
        initial.put("stage", "one");
        String entityUuid = client.pushEntity("update-merge-" + randomUuid().substring(0, 8) + ".com", "update_merge_user", initial);
        createdEntities.add(entityUuid);

        Map<String, Object> update = new HashMap<>();
        update.put("stage", "two");
        update.put("extra", "value");
        client.updateEntity(entityUuid, update);

        EntityRecord record = client.getEntityRecord(entityUuid);
        Map<String, Object> metadata = record.getMetadata();
        assertEquals("two", metadata.get("stage"));
        assertEquals("value", metadata.get("extra"));
    }

    @Test
    void testUpdateEntityValidation() {
        assertThrows(IllegalArgumentException.class, () -> client.updateEntity("", Map.of("key", "value")));
        assertThrows(IllegalArgumentException.class, () -> client.updateEntity(randomUuid(), null));
    }

    @Test
    void testSetEntityWhitelist() {
        String entityUuid = client.pushEntity("whitelist-" + randomUuid().substring(0, 8) + ".com", "whitelist_user");
        createdEntities.add(entityUuid);

        assertFalse(client.getEntityRecord(entityUuid).whitelisted());

        client.setEntityWhitelist(entityUuid, true);
        assertTrue(client.getEntityRecord(entityUuid).whitelisted());

        client.setEntityWhitelist(entityUuid, false);
        assertFalse(client.getEntityRecord(entityUuid).whitelisted());
    }

    @Test
    void testSetEntityWhitelistValidation() {
        assertThrows(IllegalArgumentException.class, () -> client.setEntityWhitelist("", true));
        assertThrows(IllegalArgumentException.class, () -> client.setEntityWhitelist(null, false));
    }
}
