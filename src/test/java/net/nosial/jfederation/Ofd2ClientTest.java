package net.nosial.jfederation;

import net.nosial.jfederation.enums.ContentType;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.NamedEntityType;
import net.nosial.jfederation.records.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Client to server tests of the methods and members introduced by OFD 2.0 (FederationLib 2.0.0).
 */
class Ofd2ClientTest extends FederationClientTestBase {

    private String createOperatorUuid(String prefix) {
        String uuid = client.createOperator(prefix + "_" + UUID.randomUUID().toString().substring(0, 5)).uuid();
        createdOperators.add(uuid);
        return uuid;
    }

    private static String uniqueTelephoneNumber() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int exchange;
        do {
            exchange = random.nextInt(2, 9) * 100 + random.nextInt(20, 100);
        } while (exchange == 555);
        return "1650" + exchange + String.format("%04d", random.nextInt(0, 10000));
    }

    @Test
    void testServerImplementsOfd2() {
        ServerInformation info = client.getServerInformation();

        assertEquals(2, info.getApiMajorVersion(), "The server should implement OFD 2.x, reported " + info.apiVersion());
        assertTrue(info.scanContentTypes().contains(ContentType.TEXT));
        assertEquals(List.of(ContentType.values()), info.scanContentTypes(), "Every content type is interpreted by scanning");
        assertTrue(info.concerns().containsAll(List.of(IncidentType.values())), "Every incident type can be assessed by default");
        assertTrue(info.concerns().containsAll(info.defaultConcerns()), "The default concerns are a subset of the concerns");
        assertFalse(info.defaultConcerns().contains(IncidentType.ADVERTISING));
        assertFalse(info.defaultConcerns().contains(IncidentType.TRACKING));
        assertTrue(info.isUrlUploadsAllowed(), "URL uploads are enabled by default");
        assertNotNull(info.collectsPublicScanContent());
    }

    @Test
    void testUpdateOperatorPermissionsReturnsTheUpdatedPermissions() {
        String uuid = createOperatorUuid("ofd2_perms");

        OperatorPermissions permissions = client.updateOperatorPermissions(uuid, null, true, null);
        assertEquals(new OperatorPermissions(true, true, false), permissions, "Management permissions inherit client permissions");

        // Groups given as null are left unchanged
        permissions = client.updateOperatorPermissions(uuid, null, null, true);
        assertEquals(new OperatorPermissions(true, true, true), permissions);
        assertEquals(permissions, client.getOperator(uuid).permissions());

        // Revoking both at once is not a conflict
        assertEquals(new OperatorPermissions(false, false, true), client.updateOperatorPermissions(uuid, false, false, null));
    }

    @Test
    void testRevokingClientPermissionsFromManagerConflicts() {
        String uuid = createOperatorUuid("ofd2_conflict");
        client.setManagementPermissions(uuid, true);

        expectRequestFailure(() -> client.updateOperatorPermissions(uuid, false, null, null), 409);
        expectRequestFailure(() -> client.setClientPermissions(uuid, false), 409);
        assertTrue(client.getOperator(uuid).hasManagementPermissions(), "A rejected update leaves the permissions unchanged");
    }

    @Test
    void testUpdatePermissionsRequiresOperatorPermissions() {
        String target = createOperatorUuid("ofd2_perm_target");
        FederationClient manager = createLimitedOperator("ofd2_perm_mgr", true, false, true);

        expectRequestFailure(() -> manager.updateOperatorPermissions(target, true, null, null), 403);
        expectRequestFailure(() -> createAnonymousClient().updateOperatorPermissions(target, true, null, null), new int[]{401, 403});
    }

    @Test
    void testUpdateOperatorConfigurationLeavesUnsuppliedSettingsUnchanged() {
        String uuid = createOperatorUuid("ofd2_config");

        OperatorConfiguration configuration = client.updateOperatorConfiguration(uuid, true, null, null);
        assertEquals(new OperatorConfiguration(true, false, false), configuration);

        configuration = client.updateOperatorConfiguration(uuid, null, true, null);
        assertEquals(new OperatorConfiguration(true, true, false), configuration);
        assertEquals(configuration, client.getOperator(uuid).configuration());

        client.setAutoAssign(uuid, false);
        assertEquals(new OperatorConfiguration(false, false, false), client.getOperator(uuid).configuration());
    }

    @Test
    void testOperatorChangesItsOwnConfiguration() {
        FederationClient self = createLimitedOperator("ofd2_self_config", false, false, true);
        String selfUuid = self.getSelf().uuid();

        assertEquals(new OperatorConfiguration(true, false, false), self.updateOperatorConfiguration(selfUuid, true, null, null));
        assertNotNull(self.getSelf().configuration(), "An operator receives its own configuration");

        // Changing the configuration of another operator requires operator permissions
        String other = createOperatorUuid("ofd2_other_config");
        expectRequestFailure(() -> self.updateOperatorConfiguration(other, true, null, null), 403);
    }

    @Test
    void testConfigurationIsHiddenFromOtherOperators() {
        String target = createOperatorUuid("ofd2_hidden");
        client.setAutoAssign(target, true);

        FederationClient viewer = createLimitedOperator("ofd2_viewer", false, false, true);
        OperatorRecord seen = viewer.getOperator(target);
        assertNull(seen.configuration(), "The configuration is only disclosed to the operator and to operators with operator permissions");
        assertFalse(seen.isConfigurationVisible());
        assertNull(createAnonymousClient().getOperator(target).configuration());

        assertTrue(client.getOperator(target).isAutoAssigned());
    }

    @Test
    void testRootConfigurationCannotBeModified() {
        String rootUuid = client.getSelf().uuid();
        expectRequestFailure(() -> client.setBot(rootUuid, true), 403);
    }

    @Test
    void testBotEvidenceIsAutomatedAndConfidential() {
        FederationClient bot = createLimitedOperator("ofd2_bot", false, false, true);
        String botUuid = bot.getSelf().uuid();
        client.setBot(botUuid, true);
        assertTrue(client.getOperator(botUuid).isBot());

        String entityUuid = createSecurityEntity();
        String evidenceUuid = bot.submitEvidence(entityUuid, "Automated finding", null, null, false);
        createdEvidenceRecords.add(evidenceUuid);

        EvidenceRecord evidence = client.getEvidenceRecord(evidenceUuid);
        assertTrue(evidence.automated());
        assertTrue(evidence.confidential(), "Automated evidence is created confidential");

        List<String> automated = client.listEvidence(1, 100, true, "AUTOMATED").stream().map(EvidenceRecord::uuid).toList();
        assertTrue(automated.contains(evidenceUuid));
        List<String> notAutomated = client.listEvidence(1, 100, true, "NOT_AUTOMATED").stream().map(EvidenceRecord::uuid).toList();
        assertFalse(notAutomated.contains(evidenceUuid));

        // A manager can make automated evidence public afterwards
        client.updateEvidenceConfidentiality(evidenceUuid, false);
        assertFalse(client.getEvidenceRecord(evidenceUuid).confidential());

        String humanEvidence = createSecurityEvidence(entityUuid);
        assertFalse(client.getEvidenceRecord(humanEvidence).automated());
    }

    @Test
    void testSubmitEvidenceWithContentTypeAndContext() {
        String entityUuid = createSecurityEntity();
        Map<String, String> context = Map.of("subject", "Your invoice", "sender", "billing@example.com");

        String evidenceUuid = client.submitEvidence(entityUuid, "Pay the attached invoice", null, null, false, null, null,
            ContentType.EMAIL, context);
        createdEvidenceRecords.add(evidenceUuid);

        EvidenceRecord evidence = client.getEvidenceRecord(evidenceUuid);
        assertSame(ContentType.EMAIL, evidence.contentType());
        assertEquals(context, evidence.getContext());

        String textUuid = createSecurityEvidence(entityUuid);
        assertSame(ContentType.TEXT, client.getEvidenceRecord(textUuid).contentType());
    }

    @Test
    void testSubmitEvidenceRejectsInvalidContext() {
        String entityUuid = createSecurityEntity();

        // A WEB_PAGE requires the url key, and keys must be defined for the content type
        expectRequestFailure(() -> client.submitEvidence(entityUuid, "Page text", null, null, false, null, null,
            ContentType.WEB_PAGE, Map.of("title", "Missing URL")), 400);
        expectRequestFailure(() -> client.submitEvidence(entityUuid, "Body", null, null, false, null, null,
            ContentType.EMAIL, Map.of("region", "US")), 400);
    }

    @Test
    void testReportEvidenceCarriesContentType() {
        String entityUuid = createSecurityEntity();
        ContentInput input = new ContentInput("https://login.example.com/verify", ContentType.URL);

        ReportSubmission submission = client.submitReport(entityUuid, input, IncidentType.PHISHING);
        createdReports.add(submission.getReport().uuid());
        submission.getEvidence().forEach(evidence -> createdEvidenceRecords.add(evidence.uuid()));

        assertSame(ContentType.URL, client.getEvidenceRecord(submission.getEvidence().getFirst().uuid()).contentType());
    }

    @Test
    void testScanContentWithConcernsAndContentTypes() {
        ServerInformation info = client.getServerInformation();
        IncidentType concern = info.defaultConcerns().getFirst();

        ScannedContent scanned = client.scanContent(new ContentInput("Hello, how are you today?"), null, null, null, List.of(concern));
        assertNotNull(scanned.detections());
        for (Detection detection : scanned.detections()) {
            assertSame(concern, detection.concern(), "Detections only relate to the requested concerns");
        }

        List<ContentInput> inputs = List.of(
            new ContentInput("Meeting moved to 3pm"),
            new ContentInput("example.com", ContentType.HOSTNAME, Map.of("query_type", "A")),
            new ContentInput("https://example.com/page", ContentType.URL)
        );
        ScannedContent multiple = client.scanContent(inputs, null, null, null, null);
        for (Detection detection : multiple.detections()) {
            assertTrue(detection.contentIndex() >= 0 && detection.contentIndex() < inputs.size());
        }
    }

    @Test
    void testScanContentRejectsInvalidTypedContent() {
        expectRequestFailure(() -> client.scanContent(new ContentInput("not a hostname!", ContentType.HOSTNAME)), 400);
        expectRequestFailure(() -> client.scanContent(new ContentInput("not/an absolute url", ContentType.URL)), 400);
        expectRequestFailure(() -> client.scanContent(new ContentInput("Page text", ContentType.WEB_PAGE, Map.of("title", "No URL"))), 400);
    }

    @Test
    void testDetectionForBlacklistOfRequestedConcernOnly() {
        String host = "ofd2-detect-" + UUID.randomUUID().toString().substring(0, 8) + ".com";
        String entityUuid = client.pushEntity(host);
        createdEntities.add(entityUuid);
        String blacklistUuid = client.blacklistEntity(entityUuid, IncidentType.MALWARE);
        createdBlacklistRecords.add(blacklistUuid);

        ScannedContent malware = client.scanContent(new ContentInput("Download it from https://" + host + "/file"), null, null, null, List.of(IncidentType.MALWARE));
        assertTrue(malware.detections().stream().anyMatch(d -> d.concern() == IncidentType.MALWARE),
            "An active blacklist record of a requested concern is a detection");

        ScannedContent spam = client.scanContent(new ContentInput("Download it from https://" + host + "/file"), null, null, null, List.of(IncidentType.SPAM));
        assertTrue(spam.detections().stream().noneMatch(d -> d.concern() == IncidentType.MALWARE),
            "Concerns that were not requested make no detections");
    }

    @Test
    void testQueryEntityWithConcerns() {
        String entityUuid = createSecurityEntity();
        String blacklistUuid = client.blacklistEntity(entityUuid, IncidentType.MALWARE);
        createdBlacklistRecords.add(blacklistUuid);

        EntityQueryResult malware = client.queryEntity(entityUuid, List.of(IncidentType.MALWARE));
        assertNotNull(malware.suggestedAction(), "A blacklist record of a requested concern suggests an action");

        EntityQueryResult spam = client.queryEntity(entityUuid, List.of(IncidentType.SPAM));
        assertNull(spam.suggestedAction(), "Blacklist records of other incident types do not suggest blocking");
        assertTrue(spam.activeBlacklists().stream().anyMatch(record -> record.uuid().equals(blacklistUuid)),
            "Concerns do not filter the active blacklist records");
    }

    @Test
    void testBlacklistWithoutReportAndWithReportAboutAnotherEntity() {
        String entityA = createSecurityEntity();
        String entityB = createSecurityEntity();

        String withoutReport = client.blacklistEntity(entityA, IncidentType.SPAM, (int) (System.currentTimeMillis() / 1000 + 3600));
        createdBlacklistRecords.add(withoutReport);
        assertNull(client.getBlacklistRecord(withoutReport).reportUuid());

        String reportForB = createReportForEntity(entityB);
        String withOtherReport = client.blacklistEntity(entityA, reportForB, IncidentType.SPAM);
        createdBlacklistRecords.add(withOtherReport);
        BlacklistRecord record = client.getBlacklistRecord(withOtherReport);
        assertEquals(entityA, record.entityUuid());
        assertEquals(reportForB, record.reportUuid());
    }

    @Test
    void testBlacklistTypeFilters() {
        String entityUuid = createSecurityEntity();
        String advertising = client.blacklistEntity(entityUuid, IncidentType.ADVERTISING);
        String tracking = client.blacklistEntity(entityUuid, IncidentType.TRACKING);
        String spam = client.blacklistEntity(entityUuid, IncidentType.SPAM);
        createdBlacklistRecords.addAll(List.of(advertising, tracking, spam));

        List<String> listed = client.listBlacklistRecords(1, 100, false, null, null, null, List.of(IncidentType.ADVERTISING, IncidentType.TRACKING))
            .stream().map(BlacklistRecord::uuid).toList();
        assertTrue(listed.containsAll(List.of(advertising, tracking)));
        assertFalse(listed.contains(spam));

        List<BlacklistRecord> searched = client.searchBlacklist(entityUuid, 1, 50, null, null, null, List.of(IncidentType.ADVERTISING));
        List<String> searchedUuids = searched.stream().map(BlacklistRecord::uuid).toList();
        assertTrue(searchedUuids.contains(advertising));
        assertFalse(searchedUuids.contains(tracking));
        assertFalse(searchedUuids.contains(spam));
        assertTrue(searched.stream().allMatch(record -> record.type() == IncidentType.ADVERTISING));
    }

    @Test
    void testAuditLogEntriesCarryTheReport() {
        String entityUuid = createSecurityEntity();
        String reportUuid = createReportForEntity(entityUuid);

        List<AuditLog> entries = client.listEntityAuditLogs(entityUuid, 1, 100);
        assertTrue(entries.stream().anyMatch(entry -> reportUuid.equals(entry.reportUuid())),
            "The entry of the report submission references the report");
    }

    @Test
    void testEntityAddressesArePercentEncodedInPaths() {
        String host = "ofd2-encode-" + UUID.randomUUID().toString().substring(0, 8) + ".com";

        // '%' and '+' are permitted in a local part, and would be misread if sent unencoded in a path
        for (String id : List.of("user+tag", "user%41b")) {
            String entityUuid = client.pushEntity(host, id);
            createdEntities.add(entityUuid);

            EntityRecord record = client.getEntityRecord(id + "@" + host);
            assertEquals(entityUuid, record.uuid());
            assertEquals(id, record.id());
            assertEquals(entityUuid, client.queryEntity(id + "@" + host).entityRecord().uuid());
            assertNotNull(client.listEntityAuditLogs(id + "@" + host, 1, 10));
        }
    }

    @Test
    void testSortValuesAreSentUppercase() {
        // OFD 2.0 defines uppercase ordering values, the client uppercases what it is given
        List<OperatorRecord> lowercase = client.listOperators(1, 10, null, "created", "asc");
        List<OperatorRecord> uppercase = client.listOperators(1, 10, null, "CREATED", "ASC");
        assertEquals(uppercase.stream().map(OperatorRecord::uuid).toList(), lowercase.stream().map(OperatorRecord::uuid).toList());
    }

    @Test
    void testTelephoneEntitiesAreHiddenFromUnauthenticatedRequests() {
        String number = uniqueTelephoneNumber();
        String entityUuid = client.pushEntity(EntityRecord.TELEPHONE_ENTITY_HOST, number);
        createdEntities.add(entityUuid);

        EntityRecord record = client.getEntityRecord(number + "@" + EntityRecord.TELEPHONE_ENTITY_HOST);
        assertEquals(entityUuid, record.uuid());
        assertTrue(record.isTelephone());

        // Telephone entities are not public by default: an unauthenticated request can't resolve one by UUID
        assertFalse(client.getServerInformation().publicTelephoneEntities());
        FederationClient anonymous = createAnonymousClient();
        expectRequestFailure(() -> anonymous.getEntityRecord(entityUuid), 404);
        assertEquals(entityUuid, anonymous.getEntityRecord(number + "@" + EntityRecord.TELEPHONE_ENTITY_HOST).uuid());

        // Evidence about a telephone entity is always confidential
        String evidenceUuid = client.submitEvidence(entityUuid, "A private text message", null, null, false);
        createdEvidenceRecords.add(evidenceUuid);
        assertTrue(client.getEvidenceRecord(evidenceUuid).confidential());
    }

    @Test
    void testScanResolvesTelephoneNumbers() {
        String number = uniqueTelephoneNumber();
        String written = "+1 " + number.substring(1, 4) + "-" + number.substring(4, 7) + "-" + number.substring(7);

        ScannedContent scanned = client.scanContent("Call me at " + written + " for the prize");
        ResolvedEntity telephone = scanned.getResolvedEntities().stream()
            .filter(entity -> entity.getEntity().isTelephone())
            .findFirst().orElseThrow(() -> new AssertionError("The number should be resolved as a telephone entity"));
        createdEntities.add(telephone.getEntity().uuid());

        assertEquals(number, telephone.getEntity().id());
        assertSame(NamedEntityType.TELEPHONE, telephone.getEntityPosition().type());
    }

    @Test
    void testUrlUploadsRejectNonPublicAddresses() {
        String evidenceUuid = createSecurityEvidence(createSecurityEntity());

        // URL uploads from non-public addresses are rejected by default, guarding against server-side request forgery
        for (String url : List.of("http://127.0.0.1/file.txt", "http://10.0.0.1/", "http://169.254.169.254/latest/meta-data/", "http://[::1]/")) {
            expectRequestFailure(() -> client.uploadFileAttachmentByUrl(evidenceUuid, url), 400);
        }
    }
}
