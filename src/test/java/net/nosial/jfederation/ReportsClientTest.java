package net.nosial.jfederation;

import net.nosial.jfederation.enums.ClassificationFlag;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.ReportRecord;
import net.nosial.jfederation.records.ReportSubmission;
import net.nosial.jfederation.records.BlacklistRecord;
import net.nosial.jfederation.records.EvidenceRecord;
import net.nosial.jfederation.records.FileAttachmentRecord;
import net.nosial.jfederation.records.UploadResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportsClientTest extends FederationClientTestBase {

    @Test
    void testSubmitReport() {
        String entityUuid = client.pushEntity("test-report.com", "test_user");
        createdEntities.add(entityUuid);

        String reportMessage = "Normal content";
        ReportSubmission submission = client.submitReport(entityUuid, "This is report content", IncidentType.SPAM, reportMessage, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        assertNotNull(submission.getReport());
        assertNotNull(submission.getEvidence());
        assertEquals(entityUuid, submission.getReport().reportingEntity());
        assertEquals(reportMessage, submission.getReport().message());
        assertNotNull(submission.getReport().uuid());
    }

    @Test
    void testSubmitReportInvalidEntity() {
        assertThrows(IllegalArgumentException.class,
            () -> client.submitReport("", "content", IncidentType.OTHER, null, null));
    }

    @Test
    void testSubmitReportWithEvidenceTag() {
        String entityUuid = client.pushEntity("evidence-tag-report.com", "tag_user");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Report with evidence tag", IncidentType.SPAM, null, "initial-tag");
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        assertNotNull(submission.getEvidence().tag());
        assertEquals("initial-tag", submission.getEvidence().tag());
    }

    @Test
    void testListReports() {
        String entityUuid = client.pushEntity("list-reports.com", "list_user");
        createdEntities.add(entityUuid);

        String[] reportUuids = new String[3];
        for (int i = 0; i < 3; i++) {
            ReportSubmission submission = client.submitReport(entityUuid, "List report " + i, IncidentType.OTHER, null, null);
            reportUuids[i] = submission.getReport().uuid();
            createdReports.add(reportUuids[i]);
            createdEvidenceRecords.add(submission.getEvidence().uuid());
        }

        List<ReportRecord> reports = client.listReports(1, 10, null);
        assertTrue(reports.size() >= 3);

        List<String> foundUuids = reports.stream().map(ReportRecord::uuid).toList();
        for (String uuid : reportUuids) {
            assertTrue(foundUuids.contains(uuid));
        }

        List<ReportRecord> openedReports = client.listReports(1, 10, "OPENED");
        List<String> openedUuids = openedReports.stream().map(ReportRecord::uuid).toList();
        for (String uuid : reportUuids) {
            assertTrue(openedUuids.contains(uuid));
        }

        List<ReportRecord> closedReports = client.listReports(1, 10, "CLOSED");
        List<String> closedUuids = closedReports.stream().map(ReportRecord::uuid).toList();
        for (String uuid : reportUuids) {
            assertFalse(closedUuids.contains(uuid));
        }
    }

    @Test
    void testListReportsInvalidLimit() {
        assertThrows(IllegalArgumentException.class, () -> client.listReports(1, 0, null));
    }

    @Test
    void testGetReport() {
        String entityUuid = client.pushEntity("get-report.com", "get_user");
        createdEntities.add(entityUuid);

        String reportMessage = "Get Report";
        ReportSubmission submission = client.submitReport(entityUuid, "Report to get", IncidentType.SPAM, reportMessage, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        ReportRecord report = client.getReport(reportUuid);
        assertNotNull(report);
        assertEquals(reportUuid, report.uuid());
        assertEquals(reportMessage, report.message());
        assertEquals(entityUuid, report.reportingEntity());
        assertTrue(report.created() > 0);
    }

    @Test
    void testGetReportEmptyUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.getReport(""));
    }

    @Test
    void testListReportEvidenceRecords() {
        String entityUuid = client.pushEntity("report-evidence.com", "report_evidence_user");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Report with evidence", IncidentType.SPAM, null, null);
        String reportUuid = submission.getReport().uuid();
        String initialEvidenceUuid = submission.getEvidence().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(initialEvidenceUuid);

        List<EvidenceRecord> evidence = client.listReportEvidenceRecords(reportUuid);
        List<String> evidenceUuids = evidence.stream().map(EvidenceRecord::uuid).toList();
        assertTrue(evidenceUuids.contains(initialEvidenceUuid));

        String additionalEvidence = client.submitEvidence(entityUuid, "Additional evidence", "note", "additional");
        createdEvidenceRecords.add(additionalEvidence);
        client.addEvidenceToReport(additionalEvidence, reportUuid);

        List<EvidenceRecord> updatedEvidence = client.listReportEvidenceRecords(reportUuid, 1, 10);
        List<String> updatedUuids = updatedEvidence.stream().map(EvidenceRecord::uuid).toList();
        assertTrue(updatedUuids.contains(initialEvidenceUuid));
        assertTrue(updatedUuids.contains(additionalEvidence));
    }

    @Test
    void testListReportEvidenceRecordsEmptyUuid() {
        assertThrows(IllegalArgumentException.class, () -> client.listReportEvidenceRecords(""));
    }

    @Test
    void testListReportEvidenceRecordsNonExistentReport() {
        assertThrows(FederationClientException.class,
            () -> client.listReportEvidenceRecords("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void testCloseReport() {
        String entityUuid = client.pushEntity("close-report.com", "close_user");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Report to close", IncidentType.SPAM, null, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        client.closeReport(reportUuid, null);
        ReportRecord report = client.getReport(reportUuid);
        assertFalse(report.opened());
    }

    @Test
    void testCloseNonExistentReport() {
        assertThrows(FederationClientException.class,
            () -> client.closeReport("00000000-0000-0000-0000-000000000000", null));
    }

    @Test
    void testDeleteReport() {
        String entityUuid = client.pushEntity("delete-report.com", "delete_user");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Report to delete", IncidentType.SPAM, null, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        client.deleteReport(reportUuid);
        removeFromCleanup(createdReports, reportUuid);

        assertThrows(FederationClientException.class, () -> client.getReport(reportUuid));
    }

    @Test
    void testDeleteNonExistentReport() {
        assertThrows(FederationClientException.class,
            () -> client.deleteReport("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void testListOperatorReports() {
        String entityUuid = client.pushEntity("list-op-reports.com", "list_op_user");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Operator report", IncidentType.SPAM, null, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        String submitterUuid = submission.getReport().submittingOperator();
        List<ReportRecord> reports = client.listOperatorReports(submitterUuid, 1, 10, null);
        List<String> foundUuids = reports.stream().map(ReportRecord::uuid).toList();
        assertTrue(foundUuids.contains(reportUuid));

        List<ReportRecord> opened = client.listOperatorReports(submitterUuid, 1, 10, "OPENED");
        List<String> openedUuids = opened.stream().map(ReportRecord::uuid).toList();
        assertTrue(openedUuids.contains(reportUuid));

        List<ReportRecord> closed = client.listOperatorReports(submitterUuid, 1, 10, "CLOSED");
        List<String> closedUuids = closed.stream().map(ReportRecord::uuid).toList();
        assertFalse(closedUuids.contains(reportUuid));
    }

    @Test
    void testListOperatorReportsInvalidPage() {
        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorReports("00000000-0000-0000-0000-000000000000", 0, 10, null));
    }

    @Test
    void testListEntityReports() {
        String entityUuid = client.pushEntity("list-entity-reports.com", "list_entity");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Entity report", IncidentType.SPAM, null, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        List<ReportRecord> reports = client.listEntityReports(entityUuid, 1, 10, null);
        List<String> foundUuids = reports.stream().map(ReportRecord::uuid).toList();
        assertTrue(foundUuids.contains(reportUuid));

        List<ReportRecord> opened = client.listEntityReports(entityUuid, 1, 10, "OPENED");
        List<String> openedUuids = opened.stream().map(ReportRecord::uuid).toList();
        assertTrue(openedUuids.contains(reportUuid));

        List<ReportRecord> closed = client.listEntityReports(entityUuid, 1, 10, "CLOSED");
        List<String> closedUuids = closed.stream().map(ReportRecord::uuid).toList();
        assertFalse(closedUuids.contains(reportUuid));
    }

    @Test
    void testGetNonExistentReport() {
        assertThrows(FederationClientException.class,
            () -> client.getReport("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void testSubmitReportWithAllOptionalParams() {
        String entityUuid = client.pushEntity("full-params.com", "full_params");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Full params report", IncidentType.SPAM, "Report message", "evidence-tag");
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        assertNotNull(submission.getReport());
        assertNotNull(submission.getEvidence());
        assertEquals("evidence-tag", submission.getEvidence().tag());
    }

    @Test
    void testListReportsPageExhaustion() {
        String entityUuid = client.pushEntity("page-exhaust.com", "page_exhaust");
        createdEntities.add(entityUuid);

        List<String> reportUuids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ReportSubmission submission = client.submitReport(entityUuid, "Page exhaust report " + i, IncidentType.OTHER, null, null);
            String uuid = submission.getReport().uuid();
            reportUuids.add(uuid);
            createdReports.add(uuid);
            createdEvidenceRecords.add(submission.getEvidence().uuid());
        }

        List<String> allReportUuids = new ArrayList<>();
        int page = 1;
        List<ReportRecord> reports;
        do {
            reports = client.listReports(page, 2, null);
            for (ReportRecord r : reports) {
                assertNotNull(r.uuid());
                allReportUuids.add(r.uuid());
            }
            page++;
        } while (!reports.isEmpty());

        for (String uuid : reportUuids) {
            assertTrue(allReportUuids.contains(uuid));
        }
    }

    @Test
    void testBulkReportSubmissionConsistency() {
        String entityUuid = client.pushEntity("bulk-report.com", "bulk_user");
        createdEntities.add(entityUuid);

        List<ReportSubmission> submissions = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ReportSubmission submission = client.submitReport(entityUuid, "Bulk report " + i, IncidentType.OTHER, null, null);
            createdReports.add(submission.getReport().uuid());
            createdEvidenceRecords.add(submission.getEvidence().uuid());
            submissions.add(submission);
        }

        assertEquals(5, submissions.size());
        for (ReportSubmission s : submissions) {
            assertNotNull(s.getReport());
            assertNotNull(s.getEvidence());
        }
    }

    @Test
    void testHighVolumeReportOperations() {
        String entityUuid = client.pushEntity("high-volume.com", "high_volume");
        createdEntities.add(entityUuid);

        List<String> reportUuids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ReportSubmission submission = client.submitReport(entityUuid, "High volume report " + i, IncidentType.SPAM, null, null);
            String uuid = submission.getReport().uuid();
            reportUuids.add(uuid);
            createdReports.add(uuid);
            createdEvidenceRecords.add(submission.getEvidence().uuid());
        }

        assertEquals(10, reportUuids.size());
        for (String uuid : reportUuids) {
            ReportRecord report = client.getReport(uuid);
            assertNotNull(report);
            assertEquals(uuid, report.uuid());
        }
    }

    @Test
    void testReportConsistencyAfterMultipleActions() {
        String entityUuid = client.pushEntity("consistency.com", "consistency_user");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Consistency report content", IncidentType.OTHER, null, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        client.closeReport(reportUuid, ClassificationFlag.SUSPICIOUS);

        ReportRecord report = client.getReport(reportUuid);
        assertFalse(report.opened());
    }

    @Test
    void testSecurityReportAssignmentAndClosureEdgeCases() {
        FederationClient submitter = createLimitedOperator("report_submitter", false, false, true);
        FederationClient firstManager = createLimitedOperator("first_manager", true, true, false);
        FederationClient secondManager = createLimitedOperator("second_manager", true, true, false);

        SecurityReport report = createSecurityReport(submitter);

        expectRequestFailure(
            () -> secondManager.closeReport(report.report(), null),
            new int[]{400, 403},
            "Non-assigned manager should not be able to close a report"
        );

        secondManager.assignOperatorToReport(report.report(), getSelfUuid(secondManager));
        secondManager.closeReport(report.report(), null);
        ReportRecord closedReport = client.getReport(report.report());
        assertFalse(closedReport.opened());

        expectRequestFailure(
            () -> secondManager.closeReport(report.report(), null),
            new int[]{400, 403},
            "Closing an already-closed report should fail"
        );

        FederationClient disabledManager = createLimitedOperator("disabled_manager", false, true, false);
        String disabledManagerUuid = getSelfUuid(disabledManager);
        client.disableOperator(disabledManagerUuid);

        SecurityReport newReport = createSecurityReport(submitter);
        expectRequestFailure(
            () -> firstManager.assignOperatorToReport(newReport.report(), disabledManagerUuid),
            new int[]{400, 403},
            "Assigning a disabled operator should fail"
        );

        FederationClient plainClient = createLimitedOperator("plain_client", true);
        expectRequestFailure(
            () -> firstManager.assignOperatorToReport(newReport.report(), getSelfUuid(plainClient)),
            new int[]{400, 403},
            "Assigning an operator without management permissions should fail"
        );
    }

    @Test
    void testSecurityAddEvidenceToReportRequiresOperatorPermission() {
        String entityUuid = createSecurityEntity();
        String evidenceUuid = createSecurityEvidence(entityUuid);
        SecurityReport report = createSecurityReport();

        FederationClient clientOnly = createLimitedOperator("add_evidence_client", false, false, true);
        FederationClient operatorOnly = createLimitedOperator("add_evidence_operator", true, true, true);

        expectRequestFailure(
            () -> clientOnly.addEvidenceToReport(evidenceUuid, report.report()),
            new int[]{400, 403},
            "Client-only operator should not link evidence to report"
        );

        operatorOnly.addEvidenceToReport(evidenceUuid, report.report());
        EvidenceRecord updatedEvidence = client.getEvidenceRecord(evidenceUuid);
        assertEquals(report.report(), updatedEvidence.report());
    }

    @Test
    void testSecurityListAssignedOperatorReportsAccess() {
        FederationClient manager = createLimitedOperator("assigned_reports_manager", true, true, true);
        SecurityReport report = createSecurityReport();
        String managerUuid = getSelfUuid(manager);
        manager.assignOperatorToReport(report.report(), managerUuid);

        List<ReportRecord> assignedReports = client.listAssignedOperatorReports(managerUuid, 1, 100, null);
        List<String> foundUuids = assignedReports.stream().map(ReportRecord::uuid).toList();
        assertTrue(foundUuids.contains(report.report()));

        List<ReportRecord> opened = client.listAssignedOperatorReports(managerUuid, 1, 100, "OPENED");
        List<String> openedUuids = opened.stream().map(ReportRecord::uuid).toList();
        assertTrue(openedUuids.contains(report.report()));

        List<ReportRecord> closed = client.listAssignedOperatorReports(managerUuid, 1, 100, "CLOSED");
        List<String> closedUuids = closed.stream().map(ReportRecord::uuid).toList();
        assertFalse(closedUuids.contains(report.report()));
    }

    @Test
    void testReportFullLifecycleWorkflow() {
        FederationClient submitter = createLimitedOperator("lifecycle_submitter", false, false, true);
        FederationClient manager = createLimitedOperator("lifecycle_manager", true, true, true);

        String entityUuid = createSecurityEntity(submitter);
        ReportSubmission submission = submitter.submitReport(entityUuid, "Full lifecycle report", IncidentType.SPAM, null, null);
        String reportUuid = submission.getReport().uuid();
        String evidenceUuid = submission.getEvidence().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(evidenceUuid);

        String submitterUuid = getSelfUuid(submitter);
        ReportRecord report = client.getReport(reportUuid);
        assertTrue(report.opened());
        assertEquals(submitterUuid, report.assignedOperator());

        String managerUuid = getSelfUuid(manager);
        manager.assignOperatorToReport(reportUuid, managerUuid);
        ReportRecord assignedReport = client.getReport(reportUuid);
        assertEquals(managerUuid, assignedReport.assignedOperator());

        String standaloneEvidenceUuid = createSecurityEvidence(entityUuid, false, submitter);
        manager.addEvidenceToReport(standaloneEvidenceUuid, reportUuid);
        EvidenceRecord linkedEvidence = client.getEvidenceRecord(standaloneEvidenceUuid);
        assertEquals(reportUuid, linkedEvidence.report());

        manager.closeReport(reportUuid, ClassificationFlag.SUSPICIOUS);
        ReportRecord closedReport = client.getReport(reportUuid);
        assertFalse(closedReport.opened());

        expectRequestFailure(
            () -> manager.closeReport(reportUuid, null),
            new int[]{400, 403},
            "Closing an already-closed report should fail"
        );

        client.deleteReport(reportUuid);
        removeFromCleanup(createdReports, reportUuid);

        expectRequestFailure(
            () -> client.getReport(reportUuid),
            new int[]{404},
            "Deleted report should not be retrievable"
        );
    }

    @Test
    void testCloseReportWithoutAssignmentFails() {
        FederationClient manager = createLimitedOperator("close_unassigned_manager", true, true, false);
        SecurityReport report = createSecurityReport();

        expectRequestFailure(
            () -> manager.closeReport(report.report(), null),
            new int[]{400, 403},
            "Closing an unassigned report should fail"
        );
    }

    @Test
    void testReportSubmitWithAttachment() throws IOException {
        String entityUuid = client.pushEntity("report-attachment.com", "report_attach_user");
        createdEntities.add(entityUuid);

        Path testFile = createTempFile("report_attach_", "Report attachment content");

        ReportSubmission submission = client.submitReport(entityUuid, "Report with attachment", IncidentType.SPAM, null, "report_attach");
        String reportUuid = submission.getReport().uuid();
        String evidenceUuid = submission.getEvidence().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(evidenceUuid);

        UploadResult uploadResult = client.uploadFileAttachment(evidenceUuid, testFile.toString());
        createdAttachments.add(uploadResult.uuid());

        List<FileAttachmentRecord> attachments = client.getEvidenceAttachments(evidenceUuid);
        assertEquals(1, attachments.size());
        assertEquals(uploadResult.uuid(), attachments.get(0).uuid());
    }

    @Test
    void testReportAssignmentTransferBetweenManagers() {
        FederationClient firstManager = createLimitedOperator("first_transfer_manager", true, true, true);
        FederationClient secondManager = createLimitedOperator("second_transfer_manager", true, true, true);
        SecurityReport report = createSecurityReport();

        String firstUuid = getSelfUuid(firstManager);
        firstManager.assignOperatorToReport(report.report(), firstUuid);
        ReportRecord assignedReport = client.getReport(report.report());
        assertEquals(firstUuid, assignedReport.assignedOperator());

        String secondUuid = getSelfUuid(secondManager);
        secondManager.assignOperatorToReport(report.report(), secondUuid);
        ReportRecord reassignedReport = client.getReport(report.report());
        assertEquals(secondUuid, reassignedReport.assignedOperator());
    }

    @Test
    void testReportListFiltersByReportingEntity() {
        String entityA = createSecurityEntity();
        String entityB = createSecurityEntity();

        ReportSubmission submissionA = client.submitReport(entityA, "Report for entity A", IncidentType.SPAM, null, null);
        String reportAUuid = submissionA.getReport().uuid();
        createdReports.add(reportAUuid);
        createdEvidenceRecords.add(submissionA.getEvidence().uuid());

        ReportSubmission submissionB = client.submitReport(entityB, "Report for entity B", IncidentType.SCAM, null, null);
        String reportBUuid = submissionB.getReport().uuid();
        createdReports.add(reportBUuid);
        createdEvidenceRecords.add(submissionB.getEvidence().uuid());

        List<ReportRecord> entityAReports = client.listEntityReports(entityA, 1, 10, null);
        List<String> entityAReportUuids = entityAReports.stream().map(ReportRecord::uuid).toList();
        assertTrue(entityAReportUuids.contains(reportAUuid));
        assertFalse(entityAReportUuids.contains(reportBUuid));
    }

    @Test
    void testReportDeleteCascadesToLinkedEvidence() {
        FederationClient manager = createLimitedOperator("delete_report_manager", true, true, true);
        SecurityReport report = createSecurityReport();

        String extraEvidenceUuid = createSecurityEvidence(report.entity());
        manager.addEvidenceToReport(extraEvidenceUuid, report.report());

        client.deleteReport(report.report());
        removeFromCleanup(createdReports, report.report());

        expectRequestFailure(
            () -> client.getReport(report.report()),
            new int[]{404},
            "Deleted report should not be retrievable"
        );

        try {
            client.getEvidenceRecord(extraEvidenceUuid);
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 404 || e.getStatusCode() == 403);
        }
    }

    @Test
    void testSubmitReportWithLocalFileAttachments() throws IOException {
        String entityUuid = client.pushEntity("report-local-attach-" + randomUuid().substring(0, 8) + ".com", "report_local_attach");
        createdEntities.add(entityUuid);

        Path testFile = createTempFile("report_local_", "Local attachment content");

        ReportSubmission submission = client.submitReport(entityUuid, "Report with local attachments", IncidentType.SPAM,
            null, "report_local", List.of(testFile.toString()), null);
        String reportUuid = submission.getReport().uuid();
        String evidenceUuid = submission.getEvidence().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(evidenceUuid);

        assertNotNull(submission.getReport());
        assertNotNull(submission.getEvidence());
        assertNotNull(submission.attachmentNodes());
        assertEquals(1, submission.attachmentNodes().size());

        List<FileAttachmentRecord> attachments = client.getEvidenceAttachments(evidenceUuid);
        assertEquals(1, attachments.size());
        String attachmentUuid = attachments.get(0).uuid();
        createdAttachments.add(attachmentUuid);
    }

    @Test
    void testSubmitReportWithRemoteUrlAttachment() throws IOException {
        String entityUuid = client.pushEntity("report-url-attach-" + randomUuid().substring(0, 8) + ".com", "report_url_attach");
        createdEntities.add(entityUuid);

        byte[] content = "Remote attachment content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        com.sun.net.httpserver.HttpServer httpServer = com.sun.net.httpserver.HttpServer.create(
            new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/file.txt", exchange -> {
            exchange.sendResponseHeaders(200, content.length);
            try (var os = exchange.getResponseBody()) {
                os.write(content);
            }
        });
        httpServer.start();
        try {
            String fileUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/file.txt";

            ReportSubmission submission = client.submitReport(entityUuid, "Report with URL attachment", IncidentType.SPAM,
                null, "report_url", null, List.of(fileUrl));
            String reportUuid = submission.getReport().uuid();
            String evidenceUuid = submission.getEvidence().uuid();
            createdReports.add(reportUuid);
            createdEvidenceRecords.add(evidenceUuid);

            assertNotNull(submission.getReport());
            assertNotNull(submission.getEvidence());
            assertNotNull(submission.attachmentNodes());
            assertEquals(1, submission.attachmentNodes().size());

            List<FileAttachmentRecord> attachments = client.getEvidenceAttachments(evidenceUuid);
            assertEquals(1, attachments.size());
            createdAttachments.add(attachments.get(0).uuid());
        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void testSubmitReportWithAttachmentsValidation() {
        String entityUuid = client.pushEntity("report-attach-validate-" + randomUuid().substring(0, 8) + ".com", "report_attach_validate");
        createdEntities.add(entityUuid);

        assertThrows(IllegalArgumentException.class,
            () -> client.submitReport(entityUuid, "Invalid local paths", IncidentType.SPAM,
                null, null, List.of(""), null));
        assertThrows(IllegalArgumentException.class,
            () -> client.submitReport(entityUuid, "Invalid remote urls", IncidentType.SPAM,
                null, null, null, List.of("")));
    }

    @Test
    void testListOpenedReports() {
        String entityUuid = client.pushEntity("opened-reports-" + randomUuid().substring(0, 8) + ".com", "opened_reports");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "Opened report content", IncidentType.SPAM, null, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        List<ReportRecord> openedReports = client.listOpenedReports(1, 10);
        List<String> openedUuids = openedReports.stream().map(ReportRecord::uuid).toList();
        assertTrue(openedUuids.contains(reportUuid));

        List<ReportRecord> sortedOpened = client.listOpenedReports(1, 10, "created", "DESC");
        List<String> sortedUuids = sortedOpened.stream().map(ReportRecord::uuid).toList();
        assertTrue(sortedUuids.contains(reportUuid));
    }

    @Test
    void testListOpenedReportsInvalidParams() {
        assertThrows(IllegalArgumentException.class, () -> client.listOpenedReports(0, 10));
        assertThrows(IllegalArgumentException.class, () -> client.listOpenedReports(1, 0));
        assertThrows(IllegalArgumentException.class, () -> client.listOpenedReports(1, -5, "created", "ASC"));
    }

    private String getSelfUuid(FederationClient fc) {
        return fc.getSelf().uuid();
    }
}
