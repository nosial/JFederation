package net.nosial.jfederation;

import net.nosial.jfederation.classes.Json;
import net.nosial.jfederation.enums.AuditLogType;
import net.nosial.jfederation.enums.ContentType;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.NamedEntityType;
import net.nosial.jfederation.enums.RecordType;
import net.nosial.jfederation.records.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Offline tests of the records and enums of OFD 2.0, they don't require a server.
 */
class Ofd2RecordsTest {

    @Test
    void testOperatorRecordDeserializesPermissionsAndConfiguration() {
        OperatorRecord operator = Json.readValue("""
            {
              "uuid": "01a15854-9ea9-73ca-bcf3-b8ad74f9bb40",
              "name": "bot_operator",
              "disabled": false,
              "permissions": {"client": true, "management": true, "operator": false},
              "configuration": {"auto_assign_reports": false, "auto_assign_automated_reports": true, "is_bot": true},
              "created": 1760000000,
              "updated": 1760000001
            }
            """, OperatorRecord.class);

        assertEquals(new OperatorPermissions(true, true, false), operator.permissions());
        assertTrue(operator.hasClientPermissions());
        assertTrue(operator.hasManagementPermissions());
        assertFalse(operator.hasOperatorPermissions());

        assertTrue(operator.isConfigurationVisible());
        assertFalse(operator.isAutoAssignReports());
        assertTrue(operator.isAutoAssignAutomatedReports());
        assertTrue(operator.isAutoAssigned());
        assertTrue(operator.isBot());
    }

    @Test
    void testOperatorRecordWithUndisclosedConfiguration() {
        OperatorRecord operator = Json.readValue("""
            {"uuid": "u", "name": "n", "disabled": false,
             "permissions": {"client": true, "management": false, "operator": false},
             "configuration": null, "created": 0, "updated": 0}
            """, OperatorRecord.class);

        assertNull(operator.configuration());
        assertFalse(operator.isConfigurationVisible());
        assertFalse(operator.isAutoAssigned());
        assertFalse(operator.isBot());

        // A record without permissions holds none
        OperatorRecord withoutPermissions = Json.readValue("{\"uuid\": \"u\", \"name\": \"n\"}", OperatorRecord.class);
        assertEquals(new OperatorPermissions(false, false, false), withoutPermissions.permissions());
    }

    @Test
    void testServerInformationDeserializesOfd2Members() {
        ServerInformation info = Json.readValue("""
            {
              "name": "OFD 2.0 Server",
              "api_version": "2.0",
              "public_telephone_entities": false,
              "collects_public_scan_content": true,
              "allow_url_uploads": true,
              "scan_content_types": ["TEXT", "EMAIL", "SMS", "WEB_PAGE", "HOSTNAME", "URL"],
              "concerns": ["SPAM", "SCAM", "ADVERTISING", "TRACKING", "OTHER"],
              "default_concerns": ["SPAM", "SCAM", "OTHER"]
            }
            """, ServerInformation.class);

        assertEquals(2, info.getApiMajorVersion());
        assertFalse(info.publicTelephoneEntities());
        assertEquals(Boolean.TRUE, info.collectsPublicScanContent());
        assertTrue(info.isUrlUploadsAllowed());
        assertEquals(List.of(ContentType.values()), info.scanContentTypes());
        assertTrue(info.isScanContentTypeSupported(ContentType.WEB_PAGE));
        assertEquals(List.of(IncidentType.SPAM, IncidentType.SCAM, IncidentType.ADVERTISING, IncidentType.TRACKING, IncidentType.OTHER), info.concerns());
        assertEquals(List.of(IncidentType.SPAM, IncidentType.SCAM, IncidentType.OTHER), info.defaultConcerns());
    }

    @Test
    void testServerInformationDefaultsForMembersNotPublished() {
        ServerInformation info = Json.readValue("{\"name\": \"Legacy Server\", \"api_version\": \"1.0\"}", ServerInformation.class);

        assertEquals(1, info.getApiMajorVersion());
        assertNull(info.allowUrlUploads());
        assertFalse(info.isUrlUploadsAllowed(), "A server that does not publish allow_url_uploads does not accept URL uploads");
        assertNull(info.collectsPublicScanContent());
        assertEquals(List.of(ContentType.TEXT), info.scanContentTypes(), "Content scanning always interprets TEXT");
        assertEquals(List.of(), info.concerns());
        assertEquals(List.of(), info.defaultConcerns());
        assertEquals(List.of(), info.searchTypes());

        assertEquals(-1, Json.readValue("{\"api_version\": \"unknown\"}", ServerInformation.class).getApiMajorVersion());
    }

    @Test
    void testServerInformationToleratesUnrecognizedValues() {
        ServerInformation info = Json.readValue("""
            {
              "scan_content_types": ["EMAIL", "VOICEMAIL"],
              "concerns": ["SPAM", "CRYPTOJACKING"],
              "search_types": ["ENTITY", "FUTURE_RECORD"]
            }
            """, ServerInformation.class);

        assertEquals(List.of(ContentType.TEXT, ContentType.EMAIL), info.scanContentTypes());
        assertEquals(List.of(RecordType.ENTITY), info.searchTypes());
        // An unrecognized incident type is read as OTHER
        assertEquals(List.of(IncidentType.SPAM, IncidentType.OTHER), info.concerns());
    }

    @Test
    void testUnrecognizedEnumValues() {
        assertSame(IncidentType.OTHER, Json.readValue("\"CRYPTOJACKING\"", IncidentType.class));
        assertSame(AuditLogType.OTHER, Json.readValue("\"FUTURE_EVENT\"", AuditLogType.class));
        assertSame(IncidentType.ADVERTISING, Json.readValue("\"ADVERTISING\"", IncidentType.class));
        assertSame(IncidentType.TRACKING, Json.readValue("\"TRACKING\"", IncidentType.class));
        assertSame(NamedEntityType.TELEPHONE, Json.readValue("\"telephone\"", NamedEntityType.class));

        BlacklistRecord blacklist = Json.readValue("{\"uuid\": \"b\", \"type\": \"FUTURE_TYPE\"}", BlacklistRecord.class);
        assertSame(IncidentType.OTHER, blacklist.type());

        ResolvedEntityPosition position = Json.readValue("{\"offset\": 1, \"length\": 2, \"type\": \"future\"}", ResolvedEntityPosition.class);
        assertNull(position.type());
    }

    @Test
    void testEvidenceRecordDeserializesOfd2Members() {
        EvidenceRecord evidence = Json.readValue("""
            {
              "uuid": "e", "entity": "n", "operator": "o",
              "confidential": true, "automated": true,
              "text_content": "https://example.com/login",
              "content_type": "WEB_PAGE",
              "context": {"url": "https://example.com/login", "title": "Login"}
            }
            """, EvidenceRecord.class);

        assertTrue(evidence.automated());
        assertTrue(evidence.confidential());
        assertSame(ContentType.WEB_PAGE, evidence.contentType());
        assertEquals(Map.of("url", "https://example.com/login", "title", "Login"), evidence.getContext());

        EvidenceRecord plain = Json.readValue("{\"uuid\": \"e\", \"context\": []}", EvidenceRecord.class);
        assertSame(ContentType.TEXT, plain.contentType(), "Evidence without a content type is text");
        assertFalse(plain.automated());
        assertEquals(Map.of(), plain.getContext());
    }

    @Test
    void testAuditLogDeserializesReport() {
        AuditLog entry = Json.readValue("""
            {"uuid": "a", "operator": "o", "entity": "n", "report": "r", "type": "REPORT_SUBMITTED",
             "message": "Report submitted", "timestamp": 1760000000}
            """, AuditLog.class);

        assertEquals("r", entry.reportUuid());
        assertSame(AuditLogType.REPORT_SUBMITTED, entry.type());
        assertNull(Json.readValue("{\"uuid\": \"a\"}", AuditLog.class).reportUuid());
    }

    @Test
    void testScannedContentDeserializesDetections() {
        ScannedContent scanned = Json.readValue("""
            {
              "resolved_entities": [], "author_entity": null, "classification": null,
              "risk_score": 75.0, "suggested_action": "BLOCK_CONTENT", "suggested_lift_timestamp": null,
              "scan_results": {},
              "detections": [
                {"concern": "SPAM", "content_index": 1, "entity": "n", "source": "blacklist", "confidence": null, "verified": true},
                {"concern": "TRACKING", "content_index": 0, "entity": null, "source": "filter_list", "confidence": 0.5, "verified": false}
              ]
            }
            """, ScannedContent.class);

        assertEquals(2, scanned.detections().size());
        Detection first = scanned.detections().getFirst();
        assertSame(IncidentType.SPAM, first.concern());
        assertEquals(1, first.contentIndex());
        assertEquals("n", first.entity());
        assertEquals("blacklist", first.source());
        assertNull(first.confidence());
        assertTrue(first.verified());

        Detection second = scanned.detections().get(1);
        assertNull(second.entity());
        assertEquals(0.5, second.confidence());
        assertFalse(second.verified());

        assertEquals(List.of(), Json.readValue("{\"risk_score\": 50}", ScannedContent.class).detections());
    }

    @Test
    void testContentInputSerialization() {
        JsonNode text = Json.readTree(Json.writeValueAsString(new ContentInput("Hello")));
        assertEquals("Hello", text.get("text_content").asString());
        assertFalse(text.has("content_type"), "The default content type is not sent");
        assertFalse(text.has("context"));

        // TEXT given explicitly is not sent either
        assertFalse(Json.readTree(Json.writeValueAsString(new ContentInput("Hello", ContentType.TEXT))).has("content_type"));
        assertSame(ContentType.TEXT, new ContentInput("Hello", ContentType.TEXT).effectiveContentType());

        JsonNode email = Json.readTree(Json.writeValueAsString(new ContentInput("Body", ContentType.EMAIL,
            Map.of("subject", "Invoice", "sender", "billing@example.com"))));
        assertEquals("EMAIL", email.get("content_type").asString());
        assertEquals("Invoice", email.get("context").get("subject").asString());
        assertEquals("billing@example.com", email.get("context").get("sender").asString());
    }

    @Test
    void testFileAttachmentRecordWithoutEvidence() {
        assertNull(Json.readValue("{\"uuid\": \"f\", \"evidence\": \"\"}", FileAttachmentRecord.class).evidenceUuid());
        assertNull(Json.readValue("{\"uuid\": \"f\", \"evidence\": null}", FileAttachmentRecord.class).evidenceUuid());
        assertEquals("e", Json.readValue("{\"uuid\": \"f\", \"evidence\": \"e\"}", FileAttachmentRecord.class).evidenceUuid());
    }

    @Test
    void testEntityRecordTelephoneAndHash() {
        EntityRecord telephone = Json.readValue("{\"uuid\": \"t\", \"hash\": \"abc\", \"host\": \"e164.arpa\", \"id\": \"16502530000\"}", EntityRecord.class);
        assertTrue(telephone.isTelephone());
        assertEquals("abc", telephone.hash());

        assertFalse(Json.readValue("{\"uuid\": \"d\", \"host\": \"e164.arpa\"}", EntityRecord.class).isTelephone());
        assertFalse(Json.readValue("{\"uuid\": \"d\", \"host\": \"example.com\", \"id\": \"user\"}", EntityRecord.class).isTelephone());
    }

    @Test
    void testOfd2ArgumentsAreValidatedClientSide() {
        FederationClient offline = new FederationClient("http://127.0.0.1:1");

        assertThrows(IllegalArgumentException.class, () -> offline.updateOperatorPermissions("uuid", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> offline.updateOperatorPermissions("", true, null, null));
        assertThrows(IllegalArgumentException.class, () -> offline.updateOperatorConfiguration("uuid", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> offline.updateOperatorConfiguration("", true, null, null));
        assertThrows(IllegalArgumentException.class, () -> offline.setBot("", true));

        assertThrows(IllegalArgumentException.class, () -> offline.scanContent(new ContentInput("text"), null, null, null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> offline.queryEntity("example.com", List.of()));

        assertThrows(IllegalArgumentException.class, () -> offline.blacklistEntity("example.com", "", IncidentType.SPAM, null));
        assertThrows(NullPointerException.class, () -> offline.blacklistEntity("example.com", (IncidentType) null));

        // Search queries need at least two non-whitespace characters
        assertThrows(IllegalArgumentException.class, () -> offline.searchEntities(" a ", 1, 10));
        assertThrows(IllegalArgumentException.class, () -> offline.searchEntities("  ", 1, 10));
        assertThrows(IllegalArgumentException.class, () -> offline.searchBlacklist(null, 1, 10));
        offline.close();
    }
}
