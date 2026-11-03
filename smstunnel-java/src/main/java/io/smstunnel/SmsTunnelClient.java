package io.smstunnel;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * The SMSTunnel client: sends texts through your own Android phone, from your own number, and reads their status,
 * your phones, your usage and the texts your phone received.
 *
 * <pre>{@code
 * SmsTunnelClient sms = SmsTunnelClient.builder()
 *     .apiKey(System.getenv("SMSTUNNEL_API_KEY"))
 *     .build();
 *
 * SendResult result = sms.sendSms("+14155550123", "Your order #1042 has shipped.");
 * }</pre>
 *
 * <p>One call sends one text to one number. Calls block until SMSTunnel answers (they work well on virtual threads).
 * The client is immutable and thread-safe: make one and share it.
 *
 * <p>Every call throws an unchecked {@link SmsTunnelException} when it does not go through:
 * {@link SmsTunnelAuthenticationException} (401), {@link SmsTunnelPermissionException} (403, with
 * {@link SmsTunnelApiException#getCode()}), {@link SmsTunnelRateLimitException} (429, with
 * {@link SmsTunnelApiException#getRetryAfter()}), {@link SmsTunnelServerException} (5xx), another
 * {@link SmsTunnelApiException}, or {@link SmsTunnelConnectionException} when SMSTunnel cannot be reached.
 *
 * <p>Retries: reads, and sends that carry an Idempotency-Key, are tried again up to {@code maxRetries} times (default
 * 2) after a connection problem, a timeout, or a 502/503/504. When a send has no key of yours, the client gives it one
 * of its own, so a retry can never send the text twice. A 429 is never retried.
 */
public final class SmsTunnelClient {

    /** {@code https://smstunnel.io}. */
    public static final String DEFAULT_BASE_URL = "https://smstunnel.io";

    /** This library's version. */
    public static final String VERSION = readVersion();

    private static final Pattern COUNTRY_CODE = Pattern.compile("^(\\+|00)?[1-9][0-9]{0,3}$");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[\\x20-\\x7E]{1,255}$");
    private static final Duration MAX_RETRY_WAIT = Duration.ofSeconds(10);
    private static final String IN_PROGRESS = "IDEMPOTENCY_KEY_IN_PROGRESS";

    private final String apiKey;
    private final String baseUrl;
    private final String defaultCountryCode;
    private final Duration timeout;
    private final int maxRetries;
    private final boolean autoIdempotencyKey;
    private final Duration retryBaseDelay;
    private final HttpClient http;
    private final String userAgent;
    private final boolean cleartext;

    private SmsTunnelClient(Builder b) {
        this.apiKey = b.apiKey;
        this.baseUrl = b.baseUrl;
        this.cleartext = b.baseUrl.regionMatches(true, 0, "http:", 0, 5);
        this.defaultCountryCode = b.defaultCountryCode;
        this.timeout = b.timeout;
        this.maxRetries = b.maxRetries;
        this.autoIdempotencyKey = b.autoIdempotencyKey;
        this.retryBaseDelay = b.retryBaseDelay;
        this.http = b.httpClient != null
                ? b.httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(b.connectTimeout)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
        this.userAgent = "smstunnel-java/" + VERSION + " Java/" + System.getProperty("java.version", "?")
                + (b.userAgentSuffix != null ? " " + b.userAgentSuffix : "");
    }

    public static Builder builder() {
        return new Builder();
    }

    // ─── Sending ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Sends a text to one number.
     *
     * @param to the recipient, in international format ({@code +14155550123}); a number written without {@code +} or
     *     {@code 00} is completed with the default country code
     * @param message the text, up to 1600 characters (10 joined SMS)
     * @return the text's id and whether it is waiting for an offline phone
     */
    public SendResult sendSms(String to, String message) {
        return sendSms(to, message, SendOptions.none());
    }

    /** Sends a text to one number, with a phone, a country code or an Idempotency-Key of your choice. */
    public SendResult sendSms(String to, String message, SendOptions options) {
        return send("/api/v1/sms/send", to, message, options);
    }

    /**
     * Sends a sign-in or verification code ({@code POST /api/v1/sms/send-2fa}). The text must contain the code as a
     * number of its own, 4 to 10 digits ({@code 482913}) or {@code 123-456}; SMSTunnel refuses it otherwise (400). Up
     * to 320 characters. Codes count against a separate allowance. Use it for sign-in codes only.
     */
    public SendResult sendSignInCode(String to, String message) {
        return sendSignInCode(to, message, SendOptions.none());
    }

    /** Sends a sign-in or verification code, with a phone, a country code or an Idempotency-Key of your choice. */
    public SendResult sendSignInCode(String to, String message, SendOptions options) {
        return send("/api/v1/sms/send-2fa", to, message, options);
    }

    private SendResult send(String path, String to, String message, SendOptions options) {
        requireText(to, "to (the recipient's number)");
        requireText(message, "message");
        SendOptions opts = options != null ? options : SendOptions.none();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("to", to.trim());
        body.put("message", message);
        if (opts.getDeviceId() != null) {
            body.put("deviceId", opts.getDeviceId());
        }
        String countryCode = opts.getDefaultCountryCode() != null ? opts.getDefaultCountryCode() : defaultCountryCode;
        if (countryCode != null) {
            body.put("defaultCountryCode", countryCode);
        }
        String key = opts.getIdempotencyKey();
        if (key == null && autoIdempotencyKey && maxRetries > 0) {
            key = UUID.randomUUID().toString();
        }
        Answer answer = execute("POST", path, body, key);
        return SendResult.from(answer.body, answer.replayed);
    }

    // ─── Reading ─────────────────────────────────────────────────────────────────────────────────

    /** The status of a text you sent (the key needs {@code sms:read}, which new keys have). 404 when unknown. */
    public MessageStatus getMessageStatus(String messageId) {
        requireText(messageId, "messageId");
        return MessageStatus.from(execute("GET", "/api/v1/sms/status/" + encode(messageId.trim()), null, null).body);
    }

    /** The phones linked to the key's account, and whether each is online. */
    public List<Phone> getPhones() {
        Map<String, Object> data = Fields.object(execute("GET", "/api/v1/devices", null, null).body, "data");
        List<Phone> phones = new ArrayList<>();
        for (Object item : Fields.list(data, "devices")) {
            if (item instanceof Map) {
                phones.add(Phone.from(Fields.object(item)));
            }
        }
        return Collections.unmodifiableList(phones);
    }

    /** Today's usage and what is left of the allowance. */
    public Usage getUsage() {
        return Usage.from(execute("GET", "/api/v1/account/usage", null, null).body);
    }

    /** The newest texts your phone received (up to 50). See {@link #getReceivedTexts(ReceivedTextsQuery)}. */
    public ReceivedTexts getReceivedTexts() {
        return getReceivedTexts(ReceivedTextsQuery.latest());
    }

    /**
     * Texts your phone received. Needs a key you allowed to read received texts ({@code sms:inbox}, "Can also read
     * received texts" in the dashboard); other keys get {@link SmsTunnelPermissionException} with code
     * {@code PERMISSION_REQUIRED}. Syncing received texts must also be on in the app.
     */
    public ReceivedTexts getReceivedTexts(ReceivedTextsQuery query) {
        ReceivedTextsQuery q = query != null ? query : ReceivedTextsQuery.latest();
        StringBuilder path = new StringBuilder("/api/v1/sms/received");
        char sep = '?';
        if (q.getLimit() != null) {
            path.append(sep).append("limit=").append(q.getLimit());
            sep = '&';
        }
        if (q.getPage() != null) {
            path.append(sep).append("page=").append(q.getPage());
            sep = '&';
        }
        if (q.getSince() != null) {
            path.append(sep).append("since=").append(encode(DateTimeFormatter.ISO_INSTANT.format(q.getSince())));
            sep = '&';
        }
        if (q.getPhone() != null) {
            path.append(sep).append("phone=").append(encode(q.getPhone()));
            sep = '&';
        }
        if (q.getDeviceId() != null) {
            path.append(sep).append("deviceId=").append(encode(q.getDeviceId()));
        }
        return ReceivedTexts.from(execute("GET", path.toString(), null, null).body);
    }

    /** The address this client sends to. */
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String toString() {
        return "SmsTunnelClient{baseUrl=" + baseUrl + ", apiKey=(hidden)}";
    }

    // ─── HTTP ────────────────────────────────────────────────────────────────────────────────────

    /** A 2xx JSON answer. */
    private static final class Answer {
        final Map<String, Object> body;
        final boolean replayed;

        Answer(Map<String, Object> body, boolean replayed) {
            this.body = body;
            this.replayed = replayed;
        }
    }

    private Answer execute(String method, String path, Map<String, Object> body, String idempotencyKey) {
        // A send is retried only with an Idempotency-Key: without one, a retry could send the text twice.
        boolean retryable = "GET".equals(method) || idempotencyKey != null;
        for (int attempt = 0; ; attempt++) {
            try {
                return once(method, path, body, idempotencyKey);
            } catch (SmsTunnelException e) {
                Duration wait = retryable && attempt < maxRetries ? retryWait(e, method, attempt) : null;
                if (wait == null) {
                    throw e;
                }
                try {
                    Thread.sleep(wait.toMillis());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    e.addSuppressed(interrupted);
                    throw e;
                }
            }
        }
    }

    /** How long to wait before the next attempt, or null when this error must not be retried. */
    private Duration retryWait(SmsTunnelException e, String method, int attempt) {
        long base = retryBaseDelay.toMillis() << Math.min(attempt, 6);
        Duration backoff = Duration.ofMillis(Math.round(base * ThreadLocalRandom.current().nextDouble(0.8, 1.2)));
        if (e instanceof SmsTunnelConnectionException) {
            return e.getCause() instanceof InterruptedException ? null : backoff;
        }
        if (!(e instanceof SmsTunnelApiException)) {
            return null;
        }
        SmsTunnelApiException api = (SmsTunnelApiException) e;
        int status = api.getStatusCode();
        boolean transientError = status == 502 || status == 503 || status == 504
                || (status == 500 && "GET".equals(method))
                || (status == 409 && IN_PROGRESS.equals(api.getCode()));
        if (!transientError) {
            return null;
        }
        Duration asked = api.getRetryAfter();
        if (asked == null) {
            return backoff;
        }
        return asked.compareTo(MAX_RETRY_WAIT) > 0 ? null : asked;
    }

    private Answer once(String method, String path, Map<String, Object> body, String idempotencyKey) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("X-API-Key", apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", userAgent);
        if (cleartext) {
            // Over plain http the JDK would ask to upgrade to HTTP/2 (h2c); servers that also serve WebSockets on the
            // same port can drop such requests. https negotiates the version in TLS instead.
            request.version(HttpClient.Version.HTTP_1_1);
        }
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            request.header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8));
        } else {
            request.GET();
        }

        HttpResponse<String> response;
        try {
            response = http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new SmsTunnelConnectionException(
                    "SMSTunnel did not answer within " + timeout.toMillis() + " ms (" + method + " " + pathOnly(path) + ")",
                    e, true);
        } catch (IOException e) {
            throw new SmsTunnelConnectionException(
                    "Could not reach SMSTunnel at " + baseUrl + ": " + describe(e), e, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SmsTunnelConnectionException("Interrupted while waiting for SMSTunnel", e, false);
        }

        int status = response.statusCode();
        Object decoded = decode(response.body());
        HttpHeaders headers = response.headers();
        if (status >= 200 && status < 300) {
            if (!(decoded instanceof Map)) {
                throw new SmsTunnelApiException(status, "SMSTunnel answered with something that is not JSON.", decoded, null);
            }
            Map<String, Object> json = Fields.object(decoded);
            if (Boolean.FALSE.equals(json.get("success"))) {
                String error = Fields.string(json, "error");
                throw new SmsTunnelApiException(status,
                        error != null && !error.isEmpty() ? error : "SMSTunnel did not accept the request.", json, null);
            }
            boolean replayed = "true".equalsIgnoreCase(headers.firstValue("Idempotent-Replayed").orElse(""));
            return new Answer(json, replayed);
        }
        if (status >= 300 && status < 400) {
            String location = headers.firstValue("Location").orElse("another address");
            throw new SmsTunnelApiException(status, "SMSTunnel answered with a redirect to " + location
                    + ". Check the base URL (" + baseUrl + "): the client does not follow redirects, so your API key"
                    + " is never sent anywhere else.", decoded, null);
        }
        throw SmsTunnelApiException.of(status, decoded, retryAfterOf(headers));
    }

    /** JSON when it is JSON, the text otherwise (a proxy's error page), or null for an empty body. */
    private static Object decode(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return Json.parse(text);
        } catch (IllegalArgumentException notJson) {
            return text;
        }
    }

    /** {@code Retry-After} as seconds or as an HTTP date. */
    static Duration retryAfterOf(HttpHeaders headers) {
        String value = headers.firstValue("Retry-After").orElse("").trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Duration.ofSeconds(Math.max(0, Long.parseLong(value)));
        } catch (NumberFormatException notSeconds) {
            try {
                ZonedDateTime when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME);
                Duration left = Duration.between(Instant.now(), when.toInstant());
                return left.isNegative() ? Duration.ZERO : left;
            } catch (DateTimeParseException notDate) {
                return null;
            }
        }
    }

    private static String describe(IOException e) {
        String message = e.getMessage();
        String type = e.getClass().getSimpleName();
        return message == null || message.isEmpty() ? type : type + ": " + message;
    }

    private static String pathOnly(String path) {
        int query = path.indexOf('?');
        return query < 0 ? path : path.substring(0, query);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    static String checkCountryCode(String value) {
        if (value != null && !COUNTRY_CODE.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "defaultCountryCode must be a country calling code such as \"+40\", \"40\" or \"0040\"");
        }
        return value;
    }

    static String checkIdempotencyKey(String value) {
        if (value == null) {
            return null;
        }
        if (!IDEMPOTENCY_KEY.matcher(value).matches() || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "An Idempotency-Key must be 1 to 255 printable ASCII characters (a UUID is a good choice)");
        }
        return value;
    }

    private static String readVersion() {
        try (InputStream in = SmsTunnelClient.class.getResourceAsStream("version.properties")) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("version", "").trim();
                if (!version.isEmpty() && !version.startsWith("$")) {
                    return version;
                }
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "dev";
    }

    // ─── Builder ─────────────────────────────────────────────────────────────────────────────────

    /** Builds a {@link SmsTunnelClient}. Only the API key is required. */
    public static final class Builder {
        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private String defaultCountryCode;
        private Duration timeout = Duration.ofSeconds(30);
        private Duration connectTimeout = Duration.ofSeconds(10);
        private int maxRetries = 2;
        private boolean autoIdempotencyKey = true;
        private Duration retryBaseDelay = Duration.ofMillis(500);
        private HttpClient httpClient;
        private String userAgentSuffix;

        private Builder() {
        }

        /**
         * Your API key ({@code sk_live_...}), from the dashboard: Apps, Add an app. Read it from an environment
         * variable or a secret store, never from source code.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** The SMSTunnel address, {@code https://smstunnel.io} by default. Change it only for a self-hosted server. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * The country code for numbers written without {@code +} or {@code 00}, such as {@code "+40"}, for every send
         * that does not give its own. Without it, SMSTunnel uses the phone's own setting.
         */
        public Builder defaultCountryCode(String defaultCountryCode) {
            this.defaultCountryCode = SendOptions.blankToNull(defaultCountryCode);
            return this;
        }

        /** How long one request may take, 30 seconds by default. */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /** How long connecting may take, 10 seconds by default. Not used with your own {@link #httpClient}. */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
            return this;
        }

        /**
         * How many times a failed request is tried again (0 to 5, default 2): reads, and sends with an
         * Idempotency-Key, after a connection problem, a timeout or a 502/503/504. 0 turns retries off.
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0 || maxRetries > 5) {
                throw new IllegalArgumentException("maxRetries must be between 0 and 5");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Whether a send without an Idempotency-Key of yours gets one made by the client (default true), so the
         * client's own retries can never send the text twice. With false, such sends are not retried.
         */
        public Builder autoIdempotencyKey(boolean autoIdempotencyKey) {
            this.autoIdempotencyKey = autoIdempotencyKey;
            return this;
        }

        /** The first wait between retries; it doubles each time. For tests. */
        Builder retryBaseDelay(Duration retryBaseDelay) {
            this.retryBaseDelay = Objects.requireNonNull(retryBaseDelay, "retryBaseDelay");
            return this;
        }

        /**
         * Your own {@link HttpClient}, for a proxy, your own certificates or an executor. It must not follow redirects
         * ({@code HttpClient.Redirect.NEVER}, the default), so the API key is never sent to another address.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /** Added to the User-Agent header, such as {@code my-app/2.1}. */
        public Builder userAgentSuffix(String userAgentSuffix) {
            this.userAgentSuffix = SendOptions.blankToNull(userAgentSuffix);
            return this;
        }

        public SmsTunnelClient build() {
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "An SMSTunnel API key is required: create one in the dashboard (Apps, Add an app) and pass it"
                                + " to apiKey(), for example from the SMSTUNNEL_API_KEY environment variable");
            }
            apiKey = apiKey.trim();
            if (apiKey.chars().anyMatch(c -> c < 0x21 || c > 0x7E)) {
                throw new IllegalArgumentException("The API key contains spaces or characters that are not ASCII");
            }
            baseUrl = checkBaseUrl(baseUrl);
            checkCountryCode(defaultCountryCode);
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            if (connectTimeout.isNegative() || connectTimeout.isZero()) {
                throw new IllegalArgumentException("connectTimeout must be positive");
            }
            if (httpClient != null && httpClient.followRedirects() != HttpClient.Redirect.NEVER) {
                throw new IllegalArgumentException(
                        "The HttpClient must not follow redirects (HttpClient.Redirect.NEVER), so the API key is never"
                                + " sent to another address");
            }
            return new SmsTunnelClient(this);
        }

        private static String checkBaseUrl(String value) {
            String url = value == null || value.trim().isEmpty() ? DEFAULT_BASE_URL : value.trim();
            URI uri;
            try {
                uri = URI.create(url);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("baseUrl is not a URL: " + url, e);
            }
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("baseUrl must be an http(s) address such as https://smstunnel.io");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("baseUrl must not have a query or a fragment");
            }
            while (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
            return url;
        }
    }
}
