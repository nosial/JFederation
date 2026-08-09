package net.nosial.jfederation;

import com.sun.net.httpserver.HttpServer;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.*;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ClientFunctionalityTest extends FederationClientTestBase {

    @Test
    void testUpdateOperatorName() {
        String name = "update_name_test_" + randomUuid().substring(0, 8);
        OperatorCreated uuidCreated = client.createOperator(name);
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);

        OperatorRecord before = client.getOperator(uuid);
        assertEquals(name, before.name());

        String newName = "renamed_" + randomUuid().substring(0, 8);
        client.updateOperatorName(uuid, newName);

        OperatorRecord after = client.getOperator(uuid);
        assertEquals(newName, after.name());
        assertEquals(before.uuid(), after.uuid());
    }

    @Test
    void testUpdateOperatorNameEmptyNameThrows() {
        String uuid = client.createOperator("empty_name_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);

        assertThrows(IllegalArgumentException.class, () -> client.updateOperatorName(uuid, ""));
    }

    @Test
    void testUpdateOperatorNameNullUuidThrows() {
        assertThrows(IllegalArgumentException.class, () -> client.updateOperatorName("", "newName"));
    }

    @Test
    void testUpdateOperatorNameNonExistentOperator() {
        expectRequestFailure(
            () -> client.updateOperatorName(randomUuid(), "newName"),
            404
        );
    }

    @Test
    void testUploadFileAttachmentFromUrl() throws IOException {
        String entityUuid = client.pushEntity("upload-from-url-" + randomUuid().substring(0, 8) + ".com", "url_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "URL upload evidence", "URL test", "url_upload");
        createdEvidenceRecords.add(evidenceUuid);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String content = "File from URL upload test content " + randomUuid();
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        server.createContext("/test.txt", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, contentBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(contentBytes);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String fileUrl = "http://127.0.0.1:" + port + "/test.txt";
            int maxFileSize = contentBytes.length + 1024;

            UploadResult result = client.uploadFileAttachmentFromUrl(evidenceUuid, fileUrl, maxFileSize);
            assertNotNull(result);
            assertNotNull(result.uuid());
            assertNotNull(result.url());
            createdAttachments.add(result.uuid());

            FileAttachmentRecord info = client.getAttachmentInfo(result.uuid());
            assertEquals(evidenceUuid, info.evidenceUuid());
            assertTrue(info.fileSize() > 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testUploadFileAttachmentFromUrlFileTooLarge() throws IOException {
        String entityUuid = client.pushEntity("url-too-large-" + randomUuid().substring(0, 8) + ".com", "large_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "URL too large evidence", "URL large test", "url_large");
        createdEvidenceRecords.add(evidenceUuid);

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        byte[] contentBytes = "This content is larger than allowed".getBytes(StandardCharsets.UTF_8);
        server.createContext("/large.txt", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, contentBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(contentBytes);
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String fileUrl = "http://127.0.0.1:" + port + "/large.txt";
            int maxFileSize = 5;

            FederationClientException ex = assertThrows(FederationClientException.class,
                () -> client.uploadFileAttachmentFromUrl(evidenceUuid, fileUrl, maxFileSize));
            assertTrue(ex.getStatusCode() == 0 || ex.getStatusCode() == 413);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testUploadFileAttachmentFromUrlInvalidArguments() {
        String evidenceUuid = randomUuid();

        assertThrows(IllegalArgumentException.class,
            () -> client.uploadFileAttachmentFromUrl("", "http://example.com/file", 1024));

        assertThrows(IllegalArgumentException.class,
            () -> client.uploadFileAttachmentFromUrl(evidenceUuid, "", 1024));

        assertThrows(IllegalArgumentException.class,
            () -> client.uploadFileAttachmentFromUrl(evidenceUuid, "http://example.com/file", 0));

        assertThrows(IllegalArgumentException.class,
            () -> client.uploadFileAttachmentFromUrl(evidenceUuid, "http://example.com/file", -1));
    }

    @Test
    void testGenerateAccessTokenNoArg() {
        OperatorCreated uuidCreated = client.createOperator("noarg_token_test_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);
        client.setManagementPermissions(uuid, true);
        client.setOperatorPermissions(uuid, true);

        String initialToken = uuidCreated.accessToken();
        assertNotNull(initialToken);

        FederationClient opClient = new FederationClient(serverEndpoint, initialToken);
        assertEquals(uuid, opClient.getSelf().uuid());

        String oldToken = opClient.getAccessToken();
        String newToken = opClient.generateAccessToken();
        assertNotNull(newToken);
        assertNotEquals(oldToken, newToken);
        assertEquals(newToken, opClient.getAccessToken());

        FederationClient newOpClient = new FederationClient(serverEndpoint, newToken);
        assertEquals(uuid, newOpClient.getSelf().uuid());

        opClient.close();
        newOpClient.close();
    }

    @Test
    void testGenerateAccessTokenNoArgUpdatesClientToken() {
        OperatorCreated uuidCreated = client.createOperator("auto_update_token_" + randomUuid().substring(0, 8));
        String uuid = uuidCreated.uuid();
        createdOperators.add(uuid);
        client.setManagementPermissions(uuid, true);

        String initialToken = uuidCreated.accessToken();
        FederationClient opClient = new FederationClient(serverEndpoint, initialToken);
        String beforeToken = opClient.getAccessToken();
        opClient.generateAccessToken();
        String afterToken = opClient.getAccessToken();
        assertNotEquals(beforeToken, afterToken);
        opClient.close();
    }

    @Test
    void testListOperatorBlacklist() {
        OperatorCreated opUuidCreated = client.createOperator("list_op_bl_" + randomUuid().substring(0, 8));
        String opUuid = opUuidCreated.uuid();
        createdOperators.add(opUuid);
        client.setManagementPermissions(opUuid, true);
        client.setOperatorPermissions(opUuid, true);
        client.setClientPermissions(opUuid, true);

        String opToken = opUuidCreated.accessToken();
        FederationClient opClient = new FederationClient(serverEndpoint, opToken);

        String entityUuid = opClient.pushEntity("list-op-bl-" + randomUuid().substring(0, 8) + ".com", "op_bl_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = opClient.submitEvidence(entityUuid, "Operator blacklist evidence", "Note", "op_bl");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = opClient.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        List<BlacklistRecord> opBlacklists = client.listOperatorBlacklist(opUuid, 1, 100, true);
        assertNotNull(opBlacklists);
        boolean found = opBlacklists.stream().anyMatch(b -> b.uuid().equals(blacklistUuid));
        assertTrue(found, "Blacklist created by operator should appear in listOperatorBlacklist");

        List<BlacklistRecord> activeOnly = client.listOperatorBlacklist(opUuid, 1, 100, false);
        boolean foundActive = activeOnly.stream().anyMatch(b -> b.uuid().equals(blacklistUuid));
        assertTrue(foundActive, "Active blacklist should appear when includeLifted=false");

        opClient.liftBlacklistRecord(blacklistUuid);

        List<BlacklistRecord> afterLift = client.listOperatorBlacklist(opUuid, 1, 100, false);
        boolean foundAfterLift = afterLift.stream().anyMatch(b -> b.uuid().equals(blacklistUuid));
        assertFalse(foundAfterLift, "Lifted blacklist should not appear when includeLifted=false");

        List<BlacklistRecord> withLifted = client.listOperatorBlacklist(opUuid, 1, 100, true);
        boolean foundWithLifted = withLifted.stream().anyMatch(b -> b.uuid().equals(blacklistUuid));
        assertTrue(foundWithLifted, "Lifted blacklist should appear when includeLifted=true");

        opClient.close();
    }

    @Test
    void testListOperatorBlacklistInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorBlacklist("", 1, 10, true));

        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorBlacklist(randomUuid(), -1, 10, true));

        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorBlacklist(randomUuid(), 1, -1, true));
    }

    @Test
    void testListOperatorBlacklistNonExistentOperator() {
        expectRequestFailure(
            () -> client.listOperatorBlacklist(randomUuid(), 1, 10, true),
            404
        );
    }

    @Test
    void testClientWithCustomOkHttpClient() {
        OkHttpClient customClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectionPool(new ConnectionPool(2, 10, TimeUnit.SECONDS))
            .build();

        FederationClient customClientInstance = new FederationClient(serverEndpoint, serverAccessToken, customClient);
        assertNotNull(customClientInstance);

        ServerInformation info = customClientInstance.getServerInformation();
        assertNotNull(info);
        assertNotNull(info.serverName());

        String entityUuid = customClientInstance.pushEntity("custom-okhttp-" + randomUuid().substring(0, 8) + ".com", "custom_http_user");
        createdEntities.add(entityUuid);
        assertNotNull(entityUuid);

        customClientInstance.close();
    }

    @Test
    void testClientWithCustomOkHttpClientInvalidEndpoint() {
        OkHttpClient customClient = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .build();

        FederationClient customClientInstance = new FederationClient(
            "http://this-domain-does-not-exist-12345.com", serverAccessToken, customClient);
        assertThrows(FederationClientException.class, customClientInstance::getServerInformation);
        customClientInstance.close();
    }

    @Test
    void testListOperatorEvidence() {
        OperatorCreated opUuidCreated = client.createOperator("list_op_ev_" + randomUuid().substring(0, 8));
        String opUuid = opUuidCreated.uuid();
        createdOperators.add(opUuid);
        client.setClientPermissions(opUuid, true);

        String opToken = opUuidCreated.accessToken();
        FederationClient opClient = new FederationClient(serverEndpoint, opToken);

        String entityUuid = opClient.pushEntity("list-op-ev-" + randomUuid().substring(0, 8) + ".com", "op_ev_user");
        createdEntities.add(entityUuid);

        String[] evidenceUuids = new String[3];
        for (int i = 0; i < 3; i++) {
            String evUuid = opClient.submitEvidence(entityUuid, "Op evidence " + i, "Op note " + i, "op_ev_" + i);
            createdEvidenceRecords.add(evUuid);
            evidenceUuids[i] = evUuid;
        }

        List<EvidenceRecord> opEvidence = client.listOperatorEvidence(opUuid, 1, 100, true);
        assertNotNull(opEvidence);

        for (String evUuid : evidenceUuids) {
            boolean found = opEvidence.stream().anyMatch(e -> e.uuid().equals(evUuid));
            assertTrue(found, "Evidence " + evUuid + " should appear in listOperatorEvidence");
        }

        for (EvidenceRecord ev : opEvidence) {
            assertEquals(opUuid, ev.operatorUuid());
        }

        opClient.close();
    }

    @Test
    void testListOperatorEvidenceInvalidArguments() {
        String opUuid = client.createOperator("ev_invalid_test_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(opUuid);

        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorEvidence("", 1, 10, true));

        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorEvidence(opUuid, -1, 10, true));

        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorEvidence(opUuid, 1, -1, true));
    }

    @Test
    void testListEntityAuditLogs() {
        String entityUuid = client.pushEntity("entity-audit-" + randomUuid().substring(0, 8) + ".com", "audit_entity_user");
        createdEntities.add(entityUuid);

        client.pushEntity("trigger-audit-" + randomUuid().substring(0, 8) + ".com", "trigger_user");
        createdEntities.add(entityUuid);

        List<AuditLog> entityLogs = client.listEntityAuditLogs(entityUuid, 1, 10);
        assertNotNull(entityLogs);

        for (AuditLog log : entityLogs) {
            assertNotNull(log.uuid());
            assertNotNull(log.type());
            assertTrue(log.timestamp() > 0);
        }
    }

    @Test
    void testListEntityAuditLogsInvalidArguments() {
        String entityUuid = client.pushEntity("entity-audit-invalid-" + randomUuid().substring(0, 8) + ".com", "audit_invalid_user");
        createdEntities.add(entityUuid);

        assertThrows(IllegalArgumentException.class,
            () -> client.listEntityAuditLogs("", 1, 10));

        assertThrows(IllegalArgumentException.class,
            () -> client.listEntityAuditLogs(entityUuid, 0, 10));

        assertThrows(IllegalArgumentException.class,
            () -> client.listEntityAuditLogs(entityUuid, 1, -1));
    }

    @Test
    void testSearchAuditLogs() {
        String opName = "search_audit_" + randomUuid().substring(0, 8);
        OperatorCreated opUuidCreated = client.createOperator(opName);
        String opUuid = opUuidCreated.uuid();
        createdOperators.add(opUuid);

        List<AuditLog> results = client.searchAuditLogs(opName, 1, 10);
        assertNotNull(results);
    }

    @Test
    void testSearchAuditLogsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
            () -> client.searchAuditLogs("", 1, 10));

        assertThrows(IllegalArgumentException.class,
            () -> client.searchAuditLogs("ab", 0, 10));

        assertThrows(IllegalArgumentException.class,
            () -> client.searchAuditLogs("ab", 1, -1));

        assertThrows(IllegalArgumentException.class,
            () -> client.searchAuditLogs("a", 1, 10));
    }

    @Test
    void testGetSpecification() {
        var spec = client.getSpecification();
        assertNotNull(spec);
        assertTrue(spec.has("openapi"));
        assertTrue(spec.has("info"));
        assertTrue(spec.has("paths"));
    }

    @Test
    void testClearEntityRelationshipNonExistentEntity() {
        expectRequestFailure(
            () -> client.clearEntityRelationship(randomUuid()),
            new int[]{400, 404}
        );
    }

    @Test
    void testClearEntityRelationshipInvalidIdentifier() {
        assertThrows(IllegalArgumentException.class,
            () -> client.clearEntityRelationship(""));
    }
}
