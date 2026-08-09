package net.nosial.jfederation;

import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.EntityRecord;
import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

class EntityQueryTest extends FederationClientTestBase {

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String hashEntity(String host) {
        return sha256(host);
    }

    private static String hashEntity(String host, String id) {
        return sha256(id + "@" + host);
    }

    @Test
    void testQueryEntityByHash() {
        String host = "query-test.com";
        String id = "query_user_" + randomUuid().substring(0, 8);
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        String hash = hashEntity(host, id);
        assertNotNull(hash);

        EntityRecord entityRecord = client.getEntityRecord(hash);
        assertNotNull(entityRecord);
        assertEquals(entityUuid, entityRecord.uuid());
        assertEquals(host, entityRecord.host());
        assertEquals(id, entityRecord.id());
    }

    @Test
    void testQueryEntityByHashGlobal() {
        String host = "global-query-" + randomUuid().substring(0, 8) + ".com";
        String entityUuid = client.pushEntity(host);
        createdEntities.add(entityUuid);

        String hash = hashEntity(host);
        assertNotNull(hash);

        EntityRecord entityRecord = client.getEntityRecord(hash);
        assertNotNull(entityRecord);
        assertEquals(entityUuid, entityRecord.uuid());
        assertEquals(host, entityRecord.host());
        assertNull(entityRecord.id());
    }

    @Test
    void testQueryEntityByUuid() {
        String host = "uuid-query-" + randomUuid().substring(0, 8) + ".com";
        String id = "uuid_query_user_" + randomUuid().substring(0, 8);
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        EntityRecord entityRecord = client.getEntityRecord(entityUuid);
        assertNotNull(entityRecord);
        assertEquals(entityUuid, entityRecord.uuid());
        assertEquals(host, entityRecord.host());
        assertEquals(id, entityRecord.id());
    }

    @Test
    void testQueryNonExistentEntity() {
        expectRequestFailure(
            () -> client.getEntityRecord("bc1d8716-df05-4551-935a-007192550f17"),
            404
        );
    }

    @Test
    void testQueryWithInvalidUuid() {
        expectRequestFailure(
            () -> client.getEntityRecord("invalid-uuid-format"),
            new int[]{400, 404, 422}
        );
    }

    @Test
    void testHashConsistencyForSameEntity() {
        String host = "consistency-" + randomUuid().substring(0, 8) + ".com";
        String id = "consistency_user_" + randomUuid().substring(0, 8);

        String hash1 = hashEntity(host, id);
        String hash2 = hashEntity(host, id);
        String hash3 = hashEntity(host, id);

        assertEquals(hash1, hash2);
        assertEquals(hash1, hash3);
    }

    @Test
    void testHashUniquenessForDifferentEntities() {
        String hash1 = hashEntity("test1.com", "user1");
        String hash2 = hashEntity("test1.com", "user2");
        String hash3 = hashEntity("test2.com", "user1");
        String hash4 = hashEntity("test1.com");

        assertNotEquals(hash1, hash2);
        assertNotEquals(hash1, hash3);
        assertNotEquals(hash1, hash4);
        assertNotEquals(hash2, hash3);
        assertNotEquals(hash2, hash4);
        assertNotEquals(hash3, hash4);
    }

    @Test
    void testHashFormatAndLength() {
        String hash = hashEntity("format-test.com", "format_user");
        assertNotNull(hash);
        assertFalse(hash.isEmpty());
        assertTrue(hash.length() > 10);
    }

    @Test
    void testQueryResultStructure() {
        String host = "structure-" + randomUuid().substring(0, 8) + ".com";
        String id = "structure_user_" + randomUuid().substring(0, 8);
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        EntityRecord entityRecord = client.getEntityRecord(entityUuid);
        assertNotNull(entityRecord.uuid());
        assertNotNull(entityRecord.host());
        assertNotNull(entityRecord.id());
        assertTrue(entityRecord.created() > 0);

        long now = System.currentTimeMillis() / 1000;
        assertTrue(entityRecord.created() <= now);
        assertTrue(entityRecord.created() > now - 3600);
    }

    @Test
    void testQueryGlobalEntityStructure() {
        String host = "global-structure-" + randomUuid().substring(0, 8) + ".com";
        String entityUuid = client.pushEntity(host);
        createdEntities.add(entityUuid);

        EntityRecord entityRecord = client.getEntityRecord(entityUuid);
        assertNotNull(entityRecord.uuid());
        assertNotNull(entityRecord.host());
        assertNull(entityRecord.id());
        assertTrue(entityRecord.created() > 0);
    }

    @Test
    void testQueryEntitiesWithSpecialCharacters() {
        String[][] testCases = {
            {"special-chars.com", "user_with_underscore"},
            {"test-domain.org", "user-with-hyphens"},
            {"numbers123.net", "user123"},
            {"subdomain.example.co.uk", "user.with.dots"},
        };

        for (String[] testCase : testCases) {
            String host = testCase[0];
            String id = testCase[1] + "_" + randomUuid().substring(0, 8);
            String entityUuid = client.pushEntity(host, id);
            createdEntities.add(entityUuid);

            EntityRecord entityByUuid = client.getEntityRecord(entityUuid);
            assertEquals(host, entityByUuid.host());
            assertEquals(id, entityByUuid.id());

            String hash = hashEntity(host, id);
            EntityRecord entityByHash = client.getEntityRecord(hash);
            assertEquals(entityUuid, entityByHash.uuid());
            assertEquals(host, entityByHash.host());
            assertEquals(id, entityByHash.id());
        }
    }

    @Test
    void testQueryIpAddressEntities() {
        String[] ipAddresses = {
            "192.168.1.1",
            "10.0.0.1",
            "172.16.0.1",
            "127.0.0.1",
            "8.8.8.8"
        };

        for (String ip : ipAddresses) {
            String entityUuid = client.pushEntity(ip);
            createdEntities.add(entityUuid);

            EntityRecord entityByUuid = client.getEntityRecord(entityUuid);
            assertEquals(ip, entityByUuid.host());
            assertNull(entityByUuid.id());

            String hash = hashEntity(ip);
            EntityRecord entityByHash = client.getEntityRecord(hash);
            assertEquals(entityUuid, entityByHash.uuid());
            assertEquals(ip, entityByHash.host());
            assertNull(entityByHash.id());
        }
    }

    @Test
    void testQueryConsistencyAfterMultipleCreations() {
        String host = "consistency-multi-" + randomUuid().substring(0, 8) + ".com";
        String id = "consistency_user_" + randomUuid().substring(0, 8);

        String uuid1 = client.pushEntity(host, id);
        String uuid2 = client.pushEntity(host, id);
        String uuid3 = client.pushEntity(host, id);
        createdEntities.add(uuid1);

        assertEquals(uuid1, uuid2);
        assertEquals(uuid1, uuid3);

        String hash = hashEntity(host, id);

        for (int i = 0; i < 5; i++) {
            EntityRecord resultByUuid = client.getEntityRecord(uuid1);
            EntityRecord resultByHash = client.getEntityRecord(hash);

            assertEquals(uuid1, resultByUuid.uuid());
            assertEquals(uuid1, resultByHash.uuid());
            assertEquals(host, resultByUuid.host());
            assertEquals(host, resultByHash.host());
            assertEquals(id, resultByUuid.id());
            assertEquals(id, resultByHash.id());
        }
    }

    @Test
    void testQueryEntityAsAnonymousClient() {
        ServerInformationTest serverInfoTest = new ServerInformationTest();
        if (!client.getServerInformation().publicEntities()) {
            return;
        }

        String host = "anonymous-query-" + randomUuid().substring(0, 8) + ".com";
        String id = "anonymous_user_" + randomUuid().substring(0, 8);
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        FederationClient anonymousClient = createAnonymousClient();

        EntityRecord entityByUuid = anonymousClient.getEntityRecord(entityUuid);
        assertEquals(entityUuid, entityByUuid.uuid());
        assertEquals(host, entityByUuid.host());
        assertEquals(id, entityByUuid.id());

        String hash = hashEntity(host, id);
        EntityRecord entityByHash = anonymousClient.getEntityRecord(hash);
        assertEquals(entityUuid, entityByHash.uuid());
        assertEquals(host, entityByHash.host());
        assertEquals(id, entityByHash.id());
    }
}
