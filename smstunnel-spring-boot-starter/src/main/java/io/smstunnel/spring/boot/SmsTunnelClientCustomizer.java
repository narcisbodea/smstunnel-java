package io.smstunnel.spring.boot;

import io.smstunnel.SmsTunnelClient;

/**
 * Changes the {@link SmsTunnelClient.Builder} before the auto-configured client is built, for settings the
 * {@code smstunnel.*} properties don't cover, such as your own {@link java.net.http.HttpClient} with a proxy.
 *
 * <pre>{@code
 * @Bean
 * SmsTunnelClientCustomizer smsTunnelProxy() {
 *     return builder -> builder.httpClient(HttpClient.newBuilder()
 *             .proxy(ProxySelector.of(new InetSocketAddress("proxy.internal", 3128)))
 *             .build());
 * }
 * }</pre>
 */
@FunctionalInterface
public interface SmsTunnelClientCustomizer {

    void customize(SmsTunnelClient.Builder builder);
}
