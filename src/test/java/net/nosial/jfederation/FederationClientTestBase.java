package net.nosial.jfederation;

import net.nosial.jfederation.enums.ClassificationFlag;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.OperatorCreated;
import net.nosial.jfederation.records.OperatorRecord;
import net.nosial.jfederation.records.ReportSubmission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public abstract class FederationClientTestBase {
    protected FederationClient client;
    protected final List<String> createdOperators = new ArrayList<>();
    protected final List<String> createdEntities = new ArrayList<>();
    protected final List<String> createdEvidenceRecords = new ArrayList<>();
    protected final List<String> createdBlacklistRecords = new ArrayList<>();
    protected final List<String> createdReports = new ArrayList<>();
    protected final List<String> createdAttachments = new ArrayList<>();
    protected final List<Path> createdTempFiles = new ArrayList<>();

    protected static String serverEndpoint;
    protected static String serverAccessToken;

    static {
        serverEndpoint = System.getenv("SERVER_ENDPOINT");
        if (serverEndpoint == null || serverEndpoint.isEmpty()) {
            serverEndpoint = "http://localhost:7000";
        }
        serverAccessToken = System.getenv("SERVER_ACCESS_TOKEN");
        if (serverAccessToken == null || serverAccessToken.isEmpty()) {
            serverAccessToken = "abcdefghijklmnopqrstuvwxyz123456";
        }
    }

    @BeforeEach
    void setUpClient() {
        client = new FederationClient(serverEndpoint, serverAccessToken);
    }

    @AfterEach
    void tearDownClient() {
        for (String uuid : createdAttachments) {
            try { client.deleteAttachment(uuid); } catch (Exception ignored) {}
        }
        for (String uuid : createdReports) {
            try { client.deleteReport(uuid); } catch (Exception ignored) {}
        }
        for (String uuid : createdBlacklistRecords) {
            try { client.deleteBlacklistRecord(uuid); } catch (Exception ignored) {}
        }
        for (String uuid : createdEvidenceRecords) {
            try { client.deleteEvidence(uuid); } catch (Exception ignored) {}
        }
        for (String uuid : createdEntities) {
            try { client.deleteEntity(uuid); } catch (Exception ignored) {}
        }
        for (String uuid : createdOperators) {
            try { client.deleteOperator(uuid); } catch (Exception ignored) {}
        }
        for (Path p : createdTempFiles) {
            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
        }
        createdOperators.clear();
        createdEntities.clear();
        createdEvidenceRecords.clear();
        createdBlacklistRecords.clear();
        createdReports.clear();
        createdAttachments.clear();
        createdTempFiles.clear();
        client.close();
    }

    protected FederationClient createAnonymousClient() {
        return new FederationClient(serverEndpoint);
    }

    protected FederationClient createLimitedOperator(String namePrefix, boolean management, boolean operator, boolean clientPerm) {
        String name = namePrefix + "_" + UUID.randomUUID().toString().substring(0, 5);
        OperatorCreated createdOperator = this.client.createOperator(name);
        String uuid = createdOperator.uuid();
        createdOperators.add(uuid);
        if (management) { this.client.setManagementPermissions(uuid, true); }
        if (operator) { this.client.setOperatorPermissions(uuid, true); }
        if (clientPerm) { this.client.setClientPermissions(uuid, true); }
        FederationClient opClient = new FederationClient(serverEndpoint, createdOperator.accessToken());
        return opClient;
    }

    protected FederationClient createLimitedOperator(String namePrefix) {
        return createLimitedOperator(namePrefix, false, false, false);
    }

    protected FederationClient createLimitedOperator(String namePrefix, boolean clientPerm) {
        return createLimitedOperator(namePrefix, false, false, clientPerm);
    }

    protected String createSecurityEntity() {
        return createSecurityEntity(this.client);
    }

    protected String createSecurityEntity(FederationClient client) {
        String host = "security-test-" + UUID.randomUUID().toString().substring(0, 8) + ".com";
        String id = "user_" + UUID.randomUUID().toString().substring(0, 8);
        String uuid = client.pushEntity(host, id);
        createdEntities.add(uuid);
        return uuid;
    }

    protected String createSecurityEvidence(String entityUuid) {
        return createSecurityEvidence(entityUuid, false, this.client);
    }

    protected String createSecurityEvidence(String entityUuid, boolean confidential) {
        return createSecurityEvidence(entityUuid, confidential, this.client);
    }

    protected String createSecurityEvidence(String entityUuid, boolean confidential, FederationClient client) {
        String uuid = client.submitEvidence(entityUuid, "Security test evidence", "security note", "security", confidential);
        createdEvidenceRecords.add(uuid);
        return uuid;
    }

    protected String createSecurityBlacklist(String entityUuid) {
        return createSecurityBlacklist(entityUuid, this.client);
    }

    protected String createSecurityBlacklist(String entityUuid, FederationClient client) {
        String evidenceUuid = createSecurityEvidence(entityUuid, false, client);
        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);
        return blacklistUuid;
    }

    protected record SecurityReport(String report, String entity, String evidence) {}

    protected SecurityReport createSecurityReport() {
        return createSecurityReport(this.client);
    }

    protected SecurityReport createSecurityReport(FederationClient client) {
        String entityUuid = createSecurityEntity(client);
        ReportSubmission submission = client.submitReport(entityUuid, "Security test report", IncidentType.SPAM);
        createdReports.add(submission.getReport().uuid());
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());
        return new SecurityReport(submission.getReport().uuid(), entityUuid, submission.getEvidence().get(0).uuid());
    }

    protected Path createTempFile(String name, String content) {
        try {
            Path p = Files.createTempFile(name, ".txt");
            Files.writeString(p, content);
            createdTempFiles.add(p);
            return p;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp file", e);
        }
    }

    protected void expectRequestFailure(Runnable callback, int[] allowedCodes, String message) {
        try {
            callback.run();
            fail(message);
        } catch (FederationClientException e) {
            boolean found = false;
            for (int code : allowedCodes) {
                if (e.getStatusCode() == code) { found = true; break; }
            }
            assertTrue(found, "Unexpected HTTP status code: " + e.getStatusCode() + " - " + e.getMessage());
        }
    }

    protected void expectRequestFailure(Runnable callback, int allowedCode, String message) {
        expectRequestFailure(callback, new int[]{allowedCode}, message);
    }

    protected void expectRequestFailure(Runnable callback, int allowedCode) {
        expectRequestFailure(callback, new int[]{allowedCode}, "Expected FederationClientException");
    }

    protected void expectRequestFailure(Runnable callback, int[] allowedCodes) {
        expectRequestFailure(callback, allowedCodes, "Expected FederationClientException");
    }

    protected String randomUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Probes the server to determine whether attachment search is enabled.
     *
     * @return {@code true} if the server supports searching attachments,
     *         {@code false} when the server's configuration disables it
     *         (search.enable_attachments defaults to false in FederationLib)
     */
    protected boolean isAttachmentSearchEnabled() {
        try {
            client.searchAttachments("zzz_attach_search_probe", 1, 1);
            return true;
        } catch (FederationClientException e) {
            if (e.getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Aborts the current test (reported as skipped) when the server's default
     * configuration has attachment search disabled.
     */
    protected void assumeAttachmentSearchEnabled() {
        Assumptions.assumeTrue(isAttachmentSearchEnabled(),
            "Attachment search is disabled by the server's default configuration (FEDERATION_SEARCH_ENABLE_ATTACHMENTS=false)");
    }

    protected void removeFromCleanup(List<String> list, String uuid) {
        list.remove(uuid);
    }
}
