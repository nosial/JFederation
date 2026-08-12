package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.RecordType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.*;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SearchTest extends FederationClientTestBase {

    @Test
    void testSearchEntitiesByHost() {
        String host = "search-host-" + randomUuid().substring(0, 8) + ".com";
        String entityUuid = client.pushEntity(host, "user_a");
        createdEntities.add(entityUuid);

        client.pushEntity(host, "user_b");
        createdEntities.add(entityUuid);

        List<EntityRecord> results = client.searchEntities(host, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        for (EntityRecord result : results) {
            assertEquals(host, result.host());
        }
    }

    @Test
    void testSearchEntitiesById() {
        String id = "search_id_" + randomUuid().substring(0, 8);
        String entityUuid = client.pushEntity("search-id-test.com", id);
        createdEntities.add(entityUuid);

        List<EntityRecord> results = client.searchEntities(id, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        for (EntityRecord result : results) {
            assertEquals(id, result.id());
        }
    }

    @Test
    void testSearchEntitiesByUuid() {
        String entityUuid = client.pushEntity("search-uuid-test.com", "uuid_user");
        createdEntities.add(entityUuid);

        String prefix = entityUuid.substring(0, 8);
        List<EntityRecord> results = client.searchEntities(prefix, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean found = false;
        for (EntityRecord result : results) {
            if (result.uuid().equals(entityUuid)) {
                found = true;
                break;
            }
        }
        assertTrue(found, "Created entity should be found by UUID prefix search");
    }

    @Test
    void testSearchEvidenceByContent() {
        String entityUuid = client.pushEntity("search-evidence-content-" + randomUuid().substring(0, 8) + ".com", "content_user");
        createdEntities.add(entityUuid);

        String uniqueContent = "X1 evidence content " + randomUuid();
        String evidenceUuid = client.submitEvidence(entityUuid, uniqueContent, "content test", "content_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence(uniqueContent, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());

        List<String> texts = results.stream().map(EvidenceRecord::textContent).collect(Collectors.toList());
        assertTrue(texts.contains(uniqueContent));
    }

    @Test
    void testSearchEvidenceByTag() {
        String entityUuid = client.pushEntity("search-evidence-tag-" + randomUuid().substring(0, 8) + ".com", "tag_user");
        createdEntities.add(entityUuid);

        String tag = "xtag_" + randomUuid().substring(0, 8);
        String evidenceUuid = client.submitEvidence(entityUuid, "tag content", "tag note", tag);
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence(tag, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        for (EvidenceRecord result : results) {
            assertEquals(tag, result.tag());
        }
    }

    @Test
    void testSearchEvidenceByEntityUuid() {
        String entityUuid = client.pushEntity("search-evidence-entity-" + randomUuid().substring(0, 8) + ".com", "entity_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "entity search test", "entity note", "entity_tag");
        createdEvidenceRecords.add(evidenceUuid);

        String prefix = entityUuid.substring(0, 8);
        List<EvidenceRecord> results = client.searchEvidence(prefix, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        for (EvidenceRecord result : results) {
            assertEquals(entityUuid, result.entityUuid());
        }
    }

    @Test
    void testSearchBlacklistByEntityUuid() {
        String entityUuid = client.pushEntity("search-blacklist-" + randomUuid().substring(0, 8) + ".com", "bl_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "bl content", "bl note", "bl_tag");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        String prefix = entityUuid.substring(0, 8);
        List<BlacklistRecord> results = client.searchBlacklist(prefix, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        for (BlacklistRecord result : results) {
            assertEquals(entityUuid, result.entityUuid());
        }
    }

    @Test
    void testSearchReportsByReportingEntity() {
        String host = "search-reports-entity-" + randomUuid().substring(0, 8) + ".com";
        String entityUuid = client.pushEntity(host, "rep_entity_user");
        createdEntities.add(entityUuid);

        ReportSubmission submission = client.submitReport(entityUuid, "report content 2", IncidentType.SPAM, "search by entity");
        createdReports.add(submission.getReport().uuid());
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());

        List<ReportRecord> results = client.searchReports(entityUuid, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchAttachmentsByFileName() {
        String entityUuid = client.pushEntity("search-attach-" + randomUuid().substring(0, 8) + ".com", "attach_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "attach content", "attach note", "attach_tag");
        createdEvidenceRecords.add(evidenceUuid);

        String fileName = "search_test_file_" + randomUuid().substring(0, 8) + ".txt";
        java.nio.file.Path filePath = createTempFile(fileName, "attachment search content");
        UploadResult uploadResult = client.uploadFileAttachment(evidenceUuid, filePath.toString(), fileName);
        createdAttachments.add(uploadResult.uuid());

        try {
            List<FileAttachmentRecord> results = client.searchAttachments(fileName, 1, 10);
            assertNotNull(results);
            assertFalse(results.isEmpty());
            for (FileAttachmentRecord result : results) {
                assertEquals(fileName, result.fileName());
            }
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 404,
                "Expected 400 or 404 when attachment search is disabled");
        }
    }

    @Test
    void testSearchOperatorsByName() {
        String name = "search_op_" + randomUuid().substring(0, 8);
        OperatorCreated operatorUuidCreated = client.createOperator(name);
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        List<OperatorRecord> results = client.searchOperators(name, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());
        for (OperatorRecord result : results) {
            assertEquals(name, result.name());
        }
    }

    @Test
    void testSearchEmptyQuery() {
        try {
            client.searchEntities("", 1, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testSearchSingleCharacterQuery() {
        try {
            client.searchEntities("a", 1, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testSearchWhitespaceQuery() {
        try {
            List<EntityRecord> results = client.searchEntities("  ", 1, 10);
            assertTrue(results == null || results.isEmpty(), "Expected empty results for whitespace query");
        } catch (IllegalArgumentException e) {
            // expected
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 422,
                "Server should reject whitespace-only search query");
        }
    }

    @Test
    void testSearchInvalidPageZero() {
        try {
            client.searchEntities("test", 0, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testSearchInvalidPageNegative() {
        try {
            client.searchEntities("test", -1, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testSearchInvalidLimitZero() {
        try {
            client.searchEntities("test", 1, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testSearchInvalidLimitNegative() {
        try {
            client.searchEntities("test", 1, -5);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testSearchReturnsEmptyResultsForNonExistentQuery() {
        List<EntityRecord> results = client.searchEntities("zzzzthisdoesnotexist_" + randomUuid(), 1, 10);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchEvidenceReturnsEmptyForNonExistentContent() {
        List<EvidenceRecord> results = client.searchEvidence("nonexistent_evidence_" + randomUuid(), 1, 10);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchBlacklistEmptyForNonExistentEntity() {
        List<BlacklistRecord> results = client.searchBlacklist("nonexistent_blacklist_" + randomUuid(), 1, 10);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchReportsEmptyForNonExistentMessage() {
        List<ReportRecord> results = client.searchReports("nonexistent_report_" + randomUuid(), 1, 10);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchOperatorsEmptyForNonExistentName() {
        List<OperatorRecord> results = client.searchOperators("nonexistent_operator_" + randomUuid(), 1, 10);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testSearchAttachmentsEmptyForNonExistentFileName() {
        try {
            List<FileAttachmentRecord> results = client.searchAttachments("nonexistent_attachment_" + randomUuid(), 1, 10);
            assertNotNull(results);
            assertTrue(results.isEmpty());
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 404);
        }
    }

    @Test
    void testSearchWithPercentSymbol() {
        String entityUuid = client.pushEntity("percent-test.com", "100%_user");
        createdEntities.add(entityUuid);

        List<EntityRecord> results = client.searchEntities("100%", 1, 10);
        assertNotNull(results);
    }

    @Test
    void testSearchWithUnderscore() {
        String entityUuid = client.pushEntity("underscore-test.com", "my_user_name");
        createdEntities.add(entityUuid);

        List<EntityRecord> results = client.searchEntities("my_user", 1, 10);
        assertNotNull(results);
    }

    @Test
    void testSearchWithSqlInjectionPattern() {
        String entityUuid = client.pushEntity("sql-inject.com", "admin");
        createdEntities.add(entityUuid);

        String[] payloads = {
            "'; DROP TABLE entities; --",
            "' OR '1'='1",
            "' UNION SELECT * FROM operators --",
        };

        for (String payload : payloads) {
            try {
                List<EntityRecord> results = client.searchEntities(payload, 1, 10);
                assertNotNull(results);
            } catch (FederationClientException e) {
                assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 422 || e.getStatusCode() == 500);
            }
        }
    }

    @Test
    void testSearchWithUnicodeCharacters() {
        String entityUuid = client.pushEntity("unicode-test.com", "caf\u00e9_\u00f1u\u00f1o");
        createdEntities.add(entityUuid);

        List<EntityRecord> results = client.searchEntities("caf\u00e9", 1, 10);
        assertNotNull(results);
    }

    @Test
    void testSearchPartialHostMatch() {
        String base = "partial-" + randomUuid().substring(0, 8);
        String host = base + ".com";
        String entityUuid = client.pushEntity(host, "partial_user");
        createdEntities.add(entityUuid);

        List<EntityRecord> results = client.searchEntities(base, 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchPartialEvidenceContentMatch() {
        String entityUuid = client.pushEntity("partial-evidence-" + randomUuid().substring(0, 8) + ".com", "partial_ev_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "The quick brown fox jumps over the lazy dog", "partial note", "partial_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("quick brown", 1, 10);
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchEntitiesPagination() {
        String host = "pagination-" + randomUuid().substring(0, 8) + ".com";
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String uuid = client.pushEntity(host, "pag_user_" + i);
            createdEntities.add(uuid);
            created.add(uuid);
        }

        List<EntityRecord> page1 = client.searchEntities(host, 1, 3);
        assertEquals(3, page1.size());

        List<EntityRecord> page2 = client.searchEntities(host, 2, 3);
        assertEquals(3, page2.size());

        List<EntityRecord> page3 = client.searchEntities(host, 3, 3);
        assertEquals(2, page3.size());

        List<String> allUuids = new ArrayList<>();
        page1.forEach(e -> allUuids.add(e.uuid()));
        page2.forEach(e -> allUuids.add(e.uuid()));
        page3.forEach(e -> allUuids.add(e.uuid()));

        for (String uuid : created) {
            assertTrue(allUuids.contains(uuid), "Entity " + uuid + " not found across paginated results");
        }
    }

    @Test
    void testSearchPaginationConsistency() {
        String host = "consistency-" + randomUuid().substring(0, 8) + ".com";
        for (int i = 0; i < 5; i++) {
            String uuid = client.pushEntity(host, "cons_user_" + i);
            createdEntities.add(uuid);
        }

        List<EntityRecord> first = client.searchEntities(host, 1, 3);
        List<EntityRecord> second = client.searchEntities(host, 1, 3);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).uuid(), second.get(i).uuid());
        }
    }

    @Test
    void testSearchPaginationNoOverlap() {
        String host = "no-overlap-" + randomUuid().substring(0, 8) + ".com";
        for (int i = 0; i < 6; i++) {
            String uuid = client.pushEntity(host, "nolap_user_" + i);
            createdEntities.add(uuid);
        }

        List<EntityRecord> page1 = client.searchEntities(host, 1, 3);
        List<EntityRecord> page2 = client.searchEntities(host, 2, 3);

        List<String> page1Uuids = page1.stream().map(EntityRecord::uuid).collect(Collectors.toList());
        List<String> page2Uuids = page2.stream().map(EntityRecord::uuid).collect(Collectors.toList());

        List<String> intersection = new ArrayList<>(page1Uuids);
        intersection.retainAll(page2Uuids);
        assertTrue(intersection.isEmpty(), "Entities appeared in multiple pages");
    }

    @Test
    void testSearchPageFarBeyondResults() {
        String host = "far-page-" + randomUuid().substring(0, 8) + ".com";
        for (int i = 0; i < 3; i++) {
            String uuid = client.pushEntity(host, "far_user_" + i);
            createdEntities.add(uuid);
        }

        List<EntityRecord> results = client.searchEntities(host, 100, 10);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testMultiTypeSearchReturnsCorrectTypes() {
        String entityUuid = client.pushEntity("multi-test-" + randomUuid().substring(0, 8) + ".com", "multi_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "multi type evidence content", "multi note", "multi_tag");
        createdEvidenceRecords.add(evidenceUuid);

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        List<SearchResult> results = client.search("multi", null, 1, 100);
        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean foundEntity = false;
        for (SearchResult result : results) {
            assertNotNull(result.type());
            if (result.type() == RecordType.ENTITY) {
                foundEntity = true;
                EntityRecord record = result.getRecord();
                assertNotNull(record);
            }
        }
        assertTrue(foundEntity, "Multi-type search should include entity results");
    }

    @Test
    void testMultiTypeSearchWithTypeFilter() {
        String entityUuid = client.pushEntity("type-filter-" + randomUuid().substring(0, 8) + ".com", "tf_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "type filter evidence", "tf note", "tf_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<SearchResult> results = client.search("type filter", List.of(RecordType.EVIDENCE), 1, 100);
        assertNotNull(results);
        assertFalse(results.isEmpty());

        for (SearchResult result : results) {
            assertEquals(RecordType.EVIDENCE, result.type());
        }
    }

    @Test
    void testMultiTypeSearchWithMultipleTypeFilters() {
        String host = "multi-filter-test-" + randomUuid().substring(0, 8) + ".com";
        String entityUuid = client.pushEntity(host, "mf_user");
        createdEntities.add(entityUuid);

        String searchKeyword = host.substring(0, host.indexOf(".com"));
        String evidenceUuid = client.submitEvidence(entityUuid, searchKeyword + " evidence", "mf note", "mf_tag");
        createdEvidenceRecords.add(evidenceUuid);

        String operatorName = "mf_op_" + randomUuid().substring(0, 8);
        OperatorCreated operatorUuidCreated = client.createOperator(operatorName);
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        List<SearchResult> results = client.search(searchKeyword, Arrays.asList(RecordType.ENTITY, RecordType.EVIDENCE), 1, 100);
        assertNotNull(results);
        assertFalse(results.isEmpty());

        boolean foundEntity = false;
        boolean foundEvidence = false;
        for (SearchResult result : results) {
            if (result.type() == RecordType.ENTITY) {
                foundEntity = true;
                EntityRecord record = result.getRecord();
                assertTrue(record.host().contains(searchKeyword));
            } else if (result.type() == RecordType.EVIDENCE) {
                foundEvidence = true;
            }
        }
        assertTrue(foundEntity, "Multi-filter search should include entity results");
        assertTrue(foundEvidence, "Multi-filter search should include evidence results");

        for (SearchResult result : results) {
            assertNotEquals(RecordType.OPERATOR, result.type(),
                "Operator results should not appear when filtering to ENTITY and EVIDENCE only");
        }
    }

    @Test
    void testMultiTypeSearchReturnsSearchResultObjects() {
        String entityUuid = client.pushEntity("search-result-obj.com", "sro_user");
        createdEntities.add(entityUuid);

        List<SearchResult> results = client.search("search-result-obj");
        assertNotNull(results);
        assertFalse(results.isEmpty());
        SearchResult result = results.get(0);
        assertEquals(RecordType.ENTITY, result.type());
        EntityRecord record = result.getRecord();
        assertEquals(entityUuid, record.uuid());
    }

    @Test
    void testTypedGetRecordForAllSearchableTypes() {
        String keyword = "typed-record-" + randomUuid().substring(0, 8);

        String entityUuid = client.pushEntity(keyword + ".com", "typed_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, keyword + " evidence content", "typed note", "typed_tag");
        createdEvidenceRecords.add(evidenceUuid);

        ReportSubmission submission = client.submitReport(entityUuid, keyword + " report content",
            IncidentType.SPAM, keyword + " message");
        createdReports.add(submission.getReport().uuid());
        createdEvidenceRecords.add(submission.getEvidence().get(0).uuid());

        int expires = (int) (System.currentTimeMillis() / 1000 + 3600);
        String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expires);
        createdBlacklistRecords.add(blacklistUuid);

        OperatorCreated operatorUuidCreated = client.createOperator(keyword + "_operator");
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        UploadResult attachment = client.uploadNoteAttachment(evidenceUuid, keyword + ".txt", "typed attachment content");
        createdAttachments.add(attachment.uuid());

        Map<RecordType, Class<?>> expectedTypes = Map.of(
            RecordType.ENTITY, EntityRecord.class,
            RecordType.EVIDENCE, EvidenceRecord.class,
            RecordType.REPORT, ReportRecord.class,
            RecordType.BLACKLIST, BlacklistRecord.class,
            RecordType.ATTACHMENT, FileAttachmentRecord.class,
            RecordType.OPERATOR, OperatorRecord.class,
            RecordType.AUDIT_LOG, AuditLog.class
        );

        for (Map.Entry<RecordType, Class<?>> entry : expectedTypes.entrySet()) {
            if (entry.getKey() == RecordType.ATTACHMENT && !isAttachmentSearchEnabled()) {
                continue;
            }
            List<SearchResult> results = client.search(keyword, List.of(entry.getKey()), 1, 100);
            assertFalse(results.isEmpty(),
                "Search for type " + entry.getKey() + " with keyword '" + keyword + "' returned no results");
            for (SearchResult result : results) {
                assertEquals(entry.getKey(), result.type(),
                    "Result type should match the requested type filter");
                assertTrue(entry.getValue().isInstance(result.getRecord()),
                    "getRecord() for type " + entry.getKey() + " should return "
                        + entry.getValue().getSimpleName() + " but got "
                        + result.getRecord().getClass().getSimpleName());
            }
        }
    }

    @Test
    void testMultiTypeSearchEmptyForNonExistentQuery() {
        List<SearchResult> results = client.search("zzz_nonexistent_query_" + randomUuid());
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    void testMultiTypeSearchInvalidParameters() {
        try {
            client.search("");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testEntitySearchDoesNotReturnEvidence() {
        String entityUuid = client.pushEntity("cross-type.com", "ct_user");
        createdEntities.add(entityUuid);

        String uniqueEvidenceContent = "CROSS_TYPE_EVIDENCE_" + randomUuid();
        String evidenceUuid = client.submitEvidence(entityUuid, uniqueEvidenceContent, "ct note", "ct_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EntityRecord> entityResults = client.searchEntities(uniqueEvidenceContent, 1, 10);
        assertTrue(entityResults.isEmpty(), "Entity search should not return evidence content matches");

        List<EvidenceRecord> evidenceResults = client.searchEvidence(uniqueEvidenceContent, 1, 10);
        assertFalse(evidenceResults.isEmpty(), "Evidence search should find the evidence");
    }

    @Test
    void testOperatorSearchDoesNotReturnEntities() {
        String entityUuid = client.pushEntity("operator-x-search.com", "opx_user");
        createdEntities.add(entityUuid);

        String opName = "opx_name_" + randomUuid().substring(0, 8);
        OperatorCreated operatorUuidCreated = client.createOperator(opName);
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        List<EntityRecord> entityResults = client.searchEntities(opName, 1, 10);
        assertTrue(entityResults.isEmpty(), "Entity search should not return operators by name");

        List<OperatorRecord> opResults = client.searchOperators(opName, 1, 10);
        assertFalse(opResults.isEmpty(), "Operator search should find the operator");
    }

    @Test
    void testDeletedEntityDoesNotAppearInSearch() {
        String entityUuid = client.pushEntity("delete-search.com", "delete_user");
        createdEntities.add(entityUuid);

        List<EntityRecord> results = client.searchEntities("delete-search.com", 1, 10);
        assertFalse(results.isEmpty());

        client.deleteEntity(entityUuid);
        removeFromCleanup(createdEntities, entityUuid);

        List<EntityRecord> resultsAfterDelete = client.searchEntities("delete-search.com", 1, 10);
        boolean found = false;
        for (EntityRecord result : resultsAfterDelete) {
            if (result.uuid().equals(entityUuid)) {
                found = true;
                break;
            }
        }
        assertFalse(found, "Deleted entity should not appear in search results");
    }

    @Test
    void testDeletedEvidenceDoesNotAppearInSearch() {
        String entityUuid = client.pushEntity("delete-evidence-search-" + randomUuid().substring(0, 8) + ".com", "del_ev_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "delete evidence content", "delete note", "delete_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("delete evidence content", 1, 10);
        assertFalse(results.isEmpty());

        client.deleteEvidence(evidenceUuid);
        removeFromCleanup(createdEvidenceRecords, evidenceUuid);

        List<EvidenceRecord> resultsAfterDelete = client.searchEvidence("delete evidence content", 1, 10);
        boolean found = false;
        for (EvidenceRecord result : resultsAfterDelete) {
            if (result.uuid().equals(evidenceUuid)) {
                found = true;
                break;
            }
        }
        assertFalse(found, "Deleted evidence should not appear in search results");
    }

    @Test
    void testSearchMultipleEntitiesSameHost() {
        String host = "multi-same-" + randomUuid().substring(0, 8) + ".com";
        int count = 5;
        List<String> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String uuid = client.pushEntity(host, "multi_same_user_" + i);
            createdEntities.add(uuid);
            created.add(uuid);
        }

        List<EntityRecord> results = client.searchEntities(host, 1, 100);
        assertTrue(results.size() >= count);

        List<String> resultUuids = results.stream().map(EntityRecord::uuid).collect(Collectors.toList());
        for (String uuid : created) {
            assertTrue(resultUuids.contains(uuid), "Entity " + uuid + " should be in search results");
        }
    }

    @Test
    void testSearchMultipleEvidenceSameTag() {
        String entityUuid = client.pushEntity("multi-evidence-tag-" + randomUuid().substring(0, 8) + ".com", "multi_ev_user");
        createdEntities.add(entityUuid);

        String tag = "common_tag_" + randomUuid().substring(0, 8);
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String uuid = client.submitEvidence(entityUuid, "common tag content " + i, "common note " + i, tag);
            createdEvidenceRecords.add(uuid);
            created.add(uuid);
        }

        List<EvidenceRecord> results = client.searchEvidence(tag, 1, 100);
        assertTrue(results.size() >= created.size());

        List<String> resultUuids = results.stream().map(EvidenceRecord::uuid).collect(Collectors.toList());
        for (String uuid : created) {
            assertTrue(resultUuids.contains(uuid));
        }
    }

    @Test
    void testSearchEntityThenRetrieveFullRecord() {
        String entityUuid = client.pushEntity("verify-search.com", "verify_user");
        createdEntities.add(entityUuid);

        List<EntityRecord> results = client.searchEntities(entityUuid, 1, 10);
        assertFalse(results.isEmpty());

        EntityRecord fullRecord = client.getEntityRecord(entityUuid);
        assertEquals(fullRecord.uuid(), results.get(0).uuid());
        assertEquals(fullRecord.host(), results.get(0).host());
        assertEquals(fullRecord.id(), results.get(0).id());
    }

    @Test
    void testSearchEvidenceThenRetrieveFullRecord() {
        String entityUuid = client.pushEntity("verify-evidence-search-" + randomUuid().substring(0, 8) + ".com", "verify_ev_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "verify evidence content", "verify note", "verify_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("verify evidence content", 1, 10);
        assertFalse(results.isEmpty());

        EvidenceRecord fullRecord = client.getEvidenceRecord(evidenceUuid);
        assertEquals(fullRecord.uuid(), results.get(0).uuid());
        assertEquals(fullRecord.textContent(), results.get(0).textContent());
    }

    @Test
    void testSearchConfidentialEvidenceAsAnonymousUser() {
        if (!client.getServerInformation().publicEvidence()) {
            return;
        }

        String entityUuid = client.pushEntity("confidential-test-" + randomUuid().substring(0, 8) + ".com", "conf_user");
        createdEntities.add(entityUuid);

        String confContent = "CONFIDENTIAL_EVIDENCE_" + randomUuid();
        String evidenceUuid = client.submitEvidence(entityUuid, confContent, "conf note", "conf_tag", true);
        createdEvidenceRecords.add(evidenceUuid);

        FederationClient anonymousClient = createAnonymousClient();
        try {
            List<EvidenceRecord> results = anonymousClient.searchEvidence(confContent, 1, 10);
            boolean found = false;
            for (EvidenceRecord result : results) {
                if (result.uuid().equals(evidenceUuid)) {
                    found = true;
                    break;
                }
            }
            assertFalse(found, "Anonymous user should not see confidential evidence");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401 || e.getStatusCode() == 403 || e.getStatusCode() == 404,
                "Unexpected HTTP status: " + e.getStatusCode());
        }
    }

    @Test
    void testSearchConfidentialEvidenceWithManagementOperator() {
        String entityUuid = client.pushEntity("conf-mgmt-test-" + randomUuid().substring(0, 8) + ".com", "conf_mgmt_user");
        createdEntities.add(entityUuid);

        String confContent = "MGMT_CONF_EVIDENCE_" + randomUuid();
        String evidenceUuid = client.submitEvidence(entityUuid, confContent, "mgmt conf note", "mgmt_conf_tag", true);
        createdEvidenceRecords.add(evidenceUuid);

        FederationClient mgmtClient = createLimitedOperator("mgmt_search", true, false, false);

        try {
            List<EvidenceRecord> results = mgmtClient.searchEvidence(confContent, 1, 10);
            boolean found = false;
            for (EvidenceRecord result : results) {
                if (result.uuid().equals(evidenceUuid)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Management operator should find confidential evidence");
        } catch (FederationClientException e) {
            // skip if management operator cannot search
        }
    }

    @Test
    void testSearchEntitiesAsAnonymousPublic() {
        if (!client.getServerInformation().publicEntities()) {
            return;
        }

        String host = "anon-search-" + randomUuid().substring(0, 8) + ".com";
        String entityUuid = client.pushEntity(host, "anon_user");
        createdEntities.add(entityUuid);

        FederationClient anonymousClient = createAnonymousClient();
        try {
            List<EntityRecord> results = anonymousClient.searchEntities(host, 1, 10);
            assertFalse(results.isEmpty());
            for (EntityRecord result : results) {
                assertEquals(host, result.host());
            }
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401 || e.getStatusCode() == 404,
                "Anonymous entity search failed with unexpected status: " + e.getStatusCode());
        }
    }

    @Test
    void testSearchEvidenceAsAnonymousPublic() {
        if (!client.getServerInformation().publicEvidence()) {
            return;
        }

        String entityUuid = client.pushEntity("anon-evidence-" + randomUuid().substring(0, 8) + ".com", "anon_ev_user");
        createdEntities.add(entityUuid);

        String content = "ANON_PUBLIC_EVIDENCE_" + randomUuid();
        String evidenceUuid = client.submitEvidence(entityUuid, content, "anon note", "anon_tag");
        createdEvidenceRecords.add(evidenceUuid);

        FederationClient anonymousClient = createAnonymousClient();
        try {
            List<EvidenceRecord> results = anonymousClient.searchEvidence(content, 1, 10);
            assertFalse(results.isEmpty());
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401 || e.getStatusCode() == 404,
                "Anonymous evidence search failed with unexpected status: " + e.getStatusCode());
        }
    }

    @Test
    void testSearchAuditLogsByMessage() {
        String operatorName = "audit_search_op_" + randomUuid().substring(0, 8);
        OperatorCreated operatorUuidCreated = client.createOperator(operatorName);
        String operatorUuid = operatorUuidCreated.uuid();
        createdOperators.add(operatorUuid);

        client.deleteOperator(operatorUuid);
        removeFromCleanup(createdOperators, operatorUuid);

        List<AuditLog> results = client.searchAuditLogs(operatorName, 1, 10);
        assertNotNull(results);
    }

    @Test
    void testSearchEntitiesUsesDefaultPageAndLimit() {
        String entityUuid = client.pushEntity("default-params.com", "default_user");
        createdEntities.add(entityUuid);

        List<EntityRecord> results = client.searchEntities("default-params.com", 1, 10);
        assertNotNull(results);
        assertTrue(results.size() <= 10);
    }

    @Test
    void testSearchEvidenceTextContentExactWord() {
        String entityUuid = client.pushEntity("text-exact-" + randomUuid().substring(0, 8) + ".com", "text_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "The quick brown fox", "exact note", "exact_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("brown", 1, 10);
        assertFalse(results.isEmpty());
        boolean found = false;
        for (EvidenceRecord result : results) {
            if (result.uuid().equals(evidenceUuid)) {
                found = true;
            }
        }
        assertTrue(found, "Evidence must be found by exact word within text_content");
    }

    @Test
    void testSearchEvidenceTextContentNumericOnly() {
        String entityUuid = client.pushEntity("text-numeric-" + randomUuid().substring(0, 8) + ".com", "num_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "12345 67890 54321", "numeric note", "numeric_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("67890", 1, 10);
        assertFalse(results.isEmpty());
        boolean found = false;
        for (EvidenceRecord result : results) {
            if (result.uuid().equals(evidenceUuid)) {
                found = true;
            }
        }
        assertTrue(found, "Evidence must be found by numeric content search");
    }

    @Test
    void testSearchEvidenceTextContentSpecialCharacters() {
        String entityUuid = client.pushEntity("text-special-" + randomUuid().substring(0, 8) + ".com", "spec_user");
        createdEntities.add(entityUuid);

        String content = "Price: $49.99 (discount 20%) \u2014 #sale! item@store";
        String evidenceUuid = client.submitEvidence(entityUuid, content, "special note", "special_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("$49.99", 1, 10);
        assertFalse(results.isEmpty());
    }

    @Test
    void testSearchEvidenceTextContentVeryLong() {
        String entityUuid = client.pushEntity("text-long-" + randomUuid().substring(0, 8) + ".com", "long_user");
        createdEntities.add(entityUuid);

        StringBuilder sb = new StringBuilder();
        sb.append("TEXT_CONTENT_").append(randomUuid()).append(" ");
        for (int i = 0; i < 200; i++) {
            sb.append("Lorem ipsum dolor sit amet consectetur adipiscing elit ");
        }
        String longContent = sb.toString();
        String evidenceUuid = client.submitEvidence(entityUuid, longContent, "long note", "long_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("TEXT_CONTENT", 1, 10);
        assertFalse(results.isEmpty());
        boolean found = false;
        for (EvidenceRecord result : results) {
            if (result.uuid().equals(evidenceUuid)) {
                found = true;
            }
        }
        assertTrue(found, "Evidence with very long text_content must be found");
    }

    @Test
    void testSearchEvidenceTextContentMinimumLength() {
        String entityUuid = client.pushEntity("text-min-" + randomUuid().substring(0, 8) + ".com", "min_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "ab", "min note", "min_tag");
        createdEvidenceRecords.add(evidenceUuid);

        try {
            client.searchEvidence("a", 1, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testSearchEvidenceTextContentExactlyTwoCharacters() {
        String entityUuid = client.pushEntity("text-two-" + randomUuid().substring(0, 8) + ".com", "two_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "xy", "two note", "two_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("xy", 1, 10);
        assertFalse(results.isEmpty());
        boolean found = false;
        for (EvidenceRecord result : results) {
            if (result.uuid().equals(evidenceUuid)) {
                found = true;
            }
        }
        assertTrue(found, "Evidence must be found by its exact two-character content");
    }

    @Test
    void testSearchEvidenceTextContentUnicode() {
        String entityUuid = client.pushEntity("text-unicode-" + randomUuid().substring(0, 8) + ".com", "uni_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Hello \u4e16\u754c caf\u00e9 \u00f1o\u00f1o \u4f60\u597d \ud83d\ude0a", "unicode note", "unicode_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("\u4e16\u754c", 1, 10);
        assertNotNull(results);
    }

    @Test
    void testSearchEvidenceTextContentAtStartBoundary() {
        String entityUuid = client.pushEntity("text-start-" + randomUuid().substring(0, 8) + ".com", "start_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "START_MARKER some other text follows", "start note", "start_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("START_MARKER", 1, 10);
        assertFalse(results.isEmpty());
        boolean found = false;
        for (EvidenceRecord result : results) {
            if (result.uuid().equals(evidenceUuid)) {
                found = true;
            }
        }
        assertTrue(found, "Evidence must be found when query matches text_content at the start");
    }

    @Test
    void testSearchEvidenceTextContentAtEndBoundary() {
        String entityUuid = client.pushEntity("text-end-" + randomUuid().substring(0, 8) + ".com", "end_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "some text before END_MARKER", "end note", "end_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence("END_MARKER", 1, 10);
        assertFalse(results.isEmpty());
        boolean found = false;
        for (EvidenceRecord result : results) {
            if (result.uuid().equals(evidenceUuid)) {
                found = true;
            }
        }
        assertTrue(found, "Evidence must be found when query matches text_content at the end");
    }

    @Test
    void testSearchEvidenceTextContentMultipleSharedSubstring() {
        String entityUuid = client.pushEntity("text-multi-shared-" + randomUuid().substring(0, 8) + ".com", "multi_shared_user");
        createdEntities.add(entityUuid);

        List<String> created = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String uuid = client.submitEvidence(entityUuid, "SHARED_TEXT_SUBSTRING evidence " + i, "multi note " + i, "multi_tag_" + i);
            createdEvidenceRecords.add(uuid);
            created.add(uuid);
        }

        List<EvidenceRecord> results = client.searchEvidence("SHARED_TEXT_SUBSTRING", 1, 100);
        assertTrue(results.size() >= created.size());

        List<String> resultUuids = results.stream().map(EvidenceRecord::uuid).collect(Collectors.toList());
        for (String uuid : created) {
            assertTrue(resultUuids.contains(uuid));
        }
    }

    @Test
    void testSearchEvidenceTextContentDoesNotMatchNote() {
        String entityUuid = client.pushEntity("text-note-" + randomUuid().substring(0, 8) + ".com", "note_user");
        createdEntities.add(entityUuid);

        String noteText = "THIS_IS_THE_NOTE_NOT_CONTENT_" + randomUuid();
        String evidenceUuid = client.submitEvidence(entityUuid, "actual text content", noteText, "note_tag");
        createdEvidenceRecords.add(evidenceUuid);

        List<EvidenceRecord> results = client.searchEvidence(noteText, 1, 10);
        boolean found = false;
        for (EvidenceRecord result : results) {
            if (result.uuid().equals(evidenceUuid)) {
                found = true;
            }
        }
        assertFalse(found, "Note text should NOT be searchable via evidence text_content search");
    }
}
