package io.smstunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScheduledTextsTest {

    /** A made-up key, built from pieces: written in one piece it looks like a real key to secret scanners. */
    private static final String KEY = "sk_" + "live_" + "unit_test_key_0000";
    private static final String ID = "6aeb0000000000000000c001";
    private static final Instant AT = Instant.parse("2026-11-16T09:00:00Z");
    private static final String ITEM = "{\"id\":\"" + ID + "\",\"to\":\"+14155550123\","
            + "\"message\":\"Your appointment is tomorrow at 10:30.\",\"sendAt\":\"2026-11-16T09:00:00.000Z\","
            + "\"status\":\"scheduled\",\"deviceId\":null,\"messageId\":null,\"error\":null,"
            + "\"createdAt\":\"2026-11-15T12:00:00.000Z\"}";
    private static final String SCHEDULED = "{\"success\":true,\"scheduled\":true,\"scheduledId\":\"" + ID + "\","
            + "\"sendAt\":\"2026-11-16T09:00:00.000Z\",\"data\":" + ITEM + "}";
    private static final String CANCELED = "{\"success\":true,\"data\":" + ITEM.replace("\"scheduled\"", "\"canceled\"") + "}";
    private static final String NO_TEXTS = "{\"success\":true,\"data\":{\"items\":[],\"total\":0,\"page\":1,\"limit\":50,"
            + "\"waiting\":0,\"max\":10}}";
    /** What a server that ignored sendAt would answer: the text went out now. */
    private static final String SENT_NOW = "{\"success\":true,\"messageId\":\"6aea0000000000000000a001\","
            + "\"data\":{\"messageId\":\"6aea0000000000000000a001\",\"branded\":false}}";
    /** SMSTunnel's check of sendAt (scheduled-texts.rules.ts). */
    private static final Pattern SERVER_FORMAT =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,3})?)?(Z|[+-]\\d{2}:\\d{2})$");

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

    // ─── Scheduling ──────────────────────────────────────────────────────────────────────────────

    @Test
    void schedulesATextAtAnInstant() {
        server.reply(200, SCHEDULED);

        ScheduledSendResult result = client().scheduleSms("+14155550123", "Your appointment is tomorrow at 10:30.", AT);

        MockSmsTunnel.Recorded request = server.lastRequest();
        assertEquals("POST", request.method);
        assertEquals("/api/v1/sms/send", request.path);
        assertEquals(KEY, request.header("X-API-Key"));
        assertTrue(request.header("Content-Type").startsWith("application/json"));
        assertNotNull(request.header("Idempotency-Key"), "the client protects its own retries with a key, as for a send");
        assertEquals("{\"to\":\"+14155550123\",\"message\":\"Your appointment is tomorrow at 10:30.\","
                + "\"sendAt\":\"2026-11-16T09:00:00Z\"}", request.body);

        assertEquals(ID, result.getScheduledId());
        assertEquals(AT, result.getSendAt());
        assertEquals(ScheduledTextStatus.SCHEDULED, result.getStatus());
        assertFalse(result.isReplayed());
        assertEquals(Boolean.TRUE, result.getRaw().get("scheduled"));
        ScheduledText text = result.getScheduledText();
        assertEquals(ID, text.getId());
        assertEquals("+14155550123", text.getTo());
        assertEquals("Your appointment is tomorrow at 10:30.", text.getMessage());
        assertEquals(AT, text.getSendAt());
        assertNull(text.getDeviceId());
        assertNull(text.getMessageId());
        assertNull(text.getError());
        assertEquals(Instant.parse("2026-11-15T12:00:00Z"), text.getCreatedAt());
        assertEquals(ID, text.getRaw().get("id"));
        assertFalse(result.toString().contains("appointment"), "toString leaves the words out: " + result);
    }

    @Test
    void keepsTheOffsetAndCutsTheTimeToWholeSeconds() {
        server.reply(200, SCHEDULED).reply(200, SCHEDULED).reply(200, SCHEDULED).reply(200, SCHEDULED);
        SmsTunnelClient client = client();

        client.scheduleSms("+14155550123", "Hi",
                OffsetDateTime.of(2026, 11, 16, 11, 0, 0, 123_456_789, ZoneOffset.ofHours(2)));
        assertEquals("2026-11-16T11:00:00+02:00", server.lastRequest().json().get("sendAt"));

        client.scheduleSms("+14155550123", "Hi",
                ZonedDateTime.of(2026, 11, 16, 9, 30, 15, 999_000_000, ZoneId.of("Europe/Bucharest")));
        assertEquals("2026-11-16T09:30:15+02:00", server.lastRequest().json().get("sendAt"));

        client.scheduleSms("+14155550123", "Hi",
                ZonedDateTime.of(2026, 11, 16, 4, 0, 0, 0, ZoneId.of("America/New_York")), SendOptions.none());
        assertEquals("2026-11-16T04:00:00-05:00", server.lastRequest().json().get("sendAt"));

        client.scheduleSms("+14155550123", "Hi", Instant.parse("2026-11-16T09:00:00.987654321Z"));
        assertEquals("2026-11-16T09:00:00Z", server.lastRequest().json().get("sendAt"));
    }

    @Test
    void anOffsetWithSecondsIsSentInUtc() {
        server.reply(200, SCHEDULED);

        client().scheduleSms("+14155550123", "Hi",
                OffsetDateTime.of(2026, 11, 16, 10, 0, 30, 0, ZoneOffset.ofHoursMinutesSeconds(1, 0, 30)));

        assertEquals("2026-11-16T09:00:00Z", server.lastRequest().json().get("sendAt"));
    }

    @Test
    void everyTimeIsWrittenTheWaySmsTunnelReadsIt() {
        List<OffsetDateTime> times = Arrays.asList(
                OffsetDateTime.now(),
                OffsetDateTime.now(ZoneOffset.UTC),
                Instant.now().atOffset(ZoneOffset.UTC),
                ZonedDateTime.now(ZoneId.of("Asia/Kathmandu")).toOffsetDateTime(),
                OffsetDateTime.of(2026, 11, 16, 9, 0, 0, 1, ZoneOffset.ofHoursMinutes(5, 30)),
                OffsetDateTime.of(2026, 11, 16, 9, 0, 0, 0, ZoneOffset.ofHoursMinutesSeconds(-3, -25, -12)),
                ZonedDateTime.of(1890, 1, 1, 12, 0, 0, 0, ZoneId.of("Europe/Bucharest")).toOffsetDateTime());

        for (OffsetDateTime at : times) {
            String text = SmsTunnelClient.sendAtText(at);
            assertTrue(SERVER_FORMAT.matcher(text).matches(), text + " from " + at);
            assertTrue(text.length() <= 40, text);
            assertEquals(at.toInstant().truncatedTo(ChronoUnit.SECONDS), OffsetDateTime.parse(text).toInstant(), text);
        }
    }

    @Test
    void sendsTheOptionsWithTheTime() {
        server.reply(200, SCHEDULED);

        builder().defaultCountryCode("+40").build().scheduleSms("0721 123 456", "See you at 10:30.",
                Instant.parse("2026-11-16T07:00:00Z"), SendOptions.builder()
                        .deviceId("6aea3248fedd7cccac9abf42")
                        .idempotencyKey("appointment-381-reminder")
                        .build());

        MockSmsTunnel.Recorded request = server.lastRequest();
        assertEquals("{\"to\":\"0721 123 456\",\"message\":\"See you at 10:30.\",\"deviceId\":\"6aea3248fedd7cccac9abf42\","
                + "\"defaultCountryCode\":\"+40\",\"sendAt\":\"2026-11-16T07:00:00Z\"}", request.body);
        assertEquals("appointment-381-reminder", request.header("Idempotency-Key"));
    }

    @Test
    void refusesAMissingNumberTextTimeOrIdBeforeSending() {
        SmsTunnelClient client = client();
        Instant noInstant = null;
        OffsetDateTime noOffsetDateTime = null;
        ZonedDateTime noZonedDateTime = null;

        assertThrows(IllegalArgumentException.class, () -> client.scheduleSms(" ", "Hi", AT));
        assertThrows(IllegalArgumentException.class, () -> client.scheduleSms("+14155550123", "", AT));
        assertThrows(IllegalArgumentException.class, () -> client.scheduleSms("+14155550123", "Hi", noInstant));
        assertThrows(IllegalArgumentException.class, () -> client.scheduleSms("+14155550123", "Hi", noOffsetDateTime));
        assertThrows(IllegalArgumentException.class,
                () -> client.scheduleSms("+14155550123", "Hi", noZonedDateTime, SendOptions.none()));
        assertThrows(IllegalArgumentException.class, () -> client.getScheduledText(" "));
        assertThrows(IllegalArgumentException.class, () -> client.cancelScheduledText(null));
        assertThrows(IllegalArgumentException.class, () -> ScheduledTextsQuery.builder().limit(0));
        assertThrows(IllegalArgumentException.class, () -> ScheduledTextsQuery.builder().limit(101));
        assertThrows(IllegalArgumentException.class, () -> ScheduledTextsQuery.builder().page(0));
        assertTrue(server.requests().isEmpty());
    }

    @Test
    void saysWhenTheAnswerIsAReplay() {
        server.reply(200, SCHEDULED, Collections.singletonMap("Idempotent-Replayed", "true"));

        ScheduledSendResult result = client().scheduleSms("+14155550123", "Hi", AT,
                SendOptions.builder().idempotencyKey("k1").build());

        assertTrue(result.isReplayed());
        assertTrue(result.toString().contains("replayed=true"));
    }

    @Test
    void isRetriedWithTheSameIdempotencyKeyAndTime() {
        server.reply(503, "{\"message\":\"Service Unavailable\"}").reply(200, SCHEDULED);

        ScheduledSendResult result = client().scheduleSms("+14155550123", "Hi", OffsetDateTime.now().plusHours(2));

        assertEquals(ID, result.getScheduledId());
        List<MockSmsTunnel.Recorded> requests = server.requests();
        assertEquals(2, requests.size());
        assertNotNull(requests.get(0).header("Idempotency-Key"));
        assertEquals(requests.get(0).header("Idempotency-Key"), requests.get(1).header("Idempotency-Key"));
        assertEquals(requests.get(0).body, requests.get(1).body);
    }

    @Test
    void withoutAKeyItIsNeverRetried() {
        server.reply(503, "{\"message\":\"Service Unavailable\"}").reply(200, SCHEDULED);
        SmsTunnelClient client = builder().autoIdempotencyKey(false).build();

        assertThrows(SmsTunnelServerException.class, () -> client.scheduleSms("+14155550123", "Hi", AT));

        assertEquals(1, server.requests().size());
        assertNull(server.lastRequest().header("Idempotency-Key"));
    }

    @Test
    void aServerThatSentTheTextNowIsAnError() {
        server.reply(200, SENT_NOW);

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class,
                () -> client().scheduleSms("+14155550123", "Hi", AT));

        assertEquals(200, e.getStatusCode());
        assertTrue(e.getMessage().contains("instead of scheduling it (message id 6aea0000000000000000a001)"), e.getMessage());
        assertEquals(1, server.requests().size());
    }

    // ─── Listing, reading, canceling ─────────────────────────────────────────────────────────────

    @Test
    void listsTheWaitingTexts() {
        server.reply(200, "{\"success\":true,\"data\":{\"items\":[" + ITEM + ",{\"id\":\"6aeb0000000000000000c002\","
                + "\"to\":\"+14155550124\",\"message\":\"Hi\",\"sendAt\":\"2026-11-17T09:00:00.000Z\",\"status\":\"scheduled\","
                + "\"deviceId\":\"6aea3248fedd7cccac9abf42\",\"messageId\":null,\"error\":null,\"createdAt\":null}],"
                + "\"total\":2,\"page\":1,\"limit\":50,\"waiting\":7,\"max\":10}}");

        ScheduledTexts page = client().getScheduledTexts();

        MockSmsTunnel.Recorded request = server.lastRequest();
        assertEquals("GET", request.method);
        assertEquals("/api/v1/sms/scheduled", request.path);
        assertNull(request.query);
        assertNull(request.header("Idempotency-Key"));
        assertEquals(2, page.getTexts().size());
        assertEquals(ID, page.iterator().next().getId());
        ScheduledText second = page.getTexts().get(1);
        assertEquals("6aea3248fedd7cccac9abf42", second.getDeviceId());
        assertEquals(Instant.parse("2026-11-17T09:00:00Z"), second.getSendAt());
        assertNull(second.getCreatedAt());
        assertEquals(2, page.getTotal());
        assertEquals(1, page.getPage());
        assertEquals(50, page.getLimit());
        assertEquals(7, page.getWaiting());
        assertEquals(10, page.getMax());
        assertFalse(page.hasMore());
    }

    @Test
    void listsTheDoneTextsPageByPage() {
        server.reply(200, "{\"success\":true,\"data\":{\"items\":["
                + "{\"id\":\"c3\",\"status\":\"sent\",\"messageId\":\"6aea0000000000000000a001\",\"error\":null},"
                + "{\"id\":\"c4\",\"status\":\"failed\",\"messageId\":null,\"error\":\"limit_reached\"},"
                + "{\"id\":\"c5\",\"status\":\"canceled\"},"
                + "{\"id\":\"c6\",\"status\":\"held-by-martians\"}],"
                + "\"total\":31,\"page\":2,\"limit\":4,\"waiting\":0,\"max\":200}}");

        ScheduledTexts page = client().getScheduledTexts(ScheduledTextsQuery.builder()
                .scope(ScheduledTextScope.DONE).page(2).limit(4).build());

        assertEquals("scope=done&page=2&limit=4", server.lastRequest().query);
        List<ScheduledText> texts = page.getTexts();
        assertEquals(ScheduledTextStatus.SENT, texts.get(0).getStatus());
        assertEquals("6aea0000000000000000a001", texts.get(0).getMessageId());
        assertEquals(ScheduledTextStatus.FAILED, texts.get(1).getStatus());
        assertEquals("limit_reached", texts.get(1).getError());
        assertEquals(ScheduledTextStatus.CANCELED, texts.get(2).getStatus());
        assertEquals(ScheduledTextStatus.UNKNOWN, texts.get(3).getStatus());
        assertEquals("held-by-martians", texts.get(3).getRaw().get("status"));
        assertTrue(page.hasMore(), "2 pages of 4 are 8 of 31");
        assertEquals(200, page.getMax());
    }

    @Test
    void theShortcutsAskForTheirScope() {
        server.reply(200, NO_TEXTS).reply(200, NO_TEXTS).reply(200, NO_TEXTS).reply(200, NO_TEXTS);
        SmsTunnelClient client = client();

        client.getScheduledTexts(ScheduledTextsQuery.waiting());
        assertNull(server.lastRequest().query);
        client.getScheduledTexts(ScheduledTextsQuery.done());
        assertEquals("scope=done", server.lastRequest().query);
        client.getScheduledTexts(ScheduledTextsQuery.builder().scope(ScheduledTextScope.WAITING).build());
        assertEquals("scope=waiting", server.lastRequest().query);
        ScheduledTexts none = client.getScheduledTexts(null);
        assertNull(server.lastRequest().query);
        assertTrue(none.getTexts().isEmpty());
        assertFalse(none.hasMore());
    }

    @Test
    void readsOneScheduledText() {
        server.reply(200, "{\"success\":true,\"data\":" + ITEM + "}");

        ScheduledText text = client().getScheduledText(" " + ID + " ");

        assertEquals("GET", server.lastRequest().method);
        assertEquals("/api/v1/sms/scheduled/" + ID, server.lastRequest().path);
        assertEquals(ID, text.getId());
        assertEquals(ScheduledTextStatus.SCHEDULED, text.getStatus());
        assertEquals(AT, text.getSendAt());
        assertFalse(text.toString().contains("appointment"), "toString leaves the words out: " + text);
    }

    @Test
    void cancelsAScheduledText() {
        server.reply(200, CANCELED);

        ScheduledText text = client().cancelScheduledText(ID);

        MockSmsTunnel.Recorded request = server.lastRequest();
        assertEquals("DELETE", request.method);
        assertEquals("/api/v1/sms/scheduled/" + ID, request.path);
        assertEquals("", request.body);
        assertEquals(KEY, request.header("X-API-Key"));
        assertNull(request.header("Content-Type"));
        assertNull(request.header("Idempotency-Key"));
        assertEquals(ScheduledTextStatus.CANCELED, text.getStatus());
        assertEquals(ID, text.getId());
    }

    @Test
    void encodesTheIdInThePath() {
        server.reply(200, "{\"success\":true,\"data\":" + ITEM + "}").reply(200, CANCELED);
        SmsTunnelClient client = client();

        client.getScheduledText("a/b c");
        assertEquals("/api/v1/sms/scheduled/a%2Fb%20c", server.lastRequest().path);
        client.cancelScheduledText("a/b c");
        assertEquals("/api/v1/sms/scheduled/a%2Fb%20c", server.lastRequest().path);
    }

    // ─── Errors ──────────────────────────────────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource({
        "400, SCHEDULE_TOO_SOON",
        "400, SCHEDULE_TOO_FAR",
        "400, SCHEDULE_INVALID_INPUT",
        "400, SCHEDULE_TEXT_TOO_LONG",
        "400, SCHEDULE_NUMBER_NOT_ALLOWED",
        "400, SCHEDULE_SEALED_ONLY_PHONE",
        "400, SCHEDULE_ENCRYPTED_TEXT",
        "400, SCHEDULE_SIGN_IN_CODE",
        "404, SCHEDULE_PHONE_NOT_FOUND",
    })
    void aRefusedTextKeepsItsCode(int status, String code) throws Exception {
        server.reply(status, "{\"statusCode\":" + status + ",\"error\":\"Refused\",\"code\":\"" + code + "\","
                + "\"message\":\"SMSTunnel's words for " + code + "\"}");

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class,
                () -> client().scheduleSms("+14155550123", "Hi", AT));

        assertEquals(status, e.getStatusCode());
        assertEquals(code, e.getCode());
        assertEquals(SmsTunnelApiException.class.getField(code).get(null), e.getCode(), "the constant " + code);
        assertEquals("SMSTunnel's words for " + code, e.getMessage());
        assertFalse(e instanceof SmsTunnelServerException);
        assertEquals(1, server.requests().size(), "a refusal is never retried");
    }

    @Test
    void tooManyWaitingTextsIsAPermissionErrorWithThePlansCap() {
        server.reply(403, "{\"statusCode\":403,\"error\":\"Forbidden\",\"code\":\"SCHEDULE_LIMIT_REACHED\","
                + "\"message\":\"You have as many scheduled texts as your plan allows. Wait until some go out, or cancel"
                + " one.\",\"max\":10}");

        SmsTunnelPermissionException e = assertThrows(SmsTunnelPermissionException.class,
                () -> client().scheduleSms("+14155550123", "Hi", AT));

        assertEquals(SmsTunnelApiException.SCHEDULE_LIMIT_REACHED, e.getCode());
        assertEquals(Long.valueOf(10), e.getMax());
        assertTrue(e.getMessage().startsWith("You have as many scheduled texts as your plan allows."));
        assertEquals(1, server.requests().size());
    }

    @Test
    void anUnknownIdIsNotFound() {
        server.reply(404, "{\"statusCode\":404,\"error\":\"Not Found\",\"code\":\"SCHEDULE_NOT_FOUND\","
                + "\"message\":\"This scheduled text no longer exists.\"}");

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class, () -> client().getScheduledText(ID));

        assertEquals(404, e.getStatusCode());
        assertEquals(SmsTunnelApiException.SCHEDULE_NOT_FOUND, e.getCode());
        assertNull(e.getMax());
        assertEquals(1, server.requests().size(), "a 404 is never retried");
    }

    @Test
    void aTextThatAlreadyWentOutCantBeCanceled() {
        server.reply(409, "{\"statusCode\":409,\"error\":\"Conflict\",\"code\":\"SCHEDULE_NOT_EDITABLE\",\"message\":"
                + "\"This text has already gone out or was canceled, or was scheduled through the API (cancel it instead).\"}");

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class, () -> client().cancelScheduledText(ID));

        assertEquals(409, e.getStatusCode());
        assertEquals(SmsTunnelApiException.SCHEDULE_NOT_EDITABLE, e.getCode());
        assertEquals(1, server.requests().size());
    }

    @Test
    void aServerWithoutScheduledTextsRefusesSendAtWithA400() {
        server.reply(400, "{\"message\":[\"property sendAt should not exist\"],\"error\":\"Bad Request\",\"statusCode\":400}");

        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class,
                () -> client().scheduleSms("+14155550123", "Hi", AT));

        assertEquals(400, e.getStatusCode());
        assertEquals("property sendAt should not exist", e.getMessage());
        assertNull(e.getCode());
        assertFalse(e instanceof SmsTunnelServerException);
        assertEquals(1, server.requests().size());
    }

    @Test
    void tooManyRequestsCarriesRetryAfterAndIsNeverRetried() {
        server.reply(429, "{\"statusCode\":429,\"message\":\"ThrottlerException: Too Many Requests\"}",
                Collections.singletonMap("Retry-After", "17"));

        SmsTunnelRateLimitException e = assertThrows(SmsTunnelRateLimitException.class,
                () -> client().scheduleSms("+14155550123", "Hi", AT));

        assertEquals(Duration.ofSeconds(17), e.getRetryAfter());
        assertEquals(1, server.requests().size());
    }

    // ─── Retries of reads and cancels ────────────────────────────────────────────────────────────

    @Test
    void theListIsRetriedLikeEveryRead() {
        server.reply(503, "{\"message\":\"Service Unavailable\"}").reply(200, NO_TEXTS);

        client().getScheduledTexts();

        assertEquals(2, server.requests().size());
    }

    @Test
    void aCancelAnsweredWithAServerErrorIsNotSentAgain() {
        server.reply(503, "{\"message\":\"Service Unavailable\"}").reply(200, CANCELED);

        assertThrows(SmsTunnelServerException.class, () -> client().cancelScheduledText(ID));

        assertEquals(1, server.requests().size(), "it may have canceled the text: a second cancel would answer 409");
    }

    @Test
    void aCancelThatTimedOutIsNotSentAgain() {
        server.reply(200, CANCELED, Collections.emptyMap(), 1500).reply(200, CANCELED);

        SmsTunnelConnectionException e = assertThrows(SmsTunnelConnectionException.class,
                () -> builder().timeout(Duration.ofMillis(300)).build().cancelScheduledText(ID));

        assertTrue(e.isTimeout());
        assertEquals(1, server.requests().size());
    }

    @Test
    void aCancelIsSentAgainWhenTheConnectionCouldNotBeMade() {
        server.reply(200, CANCELED).reply(200, CANCELED);
        FailingFirst refused = new FailingFirst(1, () -> new ConnectException("Connection refused"));
        FailingFirst slow = new FailingFirst(1, () -> new HttpConnectTimeoutException("HTTP connect timed out"));

        assertEquals(ScheduledTextStatus.CANCELED, builder().httpClient(refused).build().cancelScheduledText(ID).getStatus());
        assertEquals(ScheduledTextStatus.CANCELED, builder().httpClient(slow).build().cancelScheduledText(ID).getStatus());

        assertEquals(2, refused.calls.get());
        assertEquals(2, slow.calls.get());
        assertEquals(2, server.requests().size(), "each cancel reached SMSTunnel once");
    }

    @Test
    void aCancelWhoseConnectionBrokeIsNotSentAgain() {
        server.reply(200, CANCELED);
        FailingFirst reset = new FailingFirst(1, () -> new IOException("Connection reset"));

        SmsTunnelConnectionException e = assertThrows(SmsTunnelConnectionException.class,
                () -> builder().httpClient(reset).build().cancelScheduledText(ID));

        assertFalse(e.isTimeout());
        assertEquals(1, reset.calls.get());
        assertTrue(server.requests().isEmpty());
    }

    /** An HttpClient whose first sends fail with an error of the test's choice; then it sends for real. */
    private static final class FailingFirst extends HttpClient {
        private final HttpClient real = HttpClient.newBuilder().followRedirects(Redirect.NEVER).build();
        private final AtomicInteger failures;
        private final Supplier<IOException> error;
        final AtomicInteger calls = new AtomicInteger();

        FailingFirst(int failures, Supplier<IOException> error) {
            this.failures = new AtomicInteger(failures);
            this.error = error;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
                throws IOException, InterruptedException {
            calls.incrementAndGet();
            if (failures.getAndDecrement() > 0) {
                throw error.get();
            }
            return real.send(request, handler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            return real.sendAsync(request, handler);
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return real.sendAsync(request, handler, pushPromiseHandler);
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return real.cookieHandler();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return real.connectTimeout();
        }

        @Override
        public Redirect followRedirects() {
            return real.followRedirects();
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return real.proxy();
        }

        @Override
        public SSLContext sslContext() {
            return real.sslContext();
        }

        @Override
        public SSLParameters sslParameters() {
            return real.sslParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return real.authenticator();
        }

        @Override
        public Version version() {
            return real.version();
        }

        @Override
        public Optional<Executor> executor() {
            return real.executor();
        }
    }
}
