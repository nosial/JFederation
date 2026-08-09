package net.nosial.jfederation;

import net.nosial.jfederation.exceptions.FederationClientException;
import net.nosial.jfederation.records.ServerInformation;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest extends FederationClientTestBase {

    @Test
    void testServerInformationConsistency() {
        ServerInformation info1 = client.getServerInformation();
        assertNotNull(info1);
        for (int i = 0; i < 5; i++) {
            ServerInformation info = client.getServerInformation();
            assertEquals(info1.serverName(), info.serverName());
            assertEquals(info1.apiVersion(), info.apiVersion());
            assertEquals(info1.publicEntities(), info.publicEntities());
            assertEquals(info1.publicEvidence(), info.publicEvidence());
        }
    }

    @Test
    void testUnauthenticatedClientLimitations() {
        FederationClient anon = createAnonymousClient();
        assertNotNull(anon.getServerInformation());
        try {
            anon.createOperator("test");
            fail("Expected FederationClientException for unauthenticated createOperator");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401,
                "Expected 400 or 401, got " + e.getStatusCode());
        }
        anon.close();
    }

    @Test
    void testUnauthenticatedGetSelfFails() {
        FederationClient anon = createAnonymousClient();
        try {
            anon.getSelf();
            fail("Expected FederationClientException for unauthenticated getSelf");
        } catch (FederationClientException e) {
            assertTrue(e.getStatusCode() == 400 || e.getStatusCode() == 401,
                "Expected 400 or 401, got " + e.getStatusCode());
        }
        anon.close();
    }

    @Test
    void testClientEndpointHandling() {
        String endpoint = FederationClientTestBase.serverEndpoint;
        assertNotNull(endpoint);

        FederationClient withSlash = new FederationClient(endpoint + "/");
        ServerInformation info1 = withSlash.getServerInformation();
        assertNotNull(info1);

        FederationClient noSlash = new FederationClient(endpoint.replaceAll("/+$", ""));
        ServerInformation info2 = noSlash.getServerInformation();
        assertNotNull(info2);

        assertEquals(info1.serverName(), info2.serverName());
        assertEquals(info1.apiVersion(), info2.apiVersion());

        withSlash.close();
        noSlash.close();
    }

    @Test
    void testSecurityCorsDoesNotAllowWildcardOrigin() throws Exception {
        String url = FederationClientTestBase.serverEndpoint.replaceAll("/+$", "") + "/info";
        HttpClient hc = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Origin", "https://example.evil")
            .build();
        HttpResponse<String> resp = hc.send(req, HttpResponse.BodyHandlers.ofString());
        String headers = resp.headers().firstValue("access-control-allow-origin").orElse("");
        assertNotEquals("*", headers, "Server should not return a wildcard CORS header for arbitrary origins");
    }
}
