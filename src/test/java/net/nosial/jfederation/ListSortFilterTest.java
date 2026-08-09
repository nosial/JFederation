package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.records.BlacklistRecord;
import net.nosial.jfederation.records.EntityRecord;
import net.nosial.jfederation.records.EvidenceRecord;
import net.nosial.jfederation.records.FileAttachmentRecord;
import net.nosial.jfederation.records.OperatorRecord;
import net.nosial.jfederation.records.ReportRecord;
import net.nosial.jfederation.records.ReportSubmission;
import net.nosial.jfederation.records.UploadResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListSortFilterTest extends FederationClientTestBase {

    @Test
    void testListEntitiesWithSortAndCategory() {
        String entityUuid = client.pushEntity("sort-entities-" + randomUuid().substring(0, 8) + ".com", "sort_entities");
        createdEntities.add(entityUuid);
        client.setEntityWhitelist(entityUuid, true);

        List<EntityRecord> whitelisted = client.listEntities(1, 10, "WHITELISTED", "created", "ASC");
        List<String> whitelistedUuids = whitelisted.stream().map(EntityRecord::uuid).toList();
        assertTrue(whitelistedUuids.contains(entityUuid));

        List<EntityRecord> notWhitelisted = client.listEntities(1, 10, "NOT_WHITELISTED", "created", "DESC");
        List<String> notWhitelistedUuids = notWhitelisted.stream().map(EntityRecord::uuid).toList();
        assertFalse(notWhitelistedUuids.contains(entityUuid));
    }

    @Test
    void testSearchEntitiesWithSortAndCategory() {
        String entityUuid = client.pushEntity("search-sort-" + randomUuid().substring(0, 8) + ".com", "search_sort");
        createdEntities.add(entityUuid);
        client.setEntityWhitelist(entityUuid, true);

        List<EntityRecord> results = client.searchEntities("search-sort", 1, 10, "WHITELISTED", "host", "ASC");
        List<String> uuids = results.stream().map(EntityRecord::uuid).toList();
        assertTrue(uuids.contains(entityUuid));
    }

    @Test
    void testListReportsWithSort() {
        String entityUuid = client.pushEntity("sort-reports-" + randomUuid().substring(0, 8) + ".com", "sort_reports");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Sorted report", IncidentType.SPAM, null, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        List<ReportRecord> opened = client.listReports(1, 10, "OPENED", "created", "DESC");
        List<String> openedUuids = opened.stream().map(ReportRecord::uuid).toList();
        assertTrue(openedUuids.contains(reportUuid));

        List<ReportRecord> closed = client.listReports(1, 10, "CLOSED", "created", "ASC");
        List<String> closedUuids = closed.stream().map(ReportRecord::uuid).toList();
        assertFalse(closedUuids.contains(reportUuid));
    }

    @Test
    void testListEvidenceWithSortAndCategory() {
        String entityUuid = client.pushEntity("sort-evidence-" + randomUuid().substring(0, 8) + ".com", "sort_evidence");
        createdEntities.add(entityUuid);

        String publicEvidence = client.submitEvidence(entityUuid, "Public evidence", "note", "tag", false);
        createdEvidenceRecords.add(publicEvidence);
        String confidentialEvidence = client.submitEvidence(entityUuid, "Confidential evidence", "note", "tag", true);
        createdEvidenceRecords.add(confidentialEvidence);

        List<EvidenceRecord> confidential = client.listEvidence(1, 10, true, "CONFIDENTIAL", "created", "DESC");
        List<String> confidentialUuids = confidential.stream().map(EvidenceRecord::uuid).toList();
        assertTrue(confidentialUuids.contains(confidentialEvidence));
        assertFalse(confidentialUuids.contains(publicEvidence));

        List<EvidenceRecord> notConfidential = client.listEvidence(1, 10, true, "NOT_CONFIDENTIAL", "created", "DESC");
        List<String> notConfidentialUuids = notConfidential.stream().map(EvidenceRecord::uuid).toList();
        assertTrue(notConfidentialUuids.contains(publicEvidence));
        assertFalse(notConfidentialUuids.contains(confidentialEvidence));
    }

    @Test
    void testListBlacklistWithSortAndCategory() throws Exception {
        String entityUuid = client.pushEntity("sort-blacklist-" + randomUuid().substring(0, 8) + ".com", "sort_blacklist");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Blacklist evidence", "note", "tag", false);
        createdEvidenceRecords.add(evidenceUuid);
        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        List<BlacklistRecord> active = client.listBlacklistRecords(1, 10, false, "ACTIVE", "created", "DESC");
        List<String> activeUuids = active.stream().map(BlacklistRecord::uuid).toList();
        assertTrue(activeUuids.contains(blacklistUuid));

        List<BlacklistRecord> lifted = client.listBlacklistRecords(1, 10, false, "LIFTED", "created", "DESC");
        List<String> liftedUuids = lifted.stream().map(BlacklistRecord::uuid).toList();
        assertFalse(liftedUuids.contains(blacklistUuid));
    }

    @Test
    void testListAttachmentsWithSortAndCategory() throws Exception {
        String entityUuid = client.pushEntity("sort-attach-" + randomUuid().substring(0, 8) + ".com", "sort_attach");
        createdEntities.add(entityUuid);
        String evidenceUuid = client.submitEvidence(entityUuid, "Attachment evidence", "note", "tag", false);
        createdEvidenceRecords.add(evidenceUuid);

        Path testFile = createTempFile("sort_attach_", "Attachment sort content");
        UploadResult uploaded = client.uploadFileAttachment(evidenceUuid, testFile.toString());
        createdAttachments.add(uploaded.uuid());

        List<FileAttachmentRecord> attachments = client.listAttachments(1, 10, "REPORT", "created", "DESC");
        List<String> uuids = attachments.stream().map(FileAttachmentRecord::uuid).toList();
        assertFalse(uuids.isEmpty());
    }

    @Test
    void testListOperatorsWithSortAndCategory() {
        String operatorUuid = client.createOperator("sort_operator_" + randomUuid().substring(0, 5)).uuid();
        createdOperators.add(operatorUuid);
        client.setManagementPermissions(operatorUuid, true);
        client.setOperatorPermissions(operatorUuid, true);
        client.setClientPermissions(operatorUuid, true);

        List<OperatorRecord> enabled = client.listOperators(1, 10, "ENABLED", "created", "DESC");
        List<String> enabledUuids = enabled.stream().map(OperatorRecord::uuid).toList();
        assertTrue(enabledUuids.contains(operatorUuid));
    }

    @Test
    void testSortParamsInvalidPageStillValidated() {
        assertThrows(IllegalArgumentException.class,
            () -> client.listEntities(0, 10, "WHITELISTED", "created", "ASC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.listReports(1, -1, "OPENED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.searchEntities("", 1, 10, "WHITELISTED", "created", "ASC"));
    }
}
