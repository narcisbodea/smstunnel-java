package io.smstunnel.spring.boot;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The {@code smstunnel.*} settings.
 *
 * <pre>
 * smstunnel.api-key=${SMSTUNNEL_API_KEY}
 * smstunnel.default-country-code=+40
 * </pre>
 */
@ConfigurationProperties(prefix = "smstunnel")
public class SmsTunnelProperties {

    /** Whether to make the SmsTunnelClient bean. Set to false to turn SMSTunnel off, for example in tests. */
    private boolean enabled = true;

    /**
     * Your SMSTunnel API key (sk_live_...), from the dashboard: Apps, Add an app. Keep it out of source code: for
     * example smstunnel.api-key=${SMSTUNNEL_API_KEY}.
     */
    private String apiKey;

    /** The SMSTunnel address. Change it only for a self-hosted server. */
    private String baseUrl = "https://smstunnel.io";

    /**
     * The country code for numbers written without + or 00, such as +40, 40 or 0040. It takes precedence over the
     * phone's own setting. Empty: the phone's setting.
     */
    private String defaultCountryCode;

    /** How long one request to SMSTunnel may take. */
    private Duration timeout = Duration.ofSeconds(30);

    /**
     * How many times a failed request is tried again (0 to 5): reads, and sends with an Idempotency-Key, after a
     * connection problem, a timeout or a 502/503/504. Sends without a key of yours get one made by the client, so a
     * retry never sends a text twice. A 429 is never retried.
     */
    private int maxRetries = 2;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDefaultCountryCode() {
        return defaultCountryCode;
    }

    public void setDefaultCountryCode(String defaultCountryCode) {
        this.defaultCountryCode = defaultCountryCode;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    @Override
    public String toString() {
        return "SmsTunnelProperties{enabled=" + enabled + ", baseUrl=" + baseUrl + ", apiKey="
                + (apiKey == null || apiKey.isBlank() ? "(not set)" : "(hidden)") + "}";
    }
}
