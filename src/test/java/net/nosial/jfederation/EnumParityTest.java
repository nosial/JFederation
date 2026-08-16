package net.nosial.jfederation;

import net.nosial.jfederation.classes.Json;
import net.nosial.jfederation.enums.AuditLogType;
import net.nosial.jfederation.enums.ClassificationFlag;
import net.nosial.jfederation.enums.EntityRelationshipType;
import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.enums.NamedEntityType;
import net.nosial.jfederation.enums.RecordType;
import net.nosial.jfederation.enums.SuggestedAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumParityTest {

    @Test
    void testAuditLogTypeWireValuesMatchSpecificationEnum() {
        String[] expectedWireValues = {
            "OPERATOR_CREATED", "OPERATOR_DELETED", "OPERATOR_DISABLED", "OPERATOR_ENABLED", "OPERATOR_UPDATED",
            "ATTACHMENT_UPLOADED", "ATTACHMENT_DELETED",
            "EVIDENCE_SUBMITTED", "EVIDENCE_UPDATED", "EVIDENCE_DELETED",
            "REPORT_GENERATED", "REPORT_SUBMITTED", "REPORT_OPERATOR_ASSIGNED", "REPORT_CLOSED", "REPORT_DELETED",
            "ENTITY_DELETED", "ENTITY_BLACKLISTED", "ENTITY_PUSHED", "ENTITY_UPDATED",
            "BLACKLIST_DELETED", "BLACKLIST_LIFTED", "BLACKLIST_EXTENDED",
            "OTHER"
        };
        assertEquals(23, expectedWireValues.length);
        assertEquals(23, AuditLogType.values().length,
            "Java AuditLogType must cover every case of the specification");
        for (String wireValue : expectedWireValues) {
            AuditLogType parsed = Json.readValue("\"" + wireValue + "\"", AuditLogType.class);
            assertNotNull(parsed, "Failed to deserialize audit type '" + wireValue + "'");
            assertEquals(wireValue, parsed.getValue());
            assertEquals(parsed, Json.readValue(Json.writeValueAsString(parsed), AuditLogType.class),
                "Serialization round-trip failed for '" + wireValue + "'");
        }
    }

    @Test
    void testSuggestedActionDeserializesBySpecificationCaseName() {
        assertSame(SuggestedAction.BLOCK_CONTENT, Json.readValue("\"BLOCK_CONTENT\"", SuggestedAction.class));
        assertSame(SuggestedAction.TEMPORARILY_BLOCK_ENTITY, Json.readValue("\"TEMPORARILY_BLOCK_ENTITY\"", SuggestedAction.class));
        assertSame(SuggestedAction.PERMANENTLY_BLOCK_ENTITY, Json.readValue("\"PERMANENTLY_BLOCK_ENTITY\"", SuggestedAction.class));
        assertSame(SuggestedAction.CAUTION, Json.readValue("\"CAUTION\"", SuggestedAction.class));
    }

    @Test
    void testRemainingEnumsRoundTrip() {
        for (ClassificationFlag flag : ClassificationFlag.values()) {
            assertSame(flag, Json.readValue("\"" + flag.name() + "\"", ClassificationFlag.class));
        }
        for (IncidentType type : IncidentType.values()) {
            assertSame(type, Json.readValue(Json.writeValueAsString(type), IncidentType.class));
        }
        for (EntityRelationshipType type : EntityRelationshipType.values()) {
            assertSame(type, Json.readValue("\"" + type.getValue() + "\"", EntityRelationshipType.class));
        }
        for (NamedEntityType type : NamedEntityType.values()) {
            assertSame(type, Json.readValue("\"" + type.getValue() + "\"", NamedEntityType.class));
        }
        for (RecordType type : RecordType.values()) {
            assertSame(type, Json.readValue("\"" + type.getValue() + "\"", RecordType.class));
        }
    }
}
