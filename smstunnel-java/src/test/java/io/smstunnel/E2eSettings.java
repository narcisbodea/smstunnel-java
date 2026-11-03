package io.smstunnel;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

/**
 * Settings of the end-to-end tests, from environment variables. They run only when SMSTUNNEL_E2E_KEY_FILE is set, and
 * only against a test server: never production.
 *
 * <ul>
 *   <li>SMSTUNNEL_E2E_KEY_FILE: a JSON file {@code {"serverUrl": "...", "apiKey": "...", "keys": {"name": "..."}}}</li>
 *   <li>SMSTUNNEL_E2E_KEY_NAME: which entry of {@code keys} to use (otherwise {@code apiKey})</li>
 *   <li>SMSTUNNEL_E2E_SERVER_URL: overrides {@code serverUrl} (from a container: http://host.docker.internal:...)</li>
 *   <li>SMSTUNNEL_E2E_MONGO_URL: optional, the test server's database, read to check what it stored</li>
 * </ul>
 */
final class E2eSettings {

    final String serverUrl;
    final String apiKey;
    final String mongoUrl;

    private E2eSettings(String serverUrl, String apiKey, String mongoUrl) {
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.mongoUrl = mongoUrl;
    }

    static E2eSettings load() throws IOException {
        String file = System.getenv("SMSTUNNEL_E2E_KEY_FILE");
        Map<String, Object> json = Fields.object(Json.parse(
                new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8)));
        String keyName = System.getenv("SMSTUNNEL_E2E_KEY_NAME");
        String apiKey = keyName != null && !keyName.isEmpty()
                ? Fields.string(Fields.object(json, "keys"), keyName)
                : Fields.string(json, "apiKey");
        if (apiKey == null) {
            throw new IllegalStateException("No test key in " + file + (keyName != null ? " under keys." + keyName : ""));
        }
        String serverUrl = System.getenv("SMSTUNNEL_E2E_SERVER_URL");
        if (serverUrl == null || serverUrl.isEmpty()) {
            serverUrl = Fields.string(json, "serverUrl");
        }
        String host = URI.create(serverUrl).getHost().toLowerCase(Locale.ROOT);
        if (host.equals("smstunnel.io") || host.endsWith(".smstunnel.io")) {
            throw new IllegalStateException("The end-to-end tests never run against production: " + serverUrl);
        }
        String mongoUrl = System.getenv("SMSTUNNEL_E2E_MONGO_URL");
        return new E2eSettings(serverUrl, apiKey, mongoUrl == null || mongoUrl.isEmpty() ? null : mongoUrl);
    }

    SmsTunnelClient client() {
        return SmsTunnelClient.builder().apiKey(apiKey).baseUrl(serverUrl).userAgentSuffix("e2e-test").build();
    }
}
