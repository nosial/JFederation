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
 * Exercises every list/search overload that accepts a sort field ("by") without
 * an explicit sort order, plus the plain (page, limit) and single-identifier
 * overloads, so that all public overload shapes of the client are covered.
 */
class SortByOverloadsTest extends FederationClientTestBase {

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
    void testListAuditLogsWithByOnly() {
        String entityUuid = pushUniqueEntity("by-audit", "by_audit");

        List<AuditLog> entityEvents = client.listAuditLogs(1, 50, "ENTITY_EVENTS", "timestamp");
        assertTrue(entityEvents.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));

        List<AuditLog> otherEvents = client.listAuditLogs(1, 50, "OTHER", "timestamp");
        assertFalse(otherEvents.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));
    }

    @Test
    void testSearchAuditLogsWithByOnly() {
        String entityUuid = pushUniqueEntity("by-search-audit", "by_search_audit");
        String host = client.getEntityRecord(entityUuid).host();

        List<AuditLog> results = client.searchAuditLogs(host, 1, 50, "ENTITY_EVENTS", "timestamp");
        assertTrue(results.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));

        List<AuditLog> excluded = client.searchAuditLogs(host, 1, 50, "OPERATOR_EVENTS", "timestamp");
        assertFalse(excluded.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));
    }

    @Test
    void testListOperatorAuditLogsWithByOnly() {
        String entityUuid = pushUniqueEntity("by-op-audit", "by_op_audit");
        String selfUuid = client.getSelf().uuid();

        List<AuditLog> results = client.listOperatorAuditLogs(selfUuid, 1, 50, "ENTITY_EVENTS", "timestamp");
        assertTrue(results.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));
    }

    @Test
    void testListEntityAuditLogsWithByOnly() {
        String entityUuid = pushUniqueEntity("by-ent-audit", "by_ent_audit");

        List<AuditLog> results = client.listEntityAuditLogs(entityUuid, 1, 50, "ENTITY_EVENTS", "timestamp");
        assertTrue(results.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));

        List<AuditLog> excluded = client.listEntityAuditLogs(entityUuid, 1, 50, "BLACKLIST_EVENTS", "timestamp");
        assertFalse(excluded.stream().anyMatch(log -> entityUuid.equals(log.entityUuid())));
    }

    // ---------- Operators ----------

    @Test
    void testListOperatorsWithByOnly() {
        String operatorUuid = client.createOperator("by_list_op_" + randomUuid().substring(0, 6)).uuid();
        createdOperators.add(operatorUuid);

        List<OperatorRecord> enabled = client.listOperators(1, 100, "ENABLED", "created");
        assertTrue(enabled.stream().anyMatch(op -> operatorUuid.equals(op.uuid())));
    }

    @Test
    void testSearchOperatorsWithByOnly() {
        String name = "by_search_op_" + randomUuid().substring(0, 8);
        OperatorCreated operatorUuidCreated = client.createOperator(name);
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        List<OperatorRecord> results = client.searchOperators(name, 1, 50, "ENABLED", "created");
        assertTrue(results.stream().anyMatch(op -> operatorUuid.equals(op.uuid())));

        client.disableOperator(operatorUuid);
        List<OperatorRecord> enabled = client.searchOperators(name, 1, 50, "ENABLED", "created");
        assertFalse(enabled.stream().anyMatch(op -> operatorUuid.equals(op.uuid())));
    }

    @Test
    void testListOperatorEvidenceWithByOnly() {
        String entityUuid = pushUniqueEntity("by-op-evidence", "by_op_evidence");
        String evidenceUuid = client.submitEvidence(entityUuid, "By overload operator evidence", "note", "tag");
        createdEvidenceRecords.add(evidenceUuid);
        String selfUuid = client.getSelf().uuid();

        List<EvidenceRecord> results = client.listOperatorEvidence(selfUuid, 1, 50, true, "created");
        assertTrue(results.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));
    }

    @Test
    void testListOperatorBlacklistWithByOnly() {
        String entityUuid = pushUniqueEntity("by-op-blacklist", "by_op_blacklist");
        String evidenceUuid = client.submitEvidence(entityUuid, "By overload operator blacklist evidence", null, null);
        createdEvidenceRecords.add(evidenceUuid);
        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);
        String selfUuid = client.getSelf().uuid();

        List<BlacklistRecord> results = client.listOperatorBlacklist(selfUuid, 1, 50, true, "created");
        assertTrue(results.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));
    }

    @Test
    void testListOperatorReportsWithByOnly() {
        String entityUuid = pushUniqueEntity("by-op-reports", "by_op_reports");
        ReportSubmission submission = client.submitReport(entityUuid, "By overload operator reports content", IncidentType.SPAM);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());
        String selfUuid = client.getSelf().uuid();

        List<ReportRecord> opened = client.listOperatorReports(selfUuid, 1, 50, "OPENED", "created");
        assertTrue(opened.stream().anyMatch(r -> reportUuid.equals(r.uuid())));
    }

    @Test
    void testListAssignedOperatorReportsWithByOnly() {
        String entityUuid = pushUniqueEntity("by-assigned-reports", "by_assigned_reports");
        ReportSubmission submission = client.submitReport(entityUuid, "By overload assigned reports content", IncidentType.SPAM);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());
        String selfUuid = client.getSelf().uuid();
        client.assignOperatorToReport(reportUuid, selfUuid);

        List<ReportRecord> assigned = client.listAssignedOperatorReports(selfUuid, 1, 50, "ASSIGNED", "created");
        assertTrue(assigned.stream().anyMatch(r -> reportUuid.equals(r.uuid())));
    }

    // ---------- Entities ----------

    @Test
    void testSearchEntitiesWithByOnly() {
        String keyword = "by-search-entity-" + randomUuid().substring(0, 6);
        String entityUuid = client.pushEntity(keyword + ".com", "by_search_entity");
        createdEntities.add(entityUuid);
        client.setEntityWhitelist(entityUuid, true);

        List<EntityRecord> results = client.searchEntities(keyword, 1, 50, "WHITELISTED", "host");
        assertTrue(results.stream().anyMatch(e -> entityUuid.equals(e.uuid())));

        List<EntityRecord> notWhitelisted = client.searchEntities(keyword, 1, 50, "NOT_WHITELISTED", "host");
        assertFalse(notWhitelisted.stream().anyMatch(e -> entityUuid.equals(e.uuid())));
    }

    @Test
    void testListEntityBlacklistRecordsWithByOnly() {
        String entityUuid = pushUniqueEntity("by-ent-blacklist", "by_ent_blacklist");
        String evidenceUuid = client.submitEvidence(entityUuid, "By overload entity blacklist evidence", null, null);
        createdEvidenceRecords.add(evidenceUuid);
        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        List<BlacklistRecord> results = client.listEntityBlacklistRecords(entityUuid, 1, 50, false, "created");
        assertTrue(results.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));
    }

    @Test
    void testListEntityEvidenceRecordsWithByOnly() {
        String entityUuid = pushUniqueEntity("by-ent-evidence", "by_ent_evidence");
        String evidenceUuid = client.submitEvidence(entityUuid, "By overload entity evidence content", null, null);
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.listEntityEvidenceRecords(entityUuid, 1, 50, true, "created");
        assertTrue(results.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));
    }

    @Test
    void testListEntityReportsOverloads() {
        String entityUuid = pushUniqueEntity("by-ent-reports", "by_ent_reports");
        ReportSubmission submission = client.submitReport(entityUuid, "By overload entity reports content", IncidentType.SPAM);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        List<ReportRecord> all = client.listEntityReports(entityUuid);
        assertTrue(all.stream().anyMatch(r -> reportUuid.equals(r.uuid())));

        List<ReportRecord> opened = client.listEntityReports(entityUuid, 1, 50, "OPENED", "created");
        assertTrue(opened.stream().anyMatch(r -> reportUuid.equals(r.uuid())));

        List<ReportRecord> closed = client.listEntityReports(entityUuid, 1, 50, "CLOSED", "created");
        assertFalse(closed.stream().anyMatch(r -> reportUuid.equals(r.uuid())));
    }

    // ---------- Evidence / blacklist / attachments ----------

    @Test
    void testListEvidenceOverloads() {
        String entityUuid = pushUniqueEntity("by-evidence", "by_evidence");
        String evidenceUuid = client.submitEvidence(entityUuid, "By overload evidence content", null, null);
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> basic = client.listEvidence(1, 100);
        assertTrue(basic.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));

        List<EvidenceRecord> filtered = client.listEvidence(1, 100, true, "NOT_CONFIDENTIAL", "created");
        assertTrue(filtered.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));
    }

    @Test
    void testSearchEvidenceWithByOnly() {
        String entityUuid = pushUniqueEntity("by-search-evidence", "by_search_evidence");
        String uniqueText = "by-search-evidence-token-" + randomUuid();
        String evidenceUuid = client.submitEvidence(entityUuid, uniqueText, null, null);
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence(uniqueText, 1, 50, "NOT_CONFIDENTIAL", "created");
        assertTrue(results.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));

        List<EvidenceRecord> excluded = client.searchEvidence(uniqueText, 1, 50, "CONFIDENTIAL", "created");
        assertFalse(excluded.stream().anyMatch(ev -> evidenceUuid.equals(ev.uuid())));
    }

    @Test
    void testSearchReportsWithByOnly() {
        String entityUuid = pushUniqueEntity("by-search-reports", "by_search_reports");
        String uniqueMessage = "by-search-report-token-" + randomUuid();
        ReportSubmission submission = client.submitReport(entityUuid, "By overload search reports content", IncidentType.SPAM, uniqueMessage, null);
        String reportUuid = submission.getReport().uuid();
        createdReports.add(reportUuid);
        createdEvidenceRecords.add(submission.getEvidence().uuid());

        List<ReportRecord> opened = client.searchReports(uniqueMessage, 1, 50, "OPENED", "created");
        assertTrue(opened.stream().anyMatch(r -> reportUuid.equals(r.uuid())));

        List<ReportRecord> closed = client.searchReports(uniqueMessage, 1, 50, "CLOSED", "created");
        assertFalse(closed.stream().anyMatch(r -> reportUuid.equals(r.uuid())));
    }

    @Test
    void testListBlacklistRecordsOverloads() {
        String entityUuid = pushUniqueEntity("by-blacklist", "by_blacklist");
        String evidenceUuid = client.submitEvidence(entityUuid, "By overload blacklist evidence", null, null);
        createdEvidenceRecords.add(evidenceUuid);
        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        List<BlacklistRecord> basic = client.listBlacklistRecords(1, 100);
        assertTrue(basic.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));

        List<BlacklistRecord> filtered = client.listBlacklistRecords(1, 100, false, "ACTIVE", "created");
        assertTrue(filtered.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));

        List<BlacklistRecord> lifted = client.listBlacklistRecords(1, 100, false, "LIFTED", "created");
        assertFalse(lifted.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));
    }

    @Test
    void testSearchBlacklistWithByOnly() {
        String host = uniqueHost("by-search-blacklist");
        String entityUuid = client.pushEntity(host, "by_search_blacklist");
        createdEntities.add(entityUuid);
        String evidenceUuid = client.submitEvidence(entityUuid, "By overload search blacklist evidence", null, null);
        createdEvidenceRecords.add(evidenceUuid);
        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        List<BlacklistRecord> active = client.searchBlacklist(host, 1, 50, "ACTIVE", "created");
        assertTrue(active.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));

        List<BlacklistRecord> lifted = client.searchBlacklist(host, 1, 50, "LIFTED", "created");
        assertFalse(lifted.stream().anyMatch(bl -> blacklistUuid.equals(bl.uuid())));
    }
}
