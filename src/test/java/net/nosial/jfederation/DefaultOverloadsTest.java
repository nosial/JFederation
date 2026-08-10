package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.RecordType;
import net.nosial.jfederation.records.BlacklistRecord;
import net.nosial.jfederation.records.EntityRecord;
import net.nosial.jfederation.records.EvidenceRecord;
import net.nosial.jfederation.records.ReportRecord;
import net.nosial.jfederation.records.ReportSubmission;
import net.nosial.jfederation.records.ScannedContent;
import net.nosial.jfederation.records.SearchResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the default-argument overloads added for parity with the specification,
 * where trailing parameters are optional.
 */
class DefaultOverloadsTest extends FederationClientTestBase {

    private static final String BENIGN_SAMPLE_TEXT = "This is a benign sample of content that should not trigger any scanning rules.";

    @Test
    void testCloseReportWithoutClassification() {
        String entityUuid = client.pushEntity("close-default-" + randomUuid().substring(0, 8) + ".com", "close_default");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Report to close without classification", IncidentType.SPAM);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        client.closeReport(reportUuid);

        ReportRecord report = client.getReport(reportUuid);
        assertFalse(report.opened());
    }

    @Test
    void testBlacklistEntityWithoutExpires() {
        String entityUuid = client.pushEntity("perm-blacklist-" + randomUuid().substring(0, 8) + ".com", "perm_blacklist");
        createdEntities.add(entityUuid);
        String evidenceUuid = client.submitEvidence(entityUuid, "Permanent blacklist evidence", null, null);
        createdEvidenceRecords.add(evidenceUuid);

        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM);
        createdBlacklistRecords.add(blacklistUuid);

        BlacklistRecord record = client.getBlacklistRecord(blacklistUuid);
        assertEquals(blacklistUuid, record.uuid());
        assertNull(record.expires(), "Permanent blacklist should have no expiration");
        assertFalse(record.lifted());
        assertEquals(IncidentType.SPAM, record.type());
    }

    @Test
    void testGetTopThreatsWithDefaultLimit() {
        List<EntityRecord> topThreats = client.getTopThreats();

        assertNotNull(topThreats);
        assertTrue(topThreats.size() <= 10, "Default limit should be 10");
        for (EntityRecord entity : topThreats) {
            assertNotNull(entity.uuid());
            assertNotNull(entity.host());
        }
    }

    @Test
    void testSearchWithTypesAndDefaultPagination() {
        String entityUuid = client.pushEntity("search-types-" + randomUuid().substring(0, 8) + ".com", "search_types");
        createdEntities.add(entityUuid);

        List<SearchResult> results = client.search("search-types", List.of(RecordType.ENTITY));
        assertNotNull(results);
        assertTrue(results.size() <= 10, "Default limit should be 10");
    }

    @Test
    void testSearchWithPaginationAndNoTypes() {
        List<SearchResult> results = client.search("search-types", 1, 5);
        assertNotNull(results);
        assertTrue(results.size() <= 5);
    }

    @Test
    void testScanContentDefaultOverloads() {
        ScannedContent one = client.scanContent(BENIGN_SAMPLE_TEXT);
        assertNotNull(one);

        ScannedContent two = client.scanContent(BENIGN_SAMPLE_TEXT, "some-author");
        assertNotNull(two);

        ScannedContent three = client.scanContent(BENIGN_SAMPLE_TEXT, null, 2);
        assertNotNull(three);

        ScannedContent four = client.scanContent(BENIGN_SAMPLE_TEXT, null, 2, 0.5f);
        assertNotNull(four);
    }

    @Test
    void testSubmitReportWithOnlyMessage() {
        String entityUuid = client.pushEntity("report-message-" + randomUuid().substring(0, 8) + ".com", "report_message");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Report with only a message", IncidentType.SPAM, "Custom message");
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        assertEquals("Custom message", client.getReport(reportUuid).message());
    }

    @Test
    void testSubmitReportWithMessageTagAndLocalPaths() throws IOException {
        String entityUuid = client.pushEntity("report-paths-" + randomUuid().substring(0, 8) + ".com", "report_paths");
        createdEntities.add(entityUuid);

        Path firstFile = createTempFile("overload_first_", "First overload attachment");
        Path secondFile = createTempFile("overload_second_", "Second overload attachment");

        ReportSubmission submission = client.submitReport(entityUuid, "Report with local paths", IncidentType.SPAM,
            "With paths", "overload_tag", List.of(firstFile.toString(), secondFile.toString()));
        String reportUuid = submission.getReport().uuid();
        String evidenceUuid = submission.getEvidence().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(evidenceUuid);

        assertEquals("With paths", client.getReport(reportUuid).message());
        assertNotNull(submission.attachmentNodes());
        assertEquals(2, submission.attachmentNodes().size());

        List<net.nosial.jfederation.records.FileAttachmentRecord> attachments = client.getEvidenceAttachments(evidenceUuid);
        assertEquals(2, attachments.size());
        for (net.nosial.jfederation.records.FileAttachmentRecord attachment : attachments) {
            createdAttachments.add(attachment.uuid());
        }
    }

    @Test
    void testSubmitEvidenceWithoutContent() {
        String entityUuid = client.pushEntity("evidence-empty-" + randomUuid().substring(0, 8) + ".com", "evidence_empty");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid);
        createdEvidenceRecords.add(evidenceUuid);

        EvidenceRecord record = client.getEvidenceRecord(evidenceUuid);
        assertEquals(evidenceUuid, record.uuid());
        assertFalse(record.confidential());
    }

    @Test
    void testSubmitEvidenceConfidentialOnly() {
        String entityUuid = client.pushEntity("evidence-conf-" + randomUuid().substring(0, 8) + ".com", "evidence_conf");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, true);
        createdEvidenceRecords.add(evidenceUuid);

        EvidenceRecord record = client.getEvidenceRecord(evidenceUuid);
        assertTrue(record.confidential());
    }

    @Test
    void testDefaultOverloadsValidation() {
        assertThrows(IllegalArgumentException.class, () -> client.scanContent(""));
        assertThrows(IllegalArgumentException.class, () -> client.closeReport(""));
        assertThrows(IllegalArgumentException.class, () -> client.closeReport(null));
        assertThrows(IllegalArgumentException.class,
            () -> client.blacklistEntity("", "00000000-0000-0000-0000-000000000000", IncidentType.SPAM));
        assertThrows(IllegalArgumentException.class,
            () -> client.blacklistEntity("host.com", "", IncidentType.SPAM));
        assertThrows(IllegalArgumentException.class, () -> client.search("", List.of(RecordType.ENTITY)));
        assertThrows(IllegalArgumentException.class, () -> client.search("", 1, 10));
        assertThrows(IllegalArgumentException.class, () -> client.search("q", 0, 10));
        assertThrows(IllegalArgumentException.class, () -> client.search("q", 1, 0));
        assertThrows(IllegalArgumentException.class, () -> client.submitReport("", "content", IncidentType.SPAM, "msg"));
        assertThrows(IllegalArgumentException.class, () -> client.submitReport("entity", "", IncidentType.SPAM, "msg"));
        assertThrows(IllegalArgumentException.class, () -> client.submitEvidence(""));
        assertThrows(IllegalArgumentException.class, () -> client.submitEvidence(null, true));
    }
}
