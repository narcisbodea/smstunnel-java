package io.smstunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmsTunnelClientTest {

    /** A made-up key, built from pieces: written in one piece it looks like a real key to secret scanners. */
    private static final String KEY = "sk_" + "live_" + "unit_test_key_0000";
    private static final String SENT = "{\"success\":true,\"messageId\":\"6aea0000000000000000a001\","
            + "\"data\":{\"messageId\":\"6aea0000000000000000a001\",\"branded\":false,"
            + "\"remaining\":{\"clean\":95,\"total\":\"unlimited\"}}}";

    private MockSmsTunnel server;

    @BeforeEach
    void start() throws Exception {
        server = new MockSmsTunnel();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private SmsTunnelClient.Builder builder() {
        return SmsTunnelClient.builder().apiKey(KEY).baseUrl(server.baseUrl()).retryBaseDelay(Duration.ofMillis(1));
    }

    private SmsTunnelClient client() {
        return builder().build();
    }

    // ─── Sending ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void sendsOneTextWithTheKeyAndJson() {
        server.reply(200, SENT);

        SendResult result = client().sendSms("+14155550123", "Your order #1042 has shipped.");

        assertEquals("6aea0000000000000000a001", result.getMessageId());
        assertFalse(result.isQueued());
        assertFalse(result.isBranded());
        assertFalse(result.isReplayed());
        assertEquals(95L, result.getRemaining().get("clean"));

        MockSmsTunnel.Recorded request = server.lastRequest();
        assertEquals("POST", request.method);
        assertEquals("/api/v1/sms/send", request.path);
        assertEquals(KEY, request.header("X-API-Key"));
        assertTrue(request.header("Content-Type").startsWith("application/json"));
        assertTrue(request.header("User-Agent").startsWith("smstunnel-java/"));
        assertNull(request.header("Upgrade"), "plain http must not ask for an h2c upgrade");
        Map<String, Object> body = request.json();
        assertEquals("+14155550123", body.get("to"));
        assertEquals("Your order #1042 has shipped.", body.get("message"));
        assertFalse(body.containsKey("deviceId"));
        assertFalse(body.containsKey("defaultCountryCode"));
        assertNotNull(request.header("Idempotency-Key"), "the client protects its own retries with a key");
    }

    @Test
    void sendsTheOptionsAndYourOwnIdempotencyKey() {
        server.reply(200, SENT);

        client().sendSms("0721 123 456", "Hello", SendOptions.builder()
                .deviceId("6aea3248fedd7cccac9abf42")
                .defaultCountryCode("+40")
                .idempotencyKey("order-1042-shipped")
                .build());

        MockSmsTunnel.Recorded request = server.lastRequest();
        Map<String, Object> body = request.json();
        assertEquals("0721 123 456", body.get("to"));
        assertEquals("6aea3248fedd7cccac9abf42", body.get("deviceId"));
        assertEquals("+40", body.get("defaultCountryCode"));
        assertEquals("order-1042-shipped", request.header("Idempotency-Key"));
    }

    @Test
    void theClientCountryCodeAppliesUnlessTheSendGivesItsOwn() {
        server.reply(200, SENT).reply(200, SENT);
        SmsTunnelClient client = builder().defaultCountryCode("49").build();

        client.sendSms("0151 2345678", "Hallo");
        assertEquals("49", server.lastRequest().json().get("defaultCountryCode"));

        client.sendSms("0721 123 456", "Salut", SendOptions.builder().defaultCountryCode("0040").build());
        assertEquals("0040", server.lastRequest().json().get("defaultCountryCode"));
    }

    @Test
    void sendsSignInCodesThroughTheirOwnRoute() {
        server.reply(200, SENT);

        client().sendSignInCode("+14155550123", "Your sign-in code is 482913");

        assertEquals("/api/v1/sms/send-2fa", server.lastRequest().path);
        assertEquals("Your sign-in code is 482913", server.lastRequest().json().get("message"));
    }

    @Test
    void keepsLettersFromEveryLanguage() {
        server.reply(200, SENT);

        client().sendSms("+14155550123", "Comanda ta a plecat: ăîșț ÂÎȘȚ — 😀");

        assertEquals("Comanda ta a plecat: ăîșț ÂÎȘȚ — 😀", server.lastRequest().json().get("message"));
    }

    @Test
    void reportsAQueuedTextAndAReplayedAnswer() {
        server.reply(200, "{\"success\":true,\"messageId\":\"m1\",\"queued\":true,\"data\":{\"messageId\":\"m1\","
                + "\"branded\":true,\"queued\":true,\"remaining\":{}}}", Collections.singletonMap("Idempotent-Replayed", "true"));

        SendResult result = client().sendSms("+14155550123", "Hi", SendOptions.builder().idempotencyKey("k1").build());

        assertTrue(result.isQueued());
        assertTrue(result.isBranded());
        assertTrue(result.isReplayed());
    }

    @Test
    void refusesAnEmptyNumberOrTextBeforeSending() {
        SmsTunnelClient client = client();
        assertThrows(IllegalArgumentException.class, () -> client.sendSms(" ", "Hi"));
        assertThrows(IllegalArgumentException.class, () -> client.sendSms("+14155550123", ""));
        assertThrows(IllegalArgumentException.class, () -> client.sendSignInCode(null, "123456"));
        assertTrue(server.requests().isEmpty());
    }

    // ─── Reading ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void readsAStatus() {
        server.reply(200, "{\"success\":true,\"data\":{\"id\":\"m1\",\"phoneNumber\":\"+14155550123\",\"status\":\"sent\","
                + "\"deliveryStatus\":\"sent\",\"sentAt\":\"2026-11-03T19:13:38.656Z\",\"errorMessage\":null}}");

        MessageStatus status = client().getMessageStatus("m1");

        assertEquals("/api/v1/sms/status/m1", server.lastRequest().path);
        assertEquals("GET", server.lastRequest().method);
        assertEquals(DeliveryStatus.SENT, status.getDeliveryStatus());
        assertEquals("sent", status.getStatus());
        assertEquals(Instant.parse("2026-11-03T19:13:38.656Z"), status.getSentAt());
        assertFalse(status.isFinal());
        assertNull(status.getErrorMessage());
    }

    @Test
    void readsAnUnconfirmedText() {
        server.reply(200, "{\"success\":true,\"data\":{\"id\":\"m2\",\"status\":\"failed\",\"unconfirmed\":true,"
                + "\"deliveryStatus\":\"unconfirmed\",\"errorMessage\":\"Not confirmed by the phone\"}}");

        MessageStatus status = client().getMessageStatus("m2");

        assertEquals("failed", status.getStatus());
        assertEquals(DeliveryStatus.UNCONFIRMED, status.getDeliveryStatus());
        assertTrue(status.isUnconfirmed());
        assertTrue(status.isFinal());
    }

    @Test
    void encodesTheMessageIdInThePath() {
        server.reply(200, "{\"success\":true,\"data\":{\"id\":\"x\",\"status\":\"pending\"}}");

        client().getMessageStatus("a/b c");

        assertEquals("/api/v1/sms/status/a%2Fb%20c", server.lastRequest().path);
    }

    @Test
    void readsThePhones() {
        server.reply(200, "{\"success\":true,\"data\":{\"devices\":[{\"id\":\"d1\",\"name\":\"Test phone\",\"isOnline\":true,"
                + "\"lastSeenAt\":\"2026-11-03T19:13:38.656Z\"},{\"id\":\"d2\",\"name\":\"Old phone\",\"isOnline\":false}],"
                + "\"online\":1,\"total\":2}}");

        List<Phone> phones = client().getPhones();

        assertEquals("/api/v1/devices", server.lastRequest().path);
        assertEquals(2, phones.size());
        assertEquals("d1", phones.get(0).getId());
        assertEquals("Test phone", phones.get(0).getName());
        assertTrue(phones.get(0).isOnline());
        assertNotNull(phones.get(0).getLastSeenAt());
        assertFalse(phones.get(1).isOnline());
        assertNull(phones.get(1).getLastSeenAt());
    }

    @Test
    void readsTheUsage() {
        server.reply(200, "{\"success\":true,\"data\":{\"plan\":\"free\",\"accountType\":\"standard\","
                + "\"usage\":{\"smsClean\":3,\"smsBranded\":0,\"twoFaClean\":1},"
                + "\"limits\":{\"smsClean\":25,\"twoFaClean\":\"unlimited\"},"
                + "\"remaining\":{\"smsClean\":22,\"twoFaClean\":\"unlimited\"}}}");

        Usage usage = client().getUsage();

        assertEquals("/api/v1/account/usage", server.lastRequest().path);
        assertEquals("free", usage.getPlan());
        assertEquals(3L, usage.getUsage().get("smsClean"));
        assertEquals(22L, usage.getRemainingTexts());
        assertEquals(Usage.UNLIMITED, usage.getRemainingSignInCodes());
        assertNull(usage.getEnterprise());
    }

    @Test
    void readsReceivedTextsWithTheFilters() {
        server.reply(200, "{\"success\":true,\"data\":{\"messages\":[{\"id\":\"r1\",\"sender\":\"+14155550199\","
                + "\"message\":\"STOP\",\"isEncrypted\":false,\"receivedAt\":\"2026-11-03T10:00:00.000Z\",\"simSlot\":0,"
                + "\"deviceId\":\"d1\",\"deviceName\":\"Test phone\"}],\"total\":7,\"page\":2,\"limit\":1,\"hasMore\":true}}");

        ReceivedTexts page = client().getReceivedTexts(ReceivedTextsQuery.builder()
                .limit(1).page(2).since(Instant.parse("2026-11-01T00:00:00Z")).phone("+1415").deviceId("d1").build());

        MockSmsTunnel.Recorded request = server.lastRequest();
        assertEquals("/api/v1/sms/received", request.path);
        assertEquals("limit=1&page=2&since=2026-11-01T00%3A00%3A00Z&phone=%2B1415&deviceId=d1", request.query);
        assertEquals(1, page.getTexts().size());
        ReceivedText text = page.iterator().next();
        assertEquals("+14155550199", text.getSender());
        assertEquals("STOP", text.getMessage());
        assertEquals(Integer.valueOf(0), text.getSimSlot());
        assertEquals(7, page.getTotal());
        assertEquals(2, page.getPage());
        assertTrue(page.hasMore());
    }

    @Test
    void readsTheLatestReceivedTextsWithoutFilters() {
        server.reply(200, "{\"success\":true,\"data\":{\"messages\":[],\"total\":0,\"page\":1,\"limit\":50,\"hasMore\":false}}");

        ReceivedTexts page = client().getReceivedTexts();

        assertNull(server.lastRequest().query);
        assertTrue(page.getTexts().isEmpty());
        assertFalse(page.hasMore());
    }

    // ─── Errors ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void aWrongKeyIsAnAuthenticationError() {
        server.reply(401, "{\"message\":\"Invalid or expired API key\",\"error\":\"Unauthorized\",\"statusCode\":401}");

        SmsTunnelAuthenticationException e = assertThrows(SmsTunnelAuthenticationException.class,
                () -> client().sendSms("+14155550123", "Hi"));

        assertEquals(401, e.getStatusCode());
        assertEquals("Invalid or expired API key", e.getMessage());
        assertTrue(e.toString().contains("HTTP 401"));
        assertEquals(1, server.requests().size(), "a 401 is never retried");
    }

    @Test
    void aMissingPermissionCarriesItsCode() {
        server.reply(403, "{\"statusCode\":403,\"error\":\"Forbidden\",\"code\":\"PERMISSION_REQUIRED\","
                + "\"permission\":\"sms:inbox\",\"message\":\"This API key may not read received texts.\"}");

        SmsTunnelPermissionException e = assertThrows(SmsTunnelPermissionException.class,
                () -> client().getReceivedTexts());

        assertEquals("PERMISSION_REQUIRED", e.getCode());
        assertEquals("sms:inbox", e.getPermission());
        assertEquals("This API key may not read received texts.", e.getMessage());
        assertEquals(1, server.requests().size());
    }

    @Test
    void tooManyRequestsCarriesRetryAfterAndIsNeverRetried() {
        server.reply(429, "{\"statusCode\":429,\"message\":\"ThrottlerException: Too Many Requests\"}",
                Collections.singletonMap("Retry-After", "17"));

        SmsTunnelRateLimitException e = assertThrows(SmsTunnelRateLimitException.class,
                () -> client().sendSms("+14155550123", "Hi"));

        assertEquals(Duration.ofSeconds(17), e.getRetryAfter());
        assertEquals(1, server.requests().size());
    }

    @Test
    void retryAfterCanBeADate() {
        String in30s = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30));
        server.reply(429, "{\"message\":\"Too Many Requests\"}", Collections.singletonMap("Retry-After", in30s));

        SmsTunnelRateLimitException e = assertThrows(SmsTunnelRateLimitException.class, () -> client().getUsage());

        assertTrue(e.getRetryAfter().getSeconds() > 20 && e.getRetryAfter().getSeconds() <= 30, e.getRetryAfter().toString());
    }

    @Test
    void aDailyLimitHasNoRetryAfter() {
        server.reply(429, "{\"statusCode\":429,\"message\":\"Today's 25 texts on the Free plan are used up.\"}");

        SmsTunnelRateLimitException e = assertThrows(SmsTunnelRateLimitException.class,
                () -> client().sendSms("+14155550123", "Hi"));

        assertNull(e.getRetryAfter());
        assertEquals("Today's 25 texts on the Free plan are used up.", e.getMessage());
    }

    @Test
    void aSignInTextWithoutACodeIsABadRequest() {
        server.reply(400, "{\"statusCode\":400,\"error\":\"Bad Request\",\"message\":\"Invalid 2FA message: No valid"
                + " verification code found. Put the code in the text as a number of its own.\"}");

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class,
                () -> client().sendSignInCode("+14155550123", "Your code is ABCDEF"));

        assertEquals(400, e.getStatusCode());
        assertTrue(e.getMessage().contains("No valid verification code found"));
        assertFalse(e instanceof SmsTunnelServerException);
    }

    @Test
    void joinsAListOfValidationMessages() {
        server.reply(400, "{\"statusCode\":400,\"message\":[\"to must be a string\",\"message must be a string\"]}");

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class, () -> client().sendSms("x", "y"));

        assertEquals("to must be a string; message must be a string", e.getMessage());
    }

    @Test
    void aServerErrorIsAServerException() {
        server.reply(500, "{\"statusCode\":500,\"message\":\"Internal server error\"}");

        SmsTunnelServerException e = assertThrows(SmsTunnelServerException.class,
                () -> client().sendSms("+14155550123", "Hi"));

        assertEquals(500, e.getStatusCode());
        assertEquals(1, server.requests().size(), "a stored 500 would only be replayed: no retry");
    }

    @Test
    void aProxyErrorPageBecomesItsText() {
        server.reply(502, "<html><body>502 Proxy Error</body></html>")
                .reply(502, "<html><body>502 Proxy Error</body></html>")
                .reply(502, "<html><body>502 Proxy Error</body></html>");

        SmsTunnelServerException e = assertThrows(SmsTunnelServerException.class, () -> client().getPhones());

        assertEquals(502, e.getStatusCode());
        assertEquals("<html><body>502 Proxy Error</body></html>", e.getMessage());
        assertEquals(3, server.requests().size(), "a read is tried 1 + 2 times");
    }

    @Test
    void anAnswerThatIsNotJsonIsAnError() {
        server.reply(200, "OK");

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class, () -> client().getUsage());

        assertTrue(e.getMessage().contains("not JSON"));
    }

    @Test
    void aRedirectIsNotFollowed() {
        server.reply(301, "", Collections.singletonMap("Location", "https://elsewhere.example/api"));

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class, () -> client().getUsage());

        assertEquals(301, e.getStatusCode());
        assertTrue(e.getMessage().contains("https://elsewhere.example/api"));
        assertEquals(1, server.requests().size());
    }

    // ─── Retries ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void aSendIsRetriedWithTheSameIdempotencyKey() {
        server.reply(503, "{\"message\":\"Service Unavailable\"}").reply(200, SENT);

        SendResult result = client().sendSms("+14155550123", "Hi");

        assertEquals("6aea0000000000000000a001", result.getMessageId());
        List<MockSmsTunnel.Recorded> requests = server.requests();
        assertEquals(2, requests.size());
        String key = requests.get(0).header("Idempotency-Key");
        assertNotNull(key);
        assertEquals(key, requests.get(1).header("Idempotency-Key"));
        assertEquals(requests.get(0).body, requests.get(1).body);
    }

    @Test
    void aSendWithoutAKeyIsNeverRetried() {
        server.reply(503, "{\"message\":\"Service Unavailable\"}").reply(200, SENT);

        SmsTunnelClient client = builder().autoIdempotencyKey(false).build();

        assertThrows(SmsTunnelServerException.class, () -> client.sendSms("+14155550123", "Hi"));
        assertEquals(1, server.requests().size());
        assertNull(server.lastRequest().header("Idempotency-Key"));
    }

    @Test
    void noRetriesWhenTurnedOff() {
        server.reply(503, "{}").reply(200, SENT);

        SmsTunnelClient client = builder().maxRetries(0).build();

        assertThrows(SmsTunnelServerException.class, () -> client.sendSms("+14155550123", "Hi"));
        assertEquals(1, server.requests().size());
        assertNull(server.lastRequest().header("Idempotency-Key"), "no retries, so no key of the client's own");
    }

    @Test
    void waitsWhileTheSameKeyIsStillInProgress() {
        server.reply(409, "{\"statusCode\":409,\"code\":\"IDEMPOTENCY_KEY_IN_PROGRESS\",\"message\":\"in progress\"}",
                Collections.singletonMap("Retry-After", "0")).reply(200, SENT);

        SendResult result = client().sendSms("+14155550123", "Hi", SendOptions.builder().idempotencyKey("k-409").build());

        assertEquals("6aea0000000000000000a001", result.getMessageId());
        assertEquals(2, server.requests().size());
    }

    @Test
    void aReusedKeyIsNotRetried() {
        server.reply(422, "{\"statusCode\":422,\"code\":\"IDEMPOTENCY_KEY_REUSED\",\"message\":\"reused\"}");

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class,
                () -> client().sendSms("+14155550123", "Hi", SendOptions.builder().idempotencyKey("k-422").build()));

        assertEquals("IDEMPOTENCY_KEY_REUSED", e.getCode());
        assertEquals(1, server.requests().size());
    }

    @Test
    void aTimeoutIsAConnectionErrorAndIsRetriedForReads() {
        server.reply(200, "{\"success\":true,\"data\":{}}", Collections.emptyMap(), 1500)
                .reply(200, "{\"success\":true,\"data\":{\"plan\":\"pro\"}}");

        Usage usage = builder().timeout(Duration.ofMillis(300)).build().getUsage();

        assertEquals("pro", usage.getPlan());
        assertEquals(2, server.requests().size());
    }

    @Test
    void aTimeoutWithoutRetriesSaysSo() {
        server.reply(200, "{\"success\":true,\"data\":{}}", Collections.emptyMap(), 1500);

        SmsTunnelConnectionException e = assertThrows(SmsTunnelConnectionException.class,
                () -> builder().timeout(Duration.ofMillis(300)).maxRetries(0).build().getUsage());

        assertTrue(e.isTimeout());
        assertTrue(e.getMessage().contains("did not answer within 300 ms"));
    }

    @Test
    void anUnreachableServerIsAConnectionError() throws Exception {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        SmsTunnelClient client = SmsTunnelClient.builder().apiKey(KEY).baseUrl("http://127.0.0.1:" + freePort)
                .maxRetries(0).build();

        SmsTunnelConnectionException e = assertThrows(SmsTunnelConnectionException.class, client::getPhones);

        assertFalse(e.isTimeout());
        assertTrue(e.getMessage().startsWith("Could not reach SMSTunnel at http://127.0.0.1:" + freePort));
    }

    // ─── Builder ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void theBuilderChecksItsSettings() {
        assertThrows(IllegalArgumentException.class, () -> SmsTunnelClient.builder().build());
        assertThrows(IllegalArgumentException.class, () -> SmsTunnelClient.builder().apiKey("  ").build());
        assertThrows(IllegalArgumentException.class, () -> SmsTunnelClient.builder().apiKey("sk live").build());
        assertThrows(IllegalArgumentException.class, () -> SmsTunnelClient.builder().apiKey(KEY).baseUrl("ftp://x").build());
        assertThrows(IllegalArgumentException.class, () -> SmsTunnelClient.builder().apiKey(KEY).baseUrl("smstunnel.io").build());
        assertThrows(IllegalArgumentException.class, () -> SmsTunnelClient.builder().apiKey(KEY).baseUrl("https://x.io/?a=1").build());
        assertThrows(IllegalArgumentException.class, () -> SmsTunnelClient.builder().apiKey(KEY).defaultCountryCode("+0").build());
        assertThrows(IllegalArgumentException.class, () -> SmsTunnelClient.builder().apiKey(KEY).maxRetries(9));
        assertThrows(IllegalArgumentException.class, () -> SmsTunnelClient.builder().apiKey(KEY)
                .httpClient(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()).build());
        assertThrows(IllegalArgumentException.class, () -> SendOptions.builder().idempotencyKey(""));
        assertThrows(IllegalArgumentException.class, () -> SendOptions.builder().idempotencyKey("ă"));
        assertThrows(IllegalArgumentException.class, () -> ReceivedTextsQuery.builder().limit(101));
    }

    @Test
    void theBaseUrlDefaultsToSmsTunnelAndLosesItsTrailingSlash() {
        assertEquals("https://smstunnel.io", SmsTunnelClient.builder().apiKey(KEY).build().getBaseUrl());
        assertEquals("https://sms.example.com/base",
                SmsTunnelClient.builder().apiKey(KEY).baseUrl("https://sms.example.com/base/").build().getBaseUrl());
    }

    @Test
    void neverShowsTheKey() {
        assertFalse(client().toString().contains(KEY));
    }

    @Test
    void knowsItsVersion() {
        assertFalse(SmsTunnelClient.VERSION.isEmpty());
        assertTrue(server.requests().isEmpty());
    }
}
