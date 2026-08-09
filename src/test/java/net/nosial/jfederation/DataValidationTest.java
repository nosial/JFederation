package net.nosial.jfederation;

import net.nosial.jfederation.enums.IncidentType;
import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.*;
import org.junit.jupiter.api.Test;
import net.nosial.jfederation.records.OperatorCreated;

import static org.junit.jupiter.api.Assertions.*;

class DataValidationTest extends FederationClientTestBase {

    @Test
    void testEntityHostValidation() {
        String[] invalidHosts = {
            "",
            "   ",
            "invalid..domain.com",
            ".invalid-domain.com",
            "invalid-domain.com.",
            "host with spaces.com",
            "invalid-chars!.com",
            "http://notjustdomain.com",
        };

        for (String invalidHost : invalidHosts) {
            try {
                String entityUuid = client.pushEntity(invalidHost, "test_user");
                if (entityUuid != null) {
                    createdEntities.add(entityUuid);
                }
            } catch (FederationClientException e) {
                assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 422,
                    "Expected 400 or 422 for invalid host '" + invalidHost + "', got " + e.getStatusCode());
            } catch (IllegalArgumentException e) {
                assertNotNull(e.getMessage());
            }
        }
    }

    @Test
    void testEntityIdValidation() {
        String host = "validation-test.com";
        String[] invalidIds = {
            strRepeat('a', 1000),
            "id\nwith\nnewlines",
            "id\twith\ttabs",
            "id/with/slashes",
            "id\\with\\backslashes",
            "id\"with\"quotes",
            "id'with'apostrophes",
            "<script>alert('xss')</script>",
            "'; DROP TABLE entities; --",
        };

        for (String invalidId : invalidIds) {
            try {
                String entityUuid = client.pushEntity(host, invalidId);
                if (entityUuid != null) {
                    createdEntities.add(entityUuid);
                }
            } catch (FederationClientException e) {
                assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 422,
                    "Expected 400 or 422 for invalid ID, got " + e.getStatusCode());
            } catch (IllegalArgumentException e) {
                assertNotNull(e.getMessage());
            }
        }
    }

    @Test
    void testValidEntityFormats() {
        Object[][] validEntities = {
            {"example.com", "user123"},
            {"subdomain.example.org", "valid_user"},
            {"test-domain.net", "user-name"},
            {"192.168.1.1", null},
            {"127.0.0.1", "localhost_user"},
            {"10.0.0.1", null},
            {"a.com", "a"},
            {"example123.com", "123user"},
        };

        for (Object[] entityData : validEntities) {
            String host = (String) entityData[0];
            String id = (String) entityData[1];
            String entityUuid = client.pushEntity(host, id);
            createdEntities.add(entityUuid);
            assertNotNull(entityUuid);

            EntityRecord entity = client.getEntityRecord(entityUuid);
            assertEquals(host, entity.host());
            assertEquals(id, entity.id());
        }
    }

    @Test
    void testOperatorNameValidation() {
        String[] invalidNames = {
            "",
            "   ",
            strRepeat('a', 1000),
            "name\nwith\nnewlines",
            "name\twith\ttabs",
        };

        for (String invalidName : invalidNames) {
            try {
                OperatorCreated operatorUuidCreated = client.createOperator(invalidName);
        String operatorUuid = operatorUuidCreated.uuid();
                if (operatorUuid != null) {
                    createdOperators.add(operatorUuid);
                }
            } catch (FederationClientException e) {
                assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 422,
                    "Expected 400 or 422 for invalid operator name, got " + e.getStatusCode());
            } catch (IllegalArgumentException e) {
                assertNotNull(e.getMessage());
            }
        }
    }

    @Test
    void testValidOperatorNames() {
        String[] validNames = {
            "Simple Operator",
            "Operator with Numbers 123",
            "Operator-with-Hyphens",
            "Operator_with_Underscores",
            "Operator.with.Dots",
            "Single",
            "A",
            "Operator with (Parentheses)",
            "Operator with [Brackets]",
            "Special Chars: @#$%^&*()",
        };

        for (String name : validNames) {
            String operatorUuid = client.createOperator(name + "_" + randomUuid().substring(0, 4)).uuid();
            createdOperators.add(operatorUuid);
            assertNotNull(operatorUuid);

            OperatorRecord operator = client.getOperator(operatorUuid);
            // name might be truncated or modified by server
            assertNotNull(operator.name());
        }
    }

    @Test
    void testEvidenceTagValidation() {
        String entityUuid = client.pushEntity("tag-validation.com", "tag_user");
        createdEntities.add(entityUuid);

        String[] invalidTags = {
            "",
            "   ",
            strRepeat('a', 1000),
            "tag\nwith\nnewlines",
            "tag with spaces",
            "tag/with/slashes",
            "<script>alert('xss')</script>",
        };

        for (String tag : invalidTags) {
            try {
                String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence", "Test note", tag);
                if (evidenceUuid != null) {
                    createdEvidenceRecords.add(evidenceUuid);
                }
            } catch (FederationClientException e) {
                assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 422,
                    "Expected 400 or 422 for invalid tag, got " + e.getStatusCode());
            }
        }
    }

    @Test
    void testValidEvidenceData() {
        String entityUuid = client.pushEntity("valid-evidence.com", "valid_user");
        createdEntities.add(entityUuid);

        Object[][] validEvidenceData = {
            {"Simple evidence text", "Simple note", "simple"},
            {"Evidence with numbers 123", "Note with numbers 456", "numbers123"},
            {"Evidence with special chars: @#$%", "Note with chars", "special_chars"},
            {"Multi-line\nevidence\ncontent", "Multi-line\nnote", "multiline"},
            {"Unicode content: \u4e2d\u6587 \u0627\u0644\u0639\u0631\u0628\u064a\u0629", "Unicode note: \u65e5\u672c\u8a9e", "unicode"},
        };

        for (Object[] data : validEvidenceData) {
            String content = (String) data[0];
            String note = (String) data[1];
            String tag = (String) data[2];
            String evidenceUuid = client.submitEvidence(entityUuid, content, note, tag);
            createdEvidenceRecords.add(evidenceUuid);
            assertNotNull(evidenceUuid);

            EvidenceRecord evidence = client.getEvidenceRecord(evidenceUuid);
            assertEquals(content, evidence.textContent());
            assertEquals(note, evidence.note());
            assertEquals(tag, evidence.tag());
        }
    }

    @Test
    void testBlacklistExpirationValidation() {
        String entityUuid = client.pushEntity("blacklist-validation.com", "blacklist_user");
        createdEntities.add(entityUuid);

        String evidenceUuid = client.submitEvidence(entityUuid, "Test evidence", "Test note", "test");
        createdEvidenceRecords.add(evidenceUuid);

        int[] invalidExpirations = {
            -1,
            (int) (System.currentTimeMillis() / 1000 - 3600),
        };

        for (int expiration : invalidExpirations) {
            try {
                String blacklistUuid = client.blacklistEntity(entityUuid, evidenceUuid, IncidentType.SPAM, expiration);
                if (blacklistUuid != null) {
                    createdBlacklistRecords.add(blacklistUuid);
                }
            } catch (IllegalArgumentException e) {
                assertNotNull(e.getMessage());
            } catch (FederationClientException e) {
                assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 422,
                    "Expected 400 or 422 for invalid expiration " + expiration);
            }
        }
    }

    @Test
    void testBlacklistWithNonExistentEvidence() {
        String entityUuid = client.pushEntity("blacklist-invalid-evidence.com", "invalid_evidence_user");
        createdEntities.add(entityUuid);

        String fakeEvidenceUuid = "01234567-89ab-cdef-0123-456789abcdef";

        try {
            client.blacklistEntity(entityUuid, fakeEvidenceUuid, IncidentType.SPAM, (int) (System.currentTimeMillis() / 1000 + 3600));
            fail("Expected FederationClientException for non-existent evidence");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 404,
                "Expected 400 or 404 for non-existent evidence, got " + e.getStatusCode());
        }
    }

    @Test
    void testBlacklistWithNonExistentEntity() {
        String fakeEntityUuid = "01234567-89ab-cdef-0123-456789abcdef";
        String fakeEvidenceUuid = "01234567-89ab-cdef-0123-456789abcdef";

        try {
            client.blacklistEntity(fakeEntityUuid, fakeEvidenceUuid, IncidentType.SPAM, (int) (System.currentTimeMillis() / 1000 + 3600));
            fail("Expected FederationClientException for non-existent entity");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 404,
                "Expected 400 or 404 for non-existent entity, got " + e.getStatusCode());
        }
    }

    @Test
    void testUuidFormatValidation() {
        String[] invalidUuids = {
            "not-a-uuid",
            "12345678-90ab-cdef-ghij-klmnopqrstuv",
            "12345678-90ab-cdef-0123-456789abcde",
            "12345678-90ab-cdef-0123-456789abcdefg",
            "12345678_90ab_cdef_0123_456789abcdef",
            "g1234567-89ab-cdef-0123-456789abcdef",
        };

        for (String invalidUuid : invalidUuids) {
            try {
                client.getEntityRecord(invalidUuid);
                fail("Expected exception for invalid UUID '" + invalidUuid + "'");
            } catch (FederationClientException e) {
                assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 404 || e.getStatusCode() == 422,
                    "Expected validation error for UUID '" + invalidUuid + "'");
            }
        }
    }

    @Test
    void testPaginationParameterValidation() {
        try {
            client.listEntities(-1, 10);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testPaginationLimitValidation() {
        try {
            client.listEntities(1, 0);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    void testDataLengthBoundaries() {
        String entityUuid = client.pushEntity("boundary-test.com", "boundary_user");
        createdEntities.add(entityUuid);

        int[] lengths = {1, 100, 1000, 10000};

        for (int length : lengths) {
            String content = strRepeat('a', length);
            try {
                String evidenceUuid = client.submitEvidence(entityUuid, content, "Length test", "boundary");
                createdEvidenceRecords.add(evidenceUuid);

                EvidenceRecord evidence = client.getEvidenceRecord(evidenceUuid);
                assertEquals(length, evidence.textContent().length());
            } catch (FederationClientException e) {
                fail("Unexpected error for reasonable content length " + length + ": " + e.getMessage());
            }
        }
    }

    @Test
    void testUnicodeHandling() {
        String entityUuid = client.pushEntity("unicode-test.com", "unicode_user");
        createdEntities.add(entityUuid);

        String[] unicodeTests = {
            "English text",
            "\u4e2d\u6587\u6d4b\u8bd5",
            "\u0627\u0644\u0639\u0631\u0628\u064a\u0629",
            "\u0420\u0443\u0441\u0441\u043a\u0438\u0439",
            "\u65e5\u672c\u8a9e",
            "\u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac",
            "\ud83d\ude80 \ud83c\udf1f \ud83d\udcbb",
            "Mixed: English \u4e2d\u6587 \u0627\u0644\u0639\u0631\u0628\u064a\u0629 \ud83d\ude80",
            "Special chars: \u00f1\u00e1\u00e9\u00ed\u00f3\u00fa \u00e7\u00fc\u00e2\u00ea",
        };

        for (String unicodeContent : unicodeTests) {
            String evidenceUuid = client.submitEvidence(entityUuid, unicodeContent, "Unicode test", "unicode");
            createdEvidenceRecords.add(evidenceUuid);

            EvidenceRecord evidence = client.getEvidenceRecord(evidenceUuid);
            assertEquals(unicodeContent, evidence.textContent(), "Unicode content was not preserved correctly");
        }
    }

    @Test
    void testEvidenceContentValidation() {
        String entityUuid = client.pushEntity("evidence-validation.com", "evidence_user");
        createdEntities.add(entityUuid);

        String veryLongContent = strRepeat('a', 100000);

        try {
            String evidenceUuid = client.submitEvidence(entityUuid, veryLongContent, "Test note", "test_tag");
            if (evidenceUuid != null) {
                createdEvidenceRecords.add(evidenceUuid);
            }
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 422,
                "Expected 400 or 422 for invalid evidence content, got " + e.getStatusCode());
        }
    }

    private static String strRepeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
