package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.records.*;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers every overload shape made possible by the specification client's defaulted parameters
 * that is not covered elsewhere: 0-arg lists, category-only variants, (id/query, page, limit)
 * variants, and partial submitEvidence calls.
 */
class ParameterPrefixOverloadsTest extends FederationClientTestBase {

    @Test
    void testZeroArgListOverloads() {
        assertNotNull(client.listAuditLogs());
        assertNotNull(client.listOperators());
        assertNotNull(client.listEntities());
        assertNotNull(client.listEvidence());
        assertNotNull(client.listBlacklistRecords());
        assertNotNull(client.listAttachments());
        assertNotNull(client.listReports());

        assertFalse(client.listOperators().isEmpty(), "Shared server should have operators");
        assertFalse(client.listEntities().isEmpty(), "Shared server should have entities");
    }

    @Test
    void testListCategoryAndByOverloads() {
        String operatorName = "cat-overload-" + randomUuid().substring(0, 6);
        OperatorCreated operatorUuidCreated = client.createOperator(operatorName);
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);
        List<AuditLog> operatorEvents = client.listAuditLogs(1, 10, "OPERATOR_EVENTS");
        assertFalse(operatorEvents.isEmpty(), "Newest operator event should appear on page 1 with OPERATOR_EVENTS filter");
        assertTrue(operatorEvents.get(0).type().getValue().equals("OPERATOR_CREATED")
                && operatorEvents.get(0).message().contains(operatorName),
            "Newest operator event should be the creation of the new operator");
        assertTrue(client.listOperators(1, 100, "ENABLED").stream()
            .anyMatch(op -> op.uuid().equals(operatorUuid)));

        String entityUuid = client.pushEntity("cat-overload-" + randomUuid().substring(0, 6) + ".com", "cat_user");
        createdEntities.add(entityUuid);
        client.setEntityWhitelist(entityUuid, true);
        assertTrue(client.listEntities(1, 100, "WHITELISTED").stream()
            .anyMatch(e -> e.uuid().equals(entityUuid)));
        assertTrue(client.listEntities(1, 100, "WHITELISTED", "created").stream()
            .anyMatch(e -> e.uuid().equals(entityUuid)));

        String evidenceUuid = client.submitEvidence(entityUuid, "cat overload evidence", "cat note", "cat_tag");
        createdEvidenceRecords.add(evidenceUuid);
        assertTrue(client.listEvidence(1, 100, false, "NOT_CONFIDENTIAL").stream()
            .anyMatch(e -> e.uuid().equals(evidenceUuid)));

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);
        assertTrue(client.listBlacklistRecords(1, 100, true, "ACTIVE").stream()
            .anyMatch(b -> b.uuid().equals(blacklistUuid)));

        ReportSubmission submission = client.submitReport(entityUuid, "cat overload report", IncidentType.SPAM, "cat overload message");
        createdReports.add(submission.getReport().uuid());
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());
        assertTrue(client.listReports(1, 100, "OPENED").stream()
            .anyMatch(r -> r.uuid().equals(submission.getReport().uuid())));
        assertTrue(client.listReports(1, 100, "OPENED", "created").stream()
            .anyMatch(r -> r.uuid().equals(submission.getReport().uuid())));

        assertNotNull(client.listOpenedReports(1, 100, "created"));
        assertNotNull(client.listAttachments(1, 100, "DOCUMENT"));
        assertNotNull(client.listAttachments(1, 100, "DOCUMENT", "created"));
    }

    @Test
    void testEntitySubListOverloads() {
        String entityUuid = client.pushEntity("entity-sublist-" + randomUuid().substring(0, 6) + ".com", "sub_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "entity sublist evidence", "sub note", "sub_tag");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "entity sublist report", IncidentType.SPAM, "entity sublist message");
        createdReports.add(submission.getReport().uuid());
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());

        assertTrue(client.listEntityBlacklistRecords(entityUuid, 1, 100).stream()
            .anyMatch(b -> b.uuid().equals(blacklistUuid)));
        assertTrue(client.listEntityEvidenceRecords(entityUuid, 1, 100).stream()
            .anyMatch(e -> e.uuid().equals(evidenceUuid)));
        assertTrue(client.listEntityReports(entityUuid, 1, 100).stream()
            .anyMatch(r -> r.uuid().equals(submission.getReport().uuid())));
        assertTrue(client.listEntityAuditLogs(entityUuid, 1, 100, "ENTITY_EVENTS").stream()
            .anyMatch(log -> log.type().getValue().equals("ENTITY_PUSHED") && entityUuid.equals(log.entityUuid())));
    }

    @Test
    void testOperatorSubListOverloads() {
        String selfUuid = client.getSelf().uuid();

        String entityUuid = client.pushEntity("operator-sublist-" + randomUuid().substring(0, 6) + ".com", "op_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "operator sublist evidence", "op note", "op_tag");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "operator sublist report", IncidentType.SPAM, "operator sublist message");
        createdReports.add(submission.getReport().uuid());
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());
        client.assignOperatorToReport(submission.getReport().uuid(), selfUuid);

        assertTrue(client.listOperatorEvidence(selfUuid).stream()
            .anyMatch(e -> e.uuid().equals(evidenceUuid)));
        assertTrue(client.listOperatorEvidence(selfUuid, 1, 100).stream()
            .anyMatch(e -> e.uuid().equals(evidenceUuid)));
        assertTrue(client.listOperatorBlacklist(selfUuid).stream()
            .anyMatch(b -> b.uuid().equals(blacklistUuid)));
        assertTrue(client.listOperatorBlacklist(selfUuid, 1, 100).stream()
            .anyMatch(b -> b.uuid().equals(blacklistUuid)));
        assertTrue(client.listOperatorReports(selfUuid).stream()
            .anyMatch(r -> r.uuid().equals(submission.getReport().uuid())));
        assertTrue(client.listOperatorReports(selfUuid, 1, 100).stream()
            .anyMatch(r -> r.uuid().equals(submission.getReport().uuid())));
        assertTrue(client.listAssignedOperatorReports(selfUuid).stream()
            .anyMatch(r -> r.uuid().equals(submission.getReport().uuid())));
        assertTrue(client.listAssignedOperatorReports(selfUuid, 1, 100).stream()
            .anyMatch(r -> r.uuid().equals(submission.getReport().uuid())));
        OperatorCreated tokenOperatorCreated = client.createOperator("token-sublist-" + randomUuid().substring(0, 6));
        String tokenOperatorUuid = tokenOperatorCreated.uuid();
        createdOperators.add(tokenOperatorUuid);
        FederationClient tokenOperatorClient = new FederationClient(serverEndpoint,
            tokenOperatorCreated.accessToken());
        tokenOperatorClient.generateAccessToken(true);
        assertTrue(client.listOperatorAuditLogs(tokenOperatorUuid, 1, 100, "OPERATOR_EVENTS").stream()
            .anyMatch(log -> tokenOperatorUuid.equals(log.operatorUuid())
                && log.type().getValue().equals("OPERATOR_ACCESS_TOKEN_GENERATED")),
            "Fresh token generation should appear as the newest operator event");
        tokenOperatorClient.close();
    }

    @Test
    void testSearchCategoryOverloads() {
        String keyword = "search-prefix-" + randomUuid().substring(0, 6);

        String entityUuid = client.pushEntity(keyword + ".com", "prefix_user");
        createdEntities.add(entityUuid);
        client.setEntityWhitelist(entityUuid, true);

        String evidenceUuid = client.submitEvidence(entityUuid, keyword + " evidence", "prefix note", "prefix_tag");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        ReportSubmission submission = client.submitReport(entityUuid, keyword + " report", IncidentType.SPAM, keyword + " message");
        createdReports.add(submission.getReport().uuid());
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());

        OperatorCreated operatorUuidCreated = client.createOperator(keyword + "_operator");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        UploadResult attachment = client.uploadNoteAttachment(evidenceUuid, keyword + ".txt", "prefix attachment");
        createdAttachments.add(attachment.uuid());

        assertTrue(client.searchEntities(keyword, 1, 100, "WHITELISTED").stream()
            .anyMatch(e -> e.uuid().equals(entityUuid)));
        assertTrue(client.searchOperators(keyword, 1, 100, "ENABLED").stream()
            .anyMatch(op -> op.uuid().equals(operatorUuid)));
        assertTrue(client.searchEvidence(keyword, 1, 100, "NOT_CONFIDENTIAL").stream()
            .anyMatch(e -> e.uuid().equals(evidenceUuid)));
        assertTrue(client.searchReports(keyword, 1, 100, "OPENED").stream()
            .anyMatch(r -> r.uuid().equals(submission.getReport().uuid())));
        assertTrue(client.searchBlacklist(keyword, 1, 100, "ACTIVE").stream()
            .anyMatch(b -> b.uuid().equals(blacklistUuid)));
        if (isAttachmentSearchEnabled()) {
            assertTrue(client.searchAttachments(keyword, 1, 100, "DOCUMENT").stream()
                .anyMatch(a -> a.uuid().equals(attachment.uuid())));
        }
        assertTrue(client.searchAuditLogs(keyword, 1, 100, "ENTITY_EVENTS").stream()
            .anyMatch(log -> log.type().getValue().equals("ENTITY_PUSHED") && entityUuid.equals(log.entityUuid())));
    }

    @Test
    void testSubmitEvidencePartialArgs() {
        String entityUuid = client.pushEntity("submit-prefix-" + randomUuid().substring(0, 6) + ".com", "submit_user");
        createdEntities.add(entityUuid);

        String textOnlyUuid = client.submitEvidence(entityUuid, "text only content");
        createdEvidenceRecords.add(textOnlyUuid);
        EvidenceRecord textOnly = client.getEvidenceRecord(textOnlyUuid);
        assertEquals("text only content", textOnly.textContent());
        assertFalse(textOnly.confidential());

        String textAndNoteUuid = client.submitEvidence(entityUuid, "text and note content", "provided note");
        createdEvidenceRecords.add(textAndNoteUuid);
        EvidenceRecord textAndNote = client.getEvidenceRecord(textAndNoteUuid);
        assertEquals("text and note content", textAndNote.textContent());
        assertEquals("provided note", textAndNote.note());
        assertFalse(textAndNote.confidential());
    }
}
