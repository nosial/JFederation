package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.records.AuditLog;
import net.nosial.jfederation.records.BlacklistRecord;
import net.nosial.jfederation.records.EntityRecord;
import net.nosial.jfederation.records.EvidenceRecord;
import net.nosial.jfederation.records.FileAttachmentRecord;
import net.nosial.jfederation.records.OperatorRecord;
import net.nosial.jfederation.records.ReportRecord;
import net.nosial.jfederation.records.ReportSubmission;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises every list/search overload that accepts category/by/order parameters,
 * asserting both positive (record present under the matching filter) and negative
 * (record absent under a non-matching filter) behavior. DESC ordering on "created"
 * keeps freshly created records on page 1.
 */
class SortCategoryCoverageTest extends FederationClientTestBase {

    private String uniqueHost(String prefix) {
        return prefix + "-" + randomUuid().substring(0, 8) + ".com";
    }

    private String pushUniqueEntity(String prefix, String id) {
        String uuid = client.pushEntity(uniqueHost(prefix), id);
        createdEntities.add(uuid);
        return uuid;
    }

    // ---------- Audit logs ----------

    @Test
    void testListAuditLogsWithCategoryAndSort() {
        String entityUuid = pushUniqueEntity("list-audit", "list_audit");

        List<AuditLog> entityEvents = client.listAuditLogs(1, 50, "ENTITY_EVENTS", "timestamp", "DESC");
        assertTrue(entityEvents.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));

        List<AuditLog> otherEvents = client.listAuditLogs(1, 50, "OTHER", "timestamp", "DESC");
        assertFalse(otherEvents.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));
    }

    @Test
    void testSearchAuditLogsWithCategoryAndSort() {
        String entityUuid = pushUniqueEntity("search-audit", "search_audit");
        String host = client.getEntityRecord(entityUuid).host();

        List<AuditLog> results = client.searchAuditLogs(host, 1, 50, "ENTITY_EVENTS", "timestamp", "DESC");
        assertTrue(results.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));

        List<AuditLog> excluded = client.searchAuditLogs(host, 1, 50, "OPERATOR_EVENTS", "timestamp", "DESC");
        assertFalse(excluded.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));
    }

    @Test
    void testListOperatorAuditLogsWithCategoryAndSort() {
        String entityUuid = pushUniqueEntity("op-audit", "op_audit");
        String selfUuid = client.getSelf().uuid();

        List<AuditLog> results = client.listOperatorAuditLogs(selfUuid, 1, 50, "ENTITY_EVENTS", "timestamp", "DESC");
        assertTrue(results.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));
    }

    @Test
    void testListEntityAuditLogsWithCategoryAndSort() {
        String entityUuid = pushUniqueEntity("ent-audit", "ent_audit");

        List<AuditLog> results = client.listEntityAuditLogs(entityUuid, 1, 50, "ENTITY_EVENTS", "timestamp", "DESC");
        assertTrue(results.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));

        List<AuditLog> excluded = client.listEntityAuditLogs(entityUuid, 1, 50, "BLACKLIST_EVENTS", "timestamp", "DESC");
        assertFalse(excluded.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));
    }

    // ---------- Operators ----------

    @Test
    void testSearchOperatorsWithCategoryAndSort() {
        String name = "search_op_" + randomUuid().substring(0, 8);
        OperatorCreated operatorUuidCreated = client.createOperator(name);
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        List<OperatorRecord> results = client.searchOperators(name, 1, 50, "ENABLED", "created", "DESC");
        assertTrue(results.stream().anyMatch(op -> operatorUuid.equals(op.uuid())));

        client.disableOperator(operatorUuid);
        List<OperatorRecord> disabled = client.searchOperators(name, 1, 50, "DISABLED", "created", "DESC");
        assertTrue(disabled.stream().anyMatch(op -> operatorUuid.equals(op.uuid())));

        List<OperatorRecord> enabled = client.searchOperators(name, 1, 50, "ENABLED", "created", "DESC");
        assertFalse(enabled.stream().anyMatch(op -> operatorUuid.equals(op.uuid())));
    }

    @Test
    void testListOperatorEvidenceWithSort() {
        String entityUuid = pushUniqueEntity("op-evidence", "op_evidence");
        String evidenceUuid = client.submitEvidence(entityUuid, "Operator evidence content", "note", "tag");
        createdEvidenceRecords.add(evidenceUuid);
        String selfUuid = client.getSelf().uuid();

        List<EvidenceRecord> results = client.listOperatorEvidence(selfUuid, 1, 50, true, "created", "DESC");
        assertTrue(results.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));
    }

    @Test
    void testListOperatorBlacklistWithSort() {
        String entityUuid = pushUniqueEntity("op-blacklist", "op_blacklist");
        String reportUuid = createReportForEntity(entityUuid);
        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, reportUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);
        String selfUuid = client.getSelf().uuid();

        List<BlacklistRecord> results = client.listOperatorBlacklist(selfUuid, 1, 50, true, "created", "DESC");
        assertTrue(results.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));
    }

    @Test
    void testListOperatorReportsWithCategoryAndSort() {
        String entityUuid = pushUniqueEntity("op-reports", "op_reports");
        ReportSubmission submission = client.submitReport(entityUuid, "Operator reports content", IncidentType.SPAM);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());
        String selfUuid = client.getSelf().uuid();

        List<ReportRecord> opened = client.listOperatorReports(selfUuid, 1, 50, "OPENED", "created", "DESC");
        assertTrue(opened.stream().anyMatch(r -> reportUuid.equals(r.uuid())));

        List<ReportRecord> closed = client.listOperatorReports(selfUuid, 1, 50, "CLOSED", "created", "DESC");
        assertFalse(closed.stream().anyMatch(r -> reportUuid.equals(r.uuid())));
    }

    @Test
    void testListAssignedOperatorReportsWithCategoryAndSort() {
        String entityUuid = pushUniqueEntity("assigned-reports", "assigned_reports");
        ReportSubmission submission = client.submitReport(entityUuid, "Assigned reports content", IncidentType.SPAM);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());
        String selfUuid = client.getSelf().uuid();
        client.assignOperatorToReport(reportUuid, selfUuid);

        List<ReportRecord> assigned = client.listAssignedOperatorReports(selfUuid, 1, 50, "ASSIGNED", "created", "DESC");
        assertTrue(assigned.stream().anyMatch(r -> reportUuid.equals(r.uuid())));
    }

    // ---------- Entities ----------

    @Test
    void testListEntityBlacklistRecordsWithSort() {
        String entityUuid = pushUniqueEntity("ent-blacklist", "ent_blacklist");
        String reportUuid = createReportForEntity(entityUuid);
        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, reportUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        List<BlacklistRecord> results = client.listEntityBlacklistRecords(entityUuid, 1, 50, false, "created", "DESC");
        assertTrue(results.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));
    }

    @Test
    void testListEntityEvidenceRecordsWithSort() {
        String entityUuid = pushUniqueEntity("ent-evidence", "ent_evidence");
        String evidenceUuid = client.submitEvidence(entityUuid, "Entity evidence records content", null, null);
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.listEntityEvidenceRecords(entityUuid, 1, 50, true, "created", "DESC");
        assertTrue(results.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));
    }

    @Test
    void testListEntityReportsWithCategoryAndSort() {
        String entityUuid = pushUniqueEntity("ent-reports", "ent_reports");
        ReportSubmission submission = client.submitReport(entityUuid, "Entity reports content", IncidentType.SPAM);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());

        List<ReportRecord> opened = client.listEntityReports(entityUuid, 1, 50, "OPENED", "created", "DESC");
        assertTrue(opened.stream().anyMatch(r -> reportUuid.equals(r.uuid())));

        List<ReportRecord> closed = client.listEntityReports(entityUuid, 1, 50, "CLOSED", "created", "DESC");
        assertFalse(closed.stream().anyMatch(r -> reportUuid.equals(r.uuid())));
    }

    // ---------- Evidence / reports / blacklist searches ----------

    @Test
    void testSearchEvidenceWithCategoryAndSort() {
        String entityUuid = pushUniqueEntity("search-evidence", "search_evidence");
        String uniqueText = "search-evidence-token-" + randomUuid();
        String evidenceUuid = client.submitEvidence(entityUuid, uniqueText, null, null);
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence(uniqueText, 1, 50, "NOT_CONFIDENTIAL", "created", "DESC");
        assertTrue(results.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));

        List<EvidenceRecord> excluded = client.searchEvidence(uniqueText, 1, 50, "CONFIDENTIAL", "created", "DESC");
        assertFalse(excluded.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));
    }

    @Test
    void testSearchReportsWithCategoryAndSort() {
        String entityUuid = pushUniqueEntity("search-reports", "search_reports");
        String uniqueMessage = "search-report-token-" + randomUuid();
        ReportSubmission submission = client.submitReport(entityUuid, "Search reports content", IncidentType.SPAM, uniqueMessage);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());

        List<ReportRecord> opened = client.searchReports(uniqueMessage, 1, 50, "OPENED", "created", "DESC");
        assertTrue(opened.stream().anyMatch(r -> reportUuid.equals(r.uuid())));

        List<ReportRecord> closed = client.searchReports(uniqueMessage, 1, 50, "CLOSED", "created", "DESC");
        assertFalse(closed.stream().anyMatch(r -> reportUuid.equals(r.uuid())));
    }

    @Test
    void testSearchBlacklistWithCategoryAndSort() {
        String host = uniqueHost("search-blacklist");
        String entityUuid = client.pushEntity(host, "search_blacklist");
        createdEntities.add(entityUuid);
        String reportUuid = createReportForEntity(entityUuid);
        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, reportUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        List<BlacklistRecord> active = client.searchBlacklist(host, 1, 50, "ACTIVE", "created", "DESC");
        assertTrue(active.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));

        List<BlacklistRecord> lifted = client.searchBlacklist(host, 1, 50, "LIFTED", "created", "DESC");
        assertFalse(lifted.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));
    }

    @Test
    void testSortOverloadsRejectInvalidPagination() {
        assertThrows(IllegalArgumentException.class, () -> client.listAuditLogs(0, 10, "ENTITY_EVENTS", "timestamp", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.listAuditLogs(1, -1, "ENTITY_EVENTS", "timestamp", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchAuditLogs("q", 0, 10, "ENTITY_EVENTS", "timestamp", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.listOperators(0, 10, "ENABLED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchOperators("q", 1, 0, "ENABLED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.listEntities(0, 10, "WHITELISTED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchEntities("q", 0, 10, "WHITELISTED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.listEvidence(1, -1, true, "CONFIDENTIAL", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchEvidence("q", 0, 10, "CONFIDENTIAL", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.listReports(0, 10, "OPENED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchReports("q", 0, 10, "OPENED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.listBlacklistRecords(1, -1, false, "ACTIVE", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchBlacklist("q", 0, 10, "ACTIVE", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.listAttachments(0, 10, "DOCUMENT", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchAttachments("q", 0, 10, "DOCUMENT", "created", "DESC"));
    }

    @Test
    void testSortOverloadsRejectEmptyIdentifiers() {
        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorAuditLogs("", 1, 10, "ENTITY_EVENTS", "timestamp", "DESC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorEvidence("", 1, 10, true, "created", "DESC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorBlacklist("", 1, 10, true, "created", "DESC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.listOperatorReports("", 1, 10, "OPENED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.listAssignedOperatorReports("", 1, 10, "OPENED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.listEntityAuditLogs("", 1, 10, "ENTITY_EVENTS", "timestamp", "DESC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.listEntityBlacklistRecords("", 1, 10, false, "created", "DESC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.listEntityEvidenceRecords("", 1, 10, true, "created", "DESC"));
        assertThrows(IllegalArgumentException.class,
            () -> client.listEntityReports("", 1, 10, "OPENED", "created", "DESC"));
    }

    @Test
    void testSearchOverloadsRejectShortQueries() {
        assertThrows(IllegalArgumentException.class, () -> client.searchAuditLogs("", 1, 10, "ENTITY_EVENTS", "timestamp", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchAuditLogs("a", 1, 10, "ENTITY_EVENTS", "timestamp", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchOperators("", 1, 10, "ENABLED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchEntities("", 1, 10, "WHITELISTED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchEvidence("", 1, 10, "CONFIDENTIAL", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchReports("", 1, 10, "OPENED", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchBlacklist("", 1, 10, "ACTIVE", "created", "DESC"));
        assertThrows(IllegalArgumentException.class, () -> client.searchAttachments("", 1, 10, "DOCUMENT", "created", "DESC"));
    }
}
