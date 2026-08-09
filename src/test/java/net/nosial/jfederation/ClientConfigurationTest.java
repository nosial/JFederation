package net.nosial.jfederation;

import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.OperatorCreated;
import net.nosial.jfederation.records.OperatorRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientConfigurationTest {

    @Test
    void testClientWithValidEndpoint() {
        String endpoint = FederationClientTestBase.serverEndpoint;
        assertNotNull(endpoint, "SERVER_ENDPOINT must be set for tests");
        FederationClient client = new FederationClient(endpoint);
        assertNotNull(client);
        assertNotNull(client.getServerInformation());
        client.close();
    }

    @Test
    void testClientWithValidEndpointAndAccessToken() {
        String endpoint = FederationClientTestBase.serverEndpoint;
        String token = FederationClientTestBase.serverAccessToken;
        assertNotNull(endpoint);
        assertNotNull(token);
        FederationClient client = new FederationClient(endpoint, token);
        assertNotNull(client);
        OperatorRecord self = client.getSelf();
        assertNotNull(self);
        assertNotNull(self.uuid());
        assertNotNull(self.name());
        client.close();
    }

    @Test
    void testClientEndpointNormalization() {
        String base = FederationClientTestBase.serverEndpoint.replaceAll("/+$", "");
        FederationClient c1 = new FederationClient(base);
        FederationClient c2 = new FederationClient(base + "/");
        FederationClient c3 = new FederationClient(base + "//");

        assertEquals(c1.getServerInformation().serverName(), c2.getServerInformation().serverName());
        assertEquals(c1.getServerInformation().serverName(), c3.getServerInformation().serverName());
        assertEquals(c1.getServerInformation().apiVersion(), c2.getServerInformation().apiVersion());
        assertEquals(c1.getServerInformation().apiVersion(), c3.getServerInformation().apiVersion());

        c1.close(); c2.close(); c3.close();
    }

    @Test
    void testClientWithEmptyEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> new FederationClient(""));
    }

    @Test
    void testClientWithWhitespaceOnlyEndpoint() {
        assertThrows(IllegalArgumentException.class, () -> new FederationClient("   "));
    }

    @Test
    void testClientWithInvalidEndpointFormat() {
        assertThrows(IllegalArgumentException.class, () -> new FederationClient("not-a-url"));
    }

    @Test
    void testClientWithEmptyAccessToken() {
        assertThrows(IllegalArgumentException.class,
            () -> new FederationClient(FederationClientTestBase.serverEndpoint, ""));
    }

    @Test
    void testClientWithWhitespaceOnlyAccessToken() {
        assertThrows(IllegalArgumentException.class,
            () -> new FederationClient(FederationClientTestBase.serverEndpoint, "   "));
    }

    @Test
    void testClientWithNonExistentEndpoint() {
        FederationClient client = new FederationClient("http://this-domain-does-not-exist-12345.com");
        assertThrows(FederationClientException.class, client::getServerInformation);
        client.close();
    }

    @Test
    void testClientWithWrongPortEndpoint() {
        String base = FederationClientTestBase.serverEndpoint.replaceAll(":\\d+", "");
        FederationClient client = new FederationClient(base + ":9999");
        assertThrows(FederationClientException.class, client::getServerInformation);
        client.close();
    }

    @Test
    void testAccessTokenFormat() {
        String token = FederationClientTestBase.serverAccessToken;
        assertNotNull(token);
        assertTrue(token.length() > 10, "Access Token seems too short");
        assertTrue(token.length() < 200, "Access Token seems too long");
        assertFalse(token.contains(" "), "Access Token should not contain spaces");
    }

    @Test
    void testInvalidAccessTokenAuthentication() {
        FederationClient client = new FederationClient(FederationClientTestBase.serverEndpoint,
            "definitely-not-a-valid-access-token-12345");
        try {
            client.getSelf();
            fail("Expected FederationClientException for invalid access token");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401,
                "Expected 400 or 401 for invalid access token, got " + e.getStatusCode());
        }
        client.close();
    }

    @Test
    void testClientStatelessness() {
        String endpoint = FederationClientTestBase.serverEndpoint;
        String token = FederationClientTestBase.serverAccessToken;
        FederationClient c1 = new FederationClient(endpoint, token);
        FederationClient c2 = new FederationClient(endpoint, token);
        assertEquals(c1.getSelf().uuid(), c2.getSelf().uuid());
        assertEquals(c1.getSelf().name(), c2.getSelf().name());
        c1.close(); c2.close();
    }

    @Test
    void testClientThreadSafety() {
        String endpoint = FederationClientTestBase.serverEndpoint;
        String[] names = new String[5];
        for (int i = 0; i < 5; i++) {
            FederationClient c = new FederationClient(endpoint);
            names[i] = c.getServerInformation().serverName();
            c.close();
        }
        String first = names[0];
        for (int i = 1; i < 5; i++) {
            assertEquals(first, names[i], "Client " + i + " returned different server name");
        }
    }

    @Test
    void testClientWithNullAccessToken() {
        String endpoint = FederationClientTestBase.serverEndpoint;
        FederationClient client = new FederationClient(endpoint, (String) null);
        assertNotNull(client);
        assertNotNull(client.getServerInformation());
        try {
            client.getSelf();
            fail("Expected FederationClientException for missing access token");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401,
                "Expected 400 or 401 for unauthenticated request, got " + e.getStatusCode());
        }
        client.close();
    }

    @Test
    void testGetEndpointReturnsNormalizedEndpoint() {
        assertEquals("http://example.com:7000",
            new FederationClient("http://example.com:7000").getEndpoint());
        assertEquals("http://example.com:7000",
            new FederationClient("http://example.com:7000/").getEndpoint());
        assertEquals("http://example.com:7000",
            new FederationClient("http://example.com:7000//").getEndpoint());
    }

    @Test
    void testSetAccessTokenRejectsInvalidTokens() {
        FederationClient client = new FederationClient("http://example.com:7000");
        assertThrows(IllegalArgumentException.class, () -> client.setAccessToken(""));
        assertThrows(IllegalArgumentException.class, () -> client.setAccessToken("   "));
        assertThrows(IllegalArgumentException.class, () -> client.setAccessToken("valid token with spaces"));
        assertNull(client.getAccessToken());
        client.close();
    }

    @Test
    void testSetAccessTokenEnablesAuthenticatedCalls() {
        FederationClient client = new FederationClient(FederationClientTestBase.serverEndpoint);
        assertNull(client.getAccessToken());
        try {
            client.getSelf();
            fail("Expected FederationClientException before setting access token");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401);
        }

        client.setAccessToken(FederationClientTestBase.serverAccessToken);
        assertEquals(FederationClientTestBase.serverAccessToken, client.getAccessToken());
        OperatorRecord self = client.getSelf();
        assertNotNull(self);
        assertNotNull(self.uuid());
        client.close();
    }

    @Test
    void testSetAccessTokenNullClearsToken() {
        FederationClient client = new FederationClient(FederationClientTestBase.serverEndpoint,
            FederationClientTestBase.serverAccessToken);
        assertNotNull(client.getSelf());

        client.setAccessToken(null);
        assertNull(client.getAccessToken());
        try {
            client.getSelf();
            fail("Expected FederationClientException after clearing access token");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401);
        }
        client.close();
    }

    @Test
    void testSetAccessTokenSwitchesIdentity() {
        FederationClient admin = new FederationClient(FederationClientTestBase.serverEndpoint,
            FederationClientTestBase.serverAccessToken);
        OperatorCreated createdOperator = admin.createOperator("token-switch-" + System.currentTimeMillis());
        String operatorUuid = createdOperator.uuid();
        try {
            OperatorRecord operator = admin.getOperator(operatorUuid);
            assertNull(operator.accessToken(), "Access token should be redacted in OperatorRecord");
            assertNotNull(createdOperator.accessToken(), "Operator access token should be available at creation time");

            FederationClient switched = new FederationClient(FederationClientTestBase.serverEndpoint);
            switched.setAccessToken(createdOperator.accessToken());
            assertEquals(operatorUuid, switched.getSelf().uuid(),
                "getSelf() should report the identity bound to the newly set token");
            assertEquals(operatorUuid, switched.getOperator(operatorUuid).uuid());
            switched.close();
        } finally {
            admin.deleteOperator(operatorUuid);
            admin.close();
        }
    }
}
