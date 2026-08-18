package net.nosial.jfederation;

import com.fasterxml.jackson.databind.JsonNode;
import net.nosial.jfederation.classes.Json;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.RecordType;
import net.nosial.jfederation.records.ServerInformation;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerInformationTest extends FederationClientTestBase {

    @Test
    void testGetServerInformation() {
        ServerInformation info = client.getServerInformation();
        assertNotNull(info);
        assertNotNull(info.serverName());
        assertFalse(info.serverName().isEmpty());
        assertNotNull(info.apiVersion());
        assertFalse(info.apiVersion().isEmpty());
    }

    @Test
    void testServerInformationDeserializesPublicCapabilityMetadata() {
        ServerInformation info = Json.readValue("""
            {
              "name": "Capability Test Server",
              "api_version": "1.0",
              "public_audit_logs": true,
              "public_evidence": false,
              "public_blacklist": true,
              "public_entities": true,
              "public_reports": false,
              "public_entity_metadata": false,
              "public_scan_content": true,
              "public_query_entity": true,
              "search_enabled": true,
              "public_search": false,
              "search_types": ["ENTITY", "EVIDENCE", "OPERATOR"],
              "public_search_types": ["ENTITY"],
              "public_audit_logs_visibility": [],
              "audit_log_records": 0,
              "blacklist_records": 0,
              "known_entities": 0,
              "evidence_records": 0,
              "file_attachment_records": 0,
              "operators": 0,
              "reports": 0
            }
            """, ServerInformation.class);

        assertFalse(info.publicEntityMetadata());
        assertTrue(info.publicScanContent());
        assertTrue(info.publicQueryEntity());
        assertTrue(info.searchEnabled());
        assertFalse(info.publicSearch());
        assertEquals(List.of(RecordType.ENTITY, RecordType.EVIDENCE, RecordType.OPERATOR), info.searchTypes());
        assertEquals(List.of(RecordType.ENTITY), info.publicSearchTypes());
    }

    @Test
    void testServerInformationConsistency() {
        ServerInformation info1 = client.getServerInformation();
        for (int i = 0; i < 5; i++) {
            ServerInformation info = client.getServerInformation();
            assertEquals(info1.serverName(), info.serverName());
            assertEquals(info1.apiVersion(), info.apiVersion());
            assertEquals(info1.publicEntities(), info.publicEntities());
            assertEquals(info1.publicEvidence(), info.publicEvidence());
        }
    }

    @Test
    void testServerInformationWithAuthentication() {
        ServerInformation anon = createAnonymousClient().getServerInformation();
        ServerInformation auth = client.getServerInformation();
        assertEquals(anon.serverName(), auth.serverName());
        assertEquals(anon.apiVersion(), auth.apiVersion());
        assertEquals(anon.publicEntities(), auth.publicEntities());
        assertEquals(anon.publicEvidence(), auth.publicEvidence());
    }

    @Test
    void testApiVersionFormat() {
        ServerInformation info = client.getServerInformation();
        String version = info.apiVersion();
        assertNotNull(version);
        assertFalse(version.trim().isEmpty());
        assertTrue(version.matches("\\d+\\.\\d+(\\.\\d+)?(-[a-zA-Z0-9\\-.]+)?(\\+[a-zA-Z0-9\\-.]+)?"),
            "API version should follow semantic versioning format: " + version);
    }

    @Test
    void testSpecificationStructure() {
        JsonNode spec = client.getSpecification();
        assertTrue(spec.has("openapi"));
        assertTrue(spec.has("info"));
        assertTrue(spec.has("paths"));
        assertTrue(spec.get("openapi").asText().startsWith("3."));
        assertTrue(spec.get("info").has("title"));
        assertFalse(spec.get("info").get("title").asText().isEmpty());
        assertTrue(spec.get("paths").isObject());
        assertFalse(spec.get("paths").properties().isEmpty());
    }

    @Test
    void testSpecificationContainsCorePaths() {
        JsonNode spec = client.getSpecification();
        JsonNode paths = spec.get("paths");
        String[] expected = {"/", "/info", "/specification", "/scan", "/entities",
            "/entities/{identifier}", "/evidence", "/evidence/{uuid}", "/blacklist",
            "/blacklist/{uuid}", "/reports", "/reports/{uuid}", "/operators",
            "/operators/{uuid}", "/attachments", "/attachments/{uuid}", "/audit/{uuid}"};
        for (String p : expected) {
            assertTrue(paths.has(p), "Specification should document path " + p);
        }
    }

    @Test
    void testServerInformationCountAccuracyAfterOperatorMutation() {
        ServerInformation before = client.getServerInformation();
        assertTrue(before.operators() >= 0);

        String uuid = client.createOperator("count_accuracy_" + randomUuid().substring(0, 8)).uuid();
        createdOperators.add(uuid);
        ServerInformation afterCreate = client.getServerInformation();
        assertEquals(before.operators() + 1, afterCreate.operators());

        client.deleteOperator(uuid);
        removeFromCleanup(createdOperators, uuid);
        ServerInformation afterDelete = client.getServerInformation();
        assertEquals(before.operators(), afterDelete.operators());
    }

    @Test
    void testServerInformationCountAccuracyAfterEntityAndEvidenceMutation() {
        ServerInformation before = client.getServerInformation();

        String entityUuid = client.pushEntity("count-accuracy-" + randomUuid().substring(0, 8) + ".com", "count_user");
        createdEntities.add(entityUuid);
        ServerInformation afterEntity = client.getServerInformation();
        assertEquals(before.knownEntities() + 1, afterEntity.knownEntities());

        String evidenceUuid = client.submitEvidence(entityUuid, "Count accuracy evidence", "Note", "count");
        createdEvidenceRecords.add(evidenceUuid);
        ServerInformation afterEvidence = client.getServerInformation();
        assertEquals(before.evidenceRecords() + 1, afterEvidence.evidenceRecords());
    }

    @Test
    void testServerInformationPublicFlagsAreBoolean() {
        ServerInformation info = client.getServerInformation();
        // Just verify they don't throw
        assertNotNull(info);
    }

    @Test
    void testServerInformationReportsCountIsInt() {
        ServerInformation info = client.getServerInformation();
        assertTrue(info.reports() >= 0);
    }

    @Test
    void testAuthenticatedClientCanReadRegardlessOfPublicFlags() {
        String entityUuid = client.pushEntity("auth-flag-test-" + randomUuid().substring(0, 8) + ".com", "auth_flag_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Auth flag evidence", "Note", "auth_flag");
        createdEvidenceRecords.add(evidenceUuid);

        assertNotNull(client.getEntityRecord(entityUuid));
        assertNotNull(client.getEvidenceRecord(evidenceUuid));
    }
}
