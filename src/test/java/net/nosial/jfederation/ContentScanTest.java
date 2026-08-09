package net.nosial.jfederation;

import net.nosial.jfederation.enums.ClassificationFlag;
import net.nosial.jfederation.enums.EntityRelationshipType;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.SuggestedAction;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContentScanTest extends FederationClientTestBase {
    private static final String BENIGN_SAMPLE_TEXT = "This is a simple, benign message used for scanning tests.";

    private static FederationClient trainingClient;
    private static String trainingEntityUuid;
    private static final List<String> trainingReports = new java.util.ArrayList<>();
    private static final List<String> trainingEvidence = new java.util.ArrayList<>();

    @BeforeAll
    static void setUpTrainingData() {
        trainingClient = new FederationClient(serverEndpoint, serverAccessToken);
        trainingEntityUuid = trainingClient.pushEntity("scan-training.com", "scan_training");

        for (ClassificationFlag flag : ClassificationFlag.values()) {
            String text = "Training sample for " + flag.getValue() + " classification with enough words to train the Bayesian classifier effectively.";
            var submission = trainingClient.submitReport(trainingEntityUuid, text, IncidentType.OTHER, null, null);
            String reportUuid = submission.getReport().uuid();
            trainingReports.add(reportUuid);
            trainingEvidence.add(submission.getEvidence().uuid());
            trainingClient.closeReport(reportUuid, flag);
        }

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    static void tearDownTrainingData() {
        for (String uuid : trainingReports) {
            try { trainingClient.deleteReport(uuid); } catch (Exception ignored) {}
        }
        for (String uuid : trainingEvidence) {
            try { trainingClient.deleteEvidence(uuid); } catch (Exception ignored) {}
        }
        if (trainingEntityUuid != null) {
            try { trainingClient.deleteEntity(trainingEntityUuid); } catch (Exception ignored) {}
        }
        if (trainingClient != null) {
            trainingClient.close();
        }
    }

    @Test
    void testScanContentBasic() {
        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, null, null, null, null);

        assertNotNull(scanned);
        assertNotNull(scanned.getResolvedEntities());
        assertNull(scanned.getAuthorEntity());
    }

    @Test
    void testScanContentEmptyContent() {
        assertThrows(IllegalArgumentException.class,
            () -> client.scanContent("", null, null, null, null));
    }

    @Test
    void testScanContentWithAuthorByUuid() {
        String entityUuid = client.pushEntity("scan-author-uuid.com", "scan_author_uuid");
        createdEntities.add(entityUuid);

        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, entityUuid, null, null, null);

        assertNotNull(scanned);
        assertNotNull(scanned.getAuthorEntity());
        assertEquals(entityUuid, scanned.getAuthorEntity().getEntity().uuid());
    }

    @Test
    void testScanContentWithAuthorByAddress() {
        String host = "scan-author-address.com";
        String id = "scan_author_address";
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        String address = id + "@" + host;
        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, address, null, null, null);

        assertNotNull(scanned);
        assertNotNull(scanned.getAuthorEntity());
        assertEquals(entityUuid, scanned.getAuthorEntity().getEntity().uuid());
    }

    @Test
    void testScanContentWithInvalidAuthor() {
        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, "not-a-valid-identifier", null, null, null);
        assertNotNull(scanned);
        assertNull(scanned.getAuthorEntity());
    }

    @Test
    void testScanContentResolvesDomain() {
        String host = "scan-domain.com";
        String entityUuid = client.pushEntity(host);
        createdEntities.add(entityUuid);

        String text = "Check out " + host + " for more information. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertNotNull(scanned);
        assertTrue(scanned.getResolvedEntities().size() >= 1);

        boolean found = scanned.getResolvedEntities().stream()
            .anyMatch(e -> e.getEntity().uuid().equals(entityUuid));
        assertTrue(found, "Expected the pushed domain to be resolved from the content");
    }

    @Test
    void testScanContentResolvesUrl() {
        String host = "scan-url.com";
        String entityUuid = client.pushEntity(host);
        createdEntities.add(entityUuid);

        String text = "Visit https://" + host + "/path?q=test for details. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertNotNull(scanned);
        assertTrue(scanned.getResolvedEntities().size() >= 1);

        boolean found = scanned.getResolvedEntities().stream()
            .anyMatch(e -> e.getEntity().uuid().equals(entityUuid));
        assertTrue(found, "Expected the pushed domain to be resolved from the URL");
    }

    @Test
    void testScanContentResolvesEmail() {
        String host = "scan-email.com";
        String id = "scan_email_user";
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        String email = id + "@" + host;
        String text = "Contact me at " + email + " for more info. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertNotNull(scanned);
        assertTrue(scanned.getResolvedEntities().size() >= 1);

        boolean found = scanned.getResolvedEntities().stream()
            .anyMatch(e -> e.getEntity().uuid().equals(entityUuid));
        assertTrue(found, "Expected the pushed email entity to be resolved from the content");
    }

    @Test
    void testScanContentResolvesIpv4() {
        String ip = "192.168.55.42";
        String entityUuid = client.pushEntity(ip);
        createdEntities.add(entityUuid);

        String text = "Server is located at " + ip + " today. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertNotNull(scanned);
        assertTrue(scanned.getResolvedEntities().size() >= 1);

        boolean found = scanned.getResolvedEntities().stream()
            .anyMatch(e -> e.getEntity().uuid().equals(entityUuid));
        assertTrue(found, "Expected the pushed IPv4 entity to be resolved from the content");
    }

    @Test
    void testScanContentResolvesIpv6() {
        String ip = "2001:0db8:85a3:0000:0000:8a2e:0370:7334";
        String entityUuid = client.pushEntity(ip);
        createdEntities.add(entityUuid);

        String text = "The server address is " + ip + " please note it. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertNotNull(scanned);
        assertTrue(scanned.getResolvedEntities().size() >= 1);

        boolean found = scanned.getResolvedEntities().stream()
            .anyMatch(e -> e.getEntity().uuid().equals(entityUuid));
        assertTrue(found, "Expected the pushed IPv6 entity to be resolved from the content");
    }

    @Test
    void testScanContentResolvesMultipleEntities() {
        String domainHost = "scan-multi-domain.com";
        String emailHost = "scan-multi-email.com";
        String emailId = "multi_user";
        String ip = "203.0.113.10";

        String domainUuid = client.pushEntity(domainHost);
        String emailUuid = client.pushEntity(emailHost, emailId);
        String ipUuid = client.pushEntity(ip);
        createdEntities.add(domainUuid);
        createdEntities.add(emailUuid);
        createdEntities.add(ipUuid);

        String text = String.format("Visit %s and contact %s@%s or %s for details. %s",
            domainHost, emailId, emailHost, ip, BENIGN_SAMPLE_TEXT);

        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertNotNull(scanned);
        assertTrue(scanned.getResolvedEntities().size() >= 3);

        List<String> resolvedUuids = scanned.getResolvedEntities().stream()
            .map(e -> e.getEntity().uuid()).toList();
        assertTrue(resolvedUuids.contains(domainUuid));
        assertTrue(resolvedUuids.contains(emailUuid));
        assertTrue(resolvedUuids.contains(ipUuid));
    }

    @Test
    void testScanContentEntityPositions() {
        String host = "scan-position.com";
        String entityUuid = client.pushEntity(host);
        createdEntities.add(entityUuid);

        String prefix = "Before ";
        String text = prefix + host + " after";
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertNotNull(scanned);
        assertEquals(1, scanned.getResolvedEntities().size());

        ResolvedEntityPosition position = scanned.getResolvedEntities().get(0).getEntityPosition();
        assertNotNull(position);
        assertEquals(prefix.length(), position.offset());
        assertEquals(host.length(), position.length());
    }

    @Test
    void testScanContentWithTopK() {
        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, null, 1, null, null);
        assertNotNull(scanned);
        assertNotNull(scanned.getResolvedEntities());
    }

    @Test
    void testScanContentWithThreshold() {
        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, null, null, 0.5f, null);
        assertNotNull(scanned);
        assertNotNull(scanned.getResolvedEntities());
        assertTrue(scanned.riskScore() >= 0.0);
    }

    @Test
    void testScanContentWithTopKAndThreshold() {
        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, null, 2, 0.25f, null);
        assertNotNull(scanned);
        assertNotNull(scanned.getResolvedEntities());
    }

    @Test
    void testScanContentWithMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "ContentScanTest");
        metadata.put("batch_id", "scan_" + UUID.randomUUID().toString().substring(0, 8));

        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, null, null, null, metadata);
        assertNotNull(scanned);
        assertNotNull(scanned.getResolvedEntities());
    }

    @Test
    void testScanContentClassificationMayBeNullWhenUntrained() {
        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, null, null, null, null);

        assertNotNull(scanned);
        ContentClassification classification = scanned.getClassification();

        if (classification != null) {
            assertNotNull(classification.classificationFlag());
            assertTrue(classification.confidence() >= 0.0);
        }
    }

    @Test
    void testScanContentAuthorWithActiveBlacklist() {
        String host = "scan-blacklisted-author.com";
        String id = "scan_blacklisted_author";
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Spam evidence for author", "Test note", "spam", false);
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);

        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, entityUuid, null, null, null);

        assertNotNull(scanned);
        assertNotNull(scanned.getAuthorEntity());
        assertTrue(scanned.getAuthorEntity().getActiveBlacklists().size() >= 1);
    }

    @Test
    void testScanContentSuggestedActionForHighRisk() {
        String host = "scan-high-risk.com";
        String id = "scan_high_risk";
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Malware evidence for high risk", "Test note", "malware", false);
        createdEvidenceRecords.add(evidenceUuid);

        client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.MALWARE, null);

        ScannedContent scanned = client.scanContent("Malicious content with malware indicators and dangerous payload signatures", entityUuid, null, null, null);

        assertNotNull(scanned);
    }

    @Test
    void testScanContentEntityPositionsForUrlEmailAndIps() {
        String domainHost = "scan-position-domain.com";
        String emailHost = "scan-position-email.com";
        String emailId = "position_user";
        String ipv4 = "203.0.113.45";
        String ipv6 = "2001:0db8:85a3:0000:0000:8a2e:0370:7335";

        String domainUuid = client.pushEntity(domainHost);
        String emailUuid = client.pushEntity(emailHost, emailId);
        String ipv4Uuid = client.pushEntity(ipv4);
        String ipv6Uuid = client.pushEntity(ipv6);
        createdEntities.add(domainUuid);
        createdEntities.add(emailUuid);
        createdEntities.add(ipv4Uuid);
        createdEntities.add(ipv6Uuid);

        String urlPrefix = "Check ";
        String url = "https://" + domainHost + "/path";
        String emailPrefix = " or email ";
        String email = emailId + "@" + emailHost;
        String ipv4Prefix = " or server ";
        String ipv6Prefix = " or v6 ";

        String text = urlPrefix + url + emailPrefix + email + ipv4Prefix + ipv4 + ipv6Prefix + ipv6;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertNotNull(scanned);
        assertTrue(scanned.getResolvedEntities().size() >= 4);

        Map<String, Integer> expected = new HashMap<>();
        expected.put(domainUuid, urlPrefix.length());
        expected.put(emailUuid, (urlPrefix + url + emailPrefix).length());
        expected.put(ipv4Uuid, (urlPrefix + url + emailPrefix + email + ipv4Prefix).length());
        expected.put(ipv6Uuid, (urlPrefix + url + emailPrefix + email + ipv4Prefix + ipv4 + ipv6Prefix).length());

        for (ResolvedEntity re : scanned.getResolvedEntities()) {
            String uuid = re.getEntity().uuid();
            ResolvedEntityPosition position = re.getEntityPosition();
            assertNotNull(position, "Expected position for resolved entity " + uuid);
            assertTrue(expected.containsKey(uuid), "Unexpected resolved entity");
            assertEquals(expected.get(uuid).intValue(), position.offset(), "Wrong offset for entity " + uuid);
        }
    }

    @Test
    void testScanContentDoesNotResolveUnknownEntities() {
        String unknownHost = "unknown-domain-" + UUID.randomUUID().toString().substring(0, 8) + ".test";
        String text = "Visit " + unknownHost + " for details. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertNotNull(scanned);
        for (ResolvedEntity re : scanned.getResolvedEntities()) {
            assertNotEquals(unknownHost, re.getEntity().host(), "Unknown domain should not be resolved as an entity");
        }
    }

    @Test
    void testScanContentAnonymousAccess() {
        FederationClient anonymousClient = createAnonymousClient();

        try {
            ScannedContent scanned = anonymousClient.scanContent(BENIGN_SAMPLE_TEXT, null, null, null, null);
            assertNotNull(scanned);
            assertNotNull(scanned.getResolvedEntities());
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401,
                "Expected 400 or 401 when scanning is not public");
        }
    }

    @Test
    void testScanContentUnauthorizedOperator() {
        OperatorCreated operatorUuidCreated = client.createOperator("scan-no-client-perm");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        client.setManagementPermissions(operatorUuid, false);
        client.setOperatorPermissions(operatorUuid, false);
        client.setClientPermissions(operatorUuid, false);

        String token = operatorUuidCreated.accessToken();
        FederationClient restrictedClient = new FederationClient(serverEndpoint, token);

        assertThrows(FederationClientException.class,
            () -> restrictedClient.scanContent(BENIGN_SAMPLE_TEXT, null, null, null, null));
    }

    @Test
    void testScanContentWithTrainingAndClassification() {
        for (ClassificationFlag flag : ClassificationFlag.values()) {
            String text = "Test sample for " + flag.getValue() + " classification analysis with appropriate vocabulary and phrasing.";
            ScannedContent scanned = client.scanContent(text, null, null, null, null);

            assertNotNull(scanned);
            assertNotNull(scanned.getResolvedEntities());
        }
    }

    @Test
    void testScanContentSuggestedActionNullForCleanContent() {
        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, null, null, null, null);

        assertNull(scanned.suggestedAction());
        assertTrue(scanned.riskScore() >= 0.0);
        assertTrue(scanned.riskScore() <= 100.0);
    }

    @Test
    void testScanContentResolvedEntityBlacklistContributesToRiskScore() {
        String host = "scan-resolved-blacklist.com";
        String entityUuid = client.pushEntity(host);
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Malware evidence for resolved entity", "Test note", "malware", false);
        createdEvidenceRecords.add(evidenceUuid);

        client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.MALWARE, null);

        String text = "Visit " + host + " for updates. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        boolean found = false;
        for (ResolvedEntity re : scanned.getResolvedEntities()) {
            if (re.getEntity().uuid().equals(entityUuid)) {
                found = true;
                assertTrue(re.getActiveBlacklists().size() >= 1);
                break;
            }
        }
        assertTrue(found, "Expected the blacklisted entity to be resolved");

        Map<String, Double> results = scanned.scanResults();
        assertNotNull(results);
        assertTrue(results.containsKey("NAMED_ENTITY_PERMANENTLY_BLACKLISTED"));
        assertTrue(results.get("NAMED_ENTITY_PERMANENTLY_BLACKLISTED") < 0.0);
    }

    @Test
    void testScanContentSuggestedActionForTemporarilyBlacklistedAuthor() {
        String host = "scan-temp-blacklist-author.com";
        String id = "scan_temp_blacklist_author";
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Temporary spam evidence", "Test note", "spam", false);
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);

        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, entityUuid, null, null, null);

        assertNotNull(scanned.getAuthorEntity());
        assertTrue(scanned.getAuthorEntity().getActiveBlacklists().size() >= 1);
        assertEquals(SuggestedAction.TEMPORARILY_BLOCK_ENTITY, scanned.suggestedAction());
        assertNotNull(scanned.suggestedLiftTimestamp());
        assertTrue(scanned.suggestedLiftTimestamp() >= expires - 5);
        assertTrue(scanned.suggestedLiftTimestamp() <= expires + 5);
    }

    @Test
    void testScanContentSuggestedActionForPermanentlyBlacklistedAuthor() {
        String host = "scan-perm-blacklist-author.com";
        String id = "scan_perm_blacklist_author";
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Permanent spam evidence", "Test note", "spam", false);
        createdEvidenceRecords.add(evidenceUuid);

        client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, null);

        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, entityUuid, null, null, null);

        assertNotNull(scanned.getAuthorEntity());
        assertTrue(scanned.getAuthorEntity().getActiveBlacklists().size() >= 1);
        assertEquals(SuggestedAction.PERMANENTLY_BLOCK_ENTITY, scanned.suggestedAction());
        assertNull(scanned.suggestedLiftTimestamp());
    }

    @Test
    void testScanContentMultipleAuthorBlacklistsPermanentWins() {
        String host = "scan-multi-blacklist-author.com";
        String id = "scan_multi_blacklist_author";
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        String tempEvidence = client.submitEvidence(entityUuid, "Temporary evidence", "Test note", "spam", false);
        createdEvidenceRecords.add(tempEvidence);

        String permEvidence = client.submitEvidence(entityUuid, "Permanent evidence", "Test note", "malware", false);
        createdEvidenceRecords.add(permEvidence);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        client.blacklistEntity(entityUuid, tempEvidence, IncidentType.SPAM, expires);
        client.blacklistEntity(entityUuid, permEvidence, IncidentType.MALWARE, null);

        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, entityUuid, null, null, null);

        assertNotNull(scanned.getAuthorEntity());
        assertEquals(2, scanned.getAuthorEntity().getActiveBlacklists().size());
        assertEquals(SuggestedAction.PERMANENTLY_BLOCK_ENTITY, scanned.suggestedAction());
    }

    @Test
    void testScanContentAuthorParentBlacklistAffectsSuggestedAction() {
        String parentHost = "scan-author-parent.com";
        String childHost = "child.scan-author-parent.com";
        String childId = "scan_author_child";

        String parentUuid = client.pushEntity(parentHost);
        String childUuid = client.pushEntity(childHost, childId);
        createdEntities.add(parentUuid);
        createdEntities.add(childUuid);

        client.setEntityRelationship(childUuid, parentUuid, EntityRelationshipType.CHILD);

        String evidenceUuid = client.submitEvidence(parentUuid, "Parent is malicious", "Test note", "malware", false);
        createdEvidenceRecords.add(evidenceUuid);

        client.blacklistEntity(parentUuid, evidenceUuid, IncidentType.MALWARE, null);

        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, childUuid, null, null, null);

        assertNotNull(scanned.getAuthorEntity());
        assertNotNull(scanned.getAuthorEntity().getParentEntity());
        assertEquals(parentUuid, scanned.getAuthorEntity().getParentEntity().getEntity().uuid());
        assertTrue(scanned.getAuthorEntity().getParentEntity().getActiveBlacklists().size() >= 1);

        assertEquals(SuggestedAction.BLOCK_CONTENT, scanned.suggestedAction());
        assertEquals(100.0, scanned.riskScore(), 0.001);

        Map<String, Double> results = scanned.scanResults();
        assertNotNull(results);
        assertTrue(results.containsKey("AUTHOR_PARENT_PERMANENTLY_BLACKLISTED"));
        assertTrue(results.get("AUTHOR_PARENT_PERMANENTLY_BLACKLISTED") < 0.0);
    }

    @Test
    void testScanContentResolvedEntityParentBlacklistContributesToRiskScore() {
        String parentHost = "scan-resolved-parent.com";
        String childHost = "child.scan-resolved-parent.com";

        String parentUuid = client.pushEntity(parentHost);
        String childUuid = client.pushEntity(childHost);
        createdEntities.add(parentUuid);
        createdEntities.add(childUuid);

        client.setEntityRelationship(childUuid, parentUuid, EntityRelationshipType.CHILD);

        String evidenceUuid = client.submitEvidence(parentUuid, "Parent malware evidence", "Test note", "malware", false);
        createdEvidenceRecords.add(evidenceUuid);

        client.blacklistEntity(parentUuid, evidenceUuid, IncidentType.MALWARE, null);

        String text = "Visit " + childHost + " for updates. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        boolean found = false;
        for (ResolvedEntity re : scanned.getResolvedEntities()) {
            if (re.getEntity().uuid().equals(childUuid)) {
                found = true;
                assertNotNull(re.getParentEntity());
                assertEquals(parentUuid, re.getParentEntity().getEntity().uuid());
                assertTrue(re.getParentEntity().getActiveBlacklists().size() >= 1);
                break;
            }
        }
        assertTrue(found, "Expected the child entity to be resolved");

        Map<String, Double> results = scanned.scanResults();
        assertNotNull(results);
        assertTrue(results.containsKey("NAMED_ENTITY_PARENT_PERMANENTLY_BLACKLISTED"));
        assertTrue(results.get("NAMED_ENTITY_PARENT_PERMANENTLY_BLACKLISTED") < 0.0);
    }

    @Test
    void testScanContentResolvedEntityTemporaryBlacklistContributes() {
        String host = "scan-temp-blacklist-resolved.com";
        String entityUuid = client.pushEntity(host);
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Temporary spam evidence", "Test note", "spam", false);
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);

        String text = "Visit " + host + " for updates. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        boolean found = false;
        for (ResolvedEntity re : scanned.getResolvedEntities()) {
            if (re.getEntity().uuid().equals(entityUuid)) {
                found = true;
                assertTrue(re.getActiveBlacklists().size() >= 1);
                assertNotNull(re.getActiveBlacklists().get(0).expires());
                break;
            }
        }
        assertTrue(found, "Expected the blacklisted entity to be resolved");

        Map<String, Double> results = scanned.scanResults();
        assertNotNull(results);
        assertTrue(results.containsKey("NAMED_ENTITY_BLACKLISTED"));
        assertTrue(results.get("NAMED_ENTITY_BLACKLISTED") < 0.0);
        assertEquals(0.0, results.get("NAMED_ENTITY_PERMANENTLY_BLACKLISTED"), 0.001);
    }

    @Test
    void testScanContentRiskScoreWithPermanentlyBlacklistedAuthor() {
        String host = "scan-risk-perm-author.com";
        String id = "scan_risk_perm_author";
        String entityUuid = client.pushEntity(host, id);
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Malware evidence", "Test note", "malware", false);
        createdEvidenceRecords.add(evidenceUuid);

        client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.MALWARE, null);

        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, entityUuid, null, null, null);

        assertEquals(SuggestedAction.PERMANENTLY_BLOCK_ENTITY, scanned.suggestedAction());
        assertEquals(100.0, scanned.riskScore(), 0.001);
    }

    @Test
    void testScanContentRiskScoreWithBlacklistedNamedEntity() {
        String host = "scan-risk-resolved.com";
        String entityUuid = client.pushEntity(host);
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Malware evidence", "Test note", "malware", false);
        createdEvidenceRecords.add(evidenceUuid);

        client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.MALWARE, null);

        String text = "Visit " + host + " for updates. " + BENIGN_SAMPLE_TEXT;
        ScannedContent scanned = client.scanContent(text, null, null, null, null);

        assertTrue(scanned.riskScore() >= 60.0);

        Map<String, Double> results = scanned.scanResults();
        assertNotNull(results);
        assertTrue(results.containsKey("NAMED_ENTITY_PERMANENTLY_BLACKLISTED"));
        assertTrue(results.get("NAMED_ENTITY_PERMANENTLY_BLACKLISTED") < 0.0);
    }

    @Test
    void testScanContentScanResultsContainAllRuleKeys() {
        String entityUuid = client.pushEntity("scan-rule-keys.com", "scan_rule_keys");
        createdEntities.add(entityUuid);

        ScannedContent scanned = client.scanContent(BENIGN_SAMPLE_TEXT, entityUuid, null, null, null);
        Map<String, Double> results = scanned.scanResults();
        assertNotNull(results);

        String[] expectedKeys = {
            "CLASSIFICATION_NORMAL", "CLASSIFICATION_SUSPICIOUS", "CLASSIFICATION_MALICIOUS",
            "NAMED_ENTITY_PERMANENTLY_BLACKLISTED", "NAMED_ENTITY_BLACKLISTED",
            "NAMED_ENTITY_PARENT_PERMANENTLY_BLACKLISTED", "AUTHOR_PARENT_PERMANENTLY_BLACKLISTED"
        };
        for (String key : expectedKeys) {
            assertTrue(results.containsKey(key), "Missing scanning rule: " + key);
            assertNotNull(results.get(key));
        }
    }

    @Test
    void testGetSpecification() {
        var spec = client.getSpecification();
        assertNotNull(spec);
        assertTrue(spec.has("openapi"));
        assertTrue(spec.has("info"));
        assertTrue(spec.has("paths"));
        assertTrue(spec.get("paths").has("/scan"));
    }
}
