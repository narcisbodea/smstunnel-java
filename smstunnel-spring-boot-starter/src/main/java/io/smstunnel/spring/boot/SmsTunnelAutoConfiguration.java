package io.smstunnel.spring.boot;

import io.smstunnel.SmsTunnelClient;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * Makes a {@link SmsTunnelClient} bean from the {@code smstunnel.*} properties, unless the application defines its
 * own. {@code smstunnel.enabled=false} turns it off.
 */
@AutoConfiguration
@ConditionalOnClass(SmsTunnelClient.class)
@ConditionalOnProperty(prefix = "smstunnel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(SmsTunnelProperties.class)
public class SmsTunnelAutoConfiguration {

    static final String VERSION = versionOf(SmsTunnelAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public SmsTunnelClient smsTunnelClient(SmsTunnelProperties properties,
            ObjectProvider<SmsTunnelClientCustomizer> customizers) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new SmsTunnelApiKeyMissingException();
        }
        SmsTunnelClient.Builder builder = SmsTunnelClient.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .defaultCountryCode(properties.getDefaultCountryCode())
                .maxRetries(properties.getMaxRetries())
                .userAgentSuffix("smstunnel-spring-boot-starter/" + VERSION);
        if (properties.getTimeout() != null) {
            builder.timeout(properties.getTimeout());
        }
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder.build();
    }

    private static String versionOf(Class<?> type) {
        String version = type.getPackage() != null ? type.getPackage().getImplementationVersion() : null;
        return version != null ? version : SmsTunnelClient.VERSION;
    }
}
