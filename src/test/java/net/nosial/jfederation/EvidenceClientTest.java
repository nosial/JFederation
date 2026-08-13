package net.nosial.jfederation;

import net.nosial.jfederation.enums.ClassificationFlag;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.EvidenceRecord;
import net.nosial.jfederation.records.OperatorCreated;
import net.nosial.jfederation.records.OperatorRecord;
import net.nosial.jfederation.records.ReportSubmission;
import net.nosial.jfederation.records.UploadResult;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EvidenceClientTest extends FederationClientTestBase {

    @Test
    void testSubmitEvidence() {
        String entityUuid = client.pushEntity("example.com", "alice123_" + randomUuid().substring(0, 8));
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Unauthorized Login Detected", "Automatic Detection by System", "unauthorized_login");
        createdEvidenceRecords.add(evidenceUuid);
        assertNotNull(evidenceUuid);

        OperatorRecord self = client.getSelf();
        EvidenceRecord rec = client.getEvidenceRecord(evidenceUuid);
        assertEquals("Unauthorized Login Detected", rec.textContent());
        assertEquals("Automatic Detection by System", rec.note());
        assertEquals("unauthorized_login", rec.tag());
        assertFalse(rec.confidential());
        assertEquals(entityUuid, rec.entityUuid());
        assertEquals(self.uuid(), rec.operatorUuid());
    }

    @Test
    void testSubmitEvidenceUnauthorized() {
        OperatorCreated basicOpCreated = client.createOperator("Basic Operator_" + randomUuid().substring(0, 8));
        String basicOpUuid = basicOpCreated.uuid();
        createdOperators.add(basicOpUuid);
        client.setManagementPermissions(basicOpUuid, false);
        client.setOperatorPermissions(basicOpUuid, false);
        client.setClientPermissions(basicOpUuid, false);

        FederationClient basicClient = new FederationClient(serverEndpoint, basicOpCreated.accessToken());

        String entityUuid = client.pushEntity("example.com", "alice123");
        createdEntities.add(entityUuid);

        try {
            basicClient.submitEvidence(entityUuid, "Test text", "Test note", "test_tag");
            fail("Expected FederationClientException");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        basicClient.close();
    }

    @Test
    void testListEvidence() {
        String entityUuid = client.pushEntity("list-evidence-" + randomUuid().substring(0, 8) + ".com", "alice123");
        createdEntities.add(entityUuid);

        Set<String> created = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            String uuid = client.submitEvidence(entityUuid, "Evidence " + i, "Note " + i, "tag" + (i % 3));
            created.add(uuid);
            createdEvidenceRecords.add(uuid);
        }

        Set<String> allFound = new HashSet<>();
        int page = 1;
        List<EvidenceRecord> pageResults;
        do {
            pageResults = client.listEvidence(page, 5, true);
            pageResults.forEach(e -> allFound.add(e.uuid()));
            page++;
        } while (!pageResults.isEmpty());

        for (String uuid : created) {
            assertTrue(allFound.contains(uuid), "Evidence " + uuid + " should be in list");
        }
    }

    @Test
    void testListOperatorEvidence() {
        OperatorRecord self = client.getSelf();

        String entityUuid = client.pushEntity("op-evidence-" + randomUuid().substring(0, 8) + ".com", "op_user");
        createdEntities.add(entityUuid);

        for (int i = 0; i < 5; i++) {
            String uuid = client.submitEvidence(entityUuid, "Evidence " + i, "Note " + i, "tag" + (i % 3));
            createdEvidenceRecords.add(uuid);
        }

        List<EvidenceRecord> opEvidence = client.listOperatorEvidence(self.uuid(), 1, 100, true);
        assertFalse(opEvidence.isEmpty());
        for (EvidenceRecord e : opEvidence) {
            assertEquals(self.uuid(), e.operatorUuid());
        }
    }

    @Test
    void testListEntityEvidence() {
        String entityUuid = client.pushEntity("entity-evidence-" + randomUuid().substring(0, 8) + ".com", "entity_user");
        createdEntities.add(entityUuid);

        for (int i = 0; i < 5; i++) {
            String uuid = client.submitEvidence(entityUuid, "Evidence " + i, "Note " + i, "tag" + (i % 3));
            createdEvidenceRecords.add(uuid);
        }

        List<EvidenceRecord> entityEvidence = client.listEntityEvidenceRecords(entityUuid);
        for (EvidenceRecord e : entityEvidence) {
            assertEquals(entityUuid, e.entityUuid());
        }
    }

    @Test
    void testNonConfidentialEvidenceAccess() {
        String entityUuid = client.pushEntity("non-conf-" + randomUuid().substring(0, 8) + ".com", "non_conf_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Non-Confidential Evidence", "Automatic Detection by System", "non_confidential_tag");
        createdEvidenceRecords.add(evidenceUuid);

        FederationClient anon = createAnonymousClient();
        EvidenceRecord rec = anon.getEvidenceRecord(evidenceUuid);
        assertNotNull(rec);
        assertEquals("Non-Confidential Evidence", rec.textContent());
        assertEquals("Automatic Detection by System", rec.note());
        assertEquals("non_confidential_tag", rec.tag());
        assertFalse(rec.confidential());
        assertEquals(entityUuid, rec.entityUuid());
        anon.close();
    }

    @Test
    void testConfidentialEvidenceAccess() {
        String entityUuid = client.pushEntity("conf-acc-" + randomUuid().substring(0, 8) + ".com", "conf_acc_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Confidential Evidence", "Automatic Detection by System", "confidential_tag", true);
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
    void testLargeEvidenceTextContent() {
        String entityUuid = client.pushEntity("large-evidence-" + randomUuid().substring(0, 8) + ".com", "large_user");
        createdEntities.add(entityUuid);

        String largeText = "A".repeat(10000);
        String evidenceUuid = client.submitEvidence(entityUuid, largeText, "Note for large content", "large_content_tag");
        createdEvidenceRecords.add(evidenceUuid);

        EvidenceRecord rec = client.getEvidenceRecord(evidenceUuid);
        assertEquals(largeText, rec.textContent());
    }

    @Test
    void testEvidenceLifecycleIntegrity() {
        String entityUuid = client.pushEntity("evidence-lifecycle-" + randomUuid().substring(0, 8) + ".com", "evidence_lifecycle_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Original evidence content", "Original note", "lifecycle_test");
        createdEvidenceRecords.add(evidenceUuid);

        EvidenceRecord rec = client.getEvidenceRecord(evidenceUuid);
        assertEquals("Original evidence content", rec.textContent());
        assertFalse(rec.confidential());

        client.updateEvidenceConfidentiality(evidenceUuid, true);
        assertTrue(client.getEvidenceRecord(evidenceUuid).confidential());

        client.updateEvidenceConfidentiality(evidenceUuid, false);
        assertFalse(client.getEvidenceRecord(evidenceUuid).confidential());

        client.deleteEvidence(evidenceUuid);
        removeFromCleanup(createdEvidenceRecords, evidenceUuid);

        try {
            client.getEvidenceRecord(evidenceUuid);
            fail("Expected FederationClientException for deleted evidence");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testEvidenceConfidentialityConsistency() {
        String entityUuid = client.pushEntity("conf-cons-" + randomUuid().substring(0, 8) + ".com", "conf_cons_user");
        createdEntities.add(entityUuid);

        String confidentialUuid = client.submitEvidence(entityUuid, "Confidential content", "Confidential note", "confidential", true);
        createdEvidenceRecords.add(confidentialUuid);
        assertTrue(client.getEvidenceRecord(confidentialUuid).confidential());

        String publicUuid = client.submitEvidence(entityUuid, "Public content", "Public note", "public");
        createdEvidenceRecords.add(publicUuid);
        assertFalse(client.getEvidenceRecord(publicUuid).confidential());

        for (int i = 0; i < 3; i++) {
            client.updateEvidenceConfidentiality(publicUuid, true);
            assertTrue(client.getEvidenceRecord(publicUuid).confidential());
            client.updateEvidenceConfidentiality(publicUuid, false);
            assertFalse(client.getEvidenceRecord(publicUuid).confidential());
        }
    }

    @Test
    void testEvidenceAssociationIntegrity() {
        OperatorRecord self = client.getSelf();

        String[] entityUuids = new String[3];
        for (int i = 0; i < 3; i++) {
            entityUuids[i] = client.pushEntity("assoc-test-" + i + "-" + randomUuid().substring(0, 8) + ".com", "assoc_user_" + i);
            createdEntities.add(entityUuids[i]);
        }

        for (String euuid : entityUuids) {
            String evuuid = client.submitEvidence(euuid, "Evidence for entity", "Note", "association");
            createdEvidenceRecords.add(evuuid);
            assertEquals(euuid, client.getEvidenceRecord(evuuid).entityUuid());
            assertEquals(self.uuid(), client.getEvidenceRecord(evuuid).operatorUuid());
        }
    }

    @Test
    void testEvidenceTagValidation() {
        String entityUuid = createSecurityEntity();
        String evidenceUuid = createSecurityEvidence(entityUuid);

        String[] validTags = {"valid_tag", "tag-123", "a", "a".repeat(32)};
        for (String tag : validTags) {
            client.updateEvidenceTag(evidenceUuid, tag);
            assertEquals(tag, client.getEvidenceRecord(evidenceUuid).tag());
        }

        String[] invalidTags = {"tag with space", "tag!special", "tag@invalid", "a".repeat(33)};
        for (String tag : invalidTags) {
            try {
                client.updateEvidenceTag(evidenceUuid, tag);
                fail("Invalid tag '" + tag + "' should be rejected");
            } catch (FederationClientException e) {
                assertEquals(400, e.getStatusCode());
            }
        }
    }

    @Test
    void testEvidenceListRespectsConfidentialityFlag() {
        String entityUuid = client.pushEntity("list-conf-" + randomUuid().substring(0, 8) + ".com", "list_conf_user");
        createdEntities.add(entityUuid);

        String publicUuid = client.submitEvidence(entityUuid, "Public list evidence", "Note", "public_list");
        createdEvidenceRecords.add(publicUuid);

        String confidentialUuid = client.submitEvidence(entityUuid, "Confidential list evidence", "Note", "conf_list", true);
        createdEvidenceRecords.add(confidentialUuid);

        List<EvidenceRecord> publicList = client.listEvidence(1, 100, false);
        Set<String> publicIds = publicList.stream().map(EvidenceRecord::uuid).collect(Collectors.toSet());
        assertTrue(publicIds.contains(publicUuid));
        assertFalse(publicIds.contains(confidentialUuid));

        List<EvidenceRecord> fullList = client.listEvidence(1, 100, true);
        Set<String> fullIds = fullList.stream().map(EvidenceRecord::uuid).collect(Collectors.toSet());
        assertTrue(fullIds.contains(publicUuid));
        assertTrue(fullIds.contains(confidentialUuid));
    }

    @Test
    void testEvidenceReportLinkingIntegrity() {
        String entityUuid = client.pushEntity("evidence-report-link-" + randomUuid().substring(0, 8) + ".com", "link_user");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Report for evidence linking", IncidentType.SPAM);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());

        String standaloneEvidenceUuid = client.submitEvidence(entityUuid, "Standalone evidence", "Note", "standalone");
        createdEvidenceRecords.add(standaloneEvidenceUuid);

        client.addEvidenceToReport(standaloneEvidenceUuid, reportUuid);
        assertEquals(reportUuid, client.getEvidenceRecord(standaloneEvidenceUuid).report());
    }

    @Test
    void testEvidenceConfidentialityToggleAffectsAnonymousAccess() {
        String entityUuid = createSecurityEntity();
        String evidenceUuid = client.submitEvidence(entityUuid, "Toggle confidential evidence", "Note", "toggle");
        createdEvidenceRecords.add(evidenceUuid);

        FederationClient anon = createAnonymousClient();
        assertFalse(anon.getEvidenceRecord(evidenceUuid).confidential());
        anon.close();

        client.updateEvidenceConfidentiality(evidenceUuid, true);

        FederationClient anon2 = createAnonymousClient();
        try {
            anon2.getEvidenceRecord(evidenceUuid);
            fail("Anonymous should not access confidential evidence");
        } catch (FederationClientException e) {
            assertEquals(403, e.getStatusCode());
        }
        anon2.close();

        client.updateEvidenceConfidentiality(evidenceUuid, false);
        FederationClient anon3 = createAnonymousClient();
        assertFalse(anon3.getEvidenceRecord(evidenceUuid).confidential());
        anon3.close();
    }

    @Test
    void testEvidenceCanBeLinkedToMultipleReports() {
        String entityUuid = client.pushEntity("multi-report-evidence-" + randomUuid().substring(0, 8) + ".com", "multi_report_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Shared evidence", "Note", "shared");
        createdEvidenceRecords.add(evidenceUuid);

        ReportSubmission reportA = client.submitReport(entityUuid, "Report A", IncidentType.SPAM);
        createdReports.add(reportA.getReport().uuid());
        createdEvidenceRecords.add(reportA.getEvidence().get(0).uuid());

        ReportSubmission reportB = client.submitReport(entityUuid, "Report B", IncidentType.SCAM);
        createdReports.add(reportB.getReport().uuid());
        createdEvidenceRecords.add(reportB.getEvidence().get(0).uuid());

        client.addEvidenceToReport(evidenceUuid, reportA.getReport().uuid());
        assertEquals(reportA.getReport().uuid(), client.getEvidenceRecord(evidenceUuid).report());

        client.addEvidenceToReport(evidenceUuid, reportB.getReport().uuid());
        assertEquals(reportB.getReport().uuid(), client.getEvidenceRecord(evidenceUuid).report());
    }

    @Test
    void testHighVolumeEvidenceOperations() {
        String entityUuid = client.pushEntity("high-volume-evidence-" + randomUuid().substring(0, 8) + ".com", "high_volume_user");
        createdEntities.add(entityUuid);

        int batchSize = 15;
        List<String> uuids = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            String uuid = client.submitEvidence(entityUuid, "Batch evidence content " + i, "Batch note " + i, "batch_" + i, i % 2 == 0);
            createdEvidenceRecords.add(uuid);
            uuids.add(uuid);
        }

        for (String uuid : uuids) {
            assertNotNull(client.getEvidenceRecord(uuid));
            assertEquals(entityUuid, client.getEvidenceRecord(uuid).entityUuid());
        }
    }

    @Test
    void testEvidenceContentVariations() {
        String entityUuid = client.pushEntity("content-variations-" + randomUuid().substring(0, 8) + ".com", "content_test_user");
        createdEntities.add(entityUuid);

        String[][] cases = {
            {"Single word", "Single word test", "single"},
            {"Special chars: @#$%^&*()[]{}|;':\",.<>?/", "Special chars", "special"},
            {"Unicode content: 你好世界 🌍", "Unicode test", "unicode"},
        };

        for (String[] testCase : cases) {
            String uuid = client.submitEvidence(entityUuid, testCase[0], testCase[1], testCase[2]);
            createdEvidenceRecords.add(uuid);
            EvidenceRecord rec = client.getEvidenceRecord(uuid);
            assertEquals(testCase[0], rec.textContent());
            assertEquals(testCase[1], rec.note());
            assertEquals(testCase[2], rec.tag());
        }
    }

    @Test
    void testEvidenceDeletionCascadesToAttachments() throws java.io.IOException {
        String entityUuid = client.pushEntity("evidence-attachment-cascade-" + randomUuid().substring(0, 8) + ".com", "cascade_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Evidence with attachment", "Note", "cascade");
        createdEvidenceRecords.add(evidenceUuid);

        java.nio.file.Path testFile = createTempFile("cascade_test.txt", "Cascade test content");
        UploadResult uploadResult = client.uploadFileAttachment(evidenceUuid, testFile.toString());
        String attachmentUuid = uploadResult.uuid();

        assertEquals(1, client.getEvidenceAttachments(evidenceUuid).size());

        client.deleteEvidence(evidenceUuid);
        removeFromCleanup(createdEvidenceRecords, evidenceUuid);

        try {
            client.getAttachmentInfo(attachmentUuid);
            fail("Attachment should be deleted when parent evidence is deleted");
        } catch (FederationClientException e) {
            assertEquals(404, e.getStatusCode());
        }
    }

    @Test
    void testEvidenceClassificationIsImmutable() {
        String entityUuid = client.pushEntity("classified-evidence-" + randomUuid().substring(0, 8) + ".com", "classified_evidence");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Immutable evidence classification", "Note", "classified");
        createdEvidenceRecords.add(evidenceUuid);

        client.classifyEvidence(evidenceUuid, ClassificationFlag.NORMAL);
        assertEquals(ClassificationFlag.NORMAL, client.getEvidenceRecord(evidenceUuid).classificationFlag());

        FederationClientException exception = assertThrows(FederationClientException.class,
            () -> client.classifyEvidence(evidenceUuid, ClassificationFlag.MALICIOUS));
        assertEquals(409, exception.getStatusCode());
        assertEquals(ClassificationFlag.NORMAL, client.getEvidenceRecord(evidenceUuid).classificationFlag());
    }

    @Test
    void testSubmitEvidenceWithClassification() {
        String entityUuid = client.pushEntity("submission-classification-" + randomUuid().substring(0, 8) + ".com", "submission_classification");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(
            entityUuid,
            "Classified during submission",
            "Note",
            "submission_classification",
            false,
            null,
            ClassificationFlag.SUSPICIOUS
        );
        createdEvidenceRecords.add(evidenceUuid);

        assertEquals(ClassificationFlag.SUSPICIOUS, client.getEvidenceRecord(evidenceUuid).classificationFlag());
    }

    @Test
    void testEvidenceClassificationRequiresManagementPermission() {
        String entityUuid = createSecurityEntity();
        String evidenceUuid = createSecurityEvidence(entityUuid);
        FederationClient clientOnly = createLimitedOperator("classification_client", false, false, true);

        try {
            FederationClientException classifyException = assertThrows(FederationClientException.class,
                () -> clientOnly.classifyEvidence(evidenceUuid, ClassificationFlag.MALICIOUS));
            assertEquals(403, classifyException.getStatusCode());

            FederationClientException submitException = assertThrows(FederationClientException.class,
                () -> clientOnly.submitEvidence(
                    entityUuid,
                    "Unauthorized classification submission",
                    "Note",
                    "classification_security",
                    false,
                    null,
                    ClassificationFlag.MALICIOUS
                ));
            assertEquals(403, submitException.getStatusCode());
        } finally {
            clientOnly.close();
        }
    }
}
