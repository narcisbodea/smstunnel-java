package io.smstunnel.spring.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.smstunnel.SmsTunnelClient;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class SmsTunnelAutoConfigurationTest {

    /** A made-up key, built from pieces: written in one piece it looks like a real key to secret scanners. */
    private static final String KEY = "sk_" + "live_" + "starter_test_key";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SmsTunnelAutoConfiguration.class));

    private LocalApi api;

    @BeforeEach
    void start() throws Exception {
        api = new LocalApi();
    }

    @AfterEach
    void stop() {
        api.close();
    }

    @Test
    void makesAClientFromTheProperties() {
        runner.withPropertyValues("smstunnel.api-key=" + KEY, "smstunnel.base-url=" + api.url() + "/",
                "smstunnel.default-country-code=+40", "smstunnel.timeout=5s", "smstunnel.max-retries=0")
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsTunnelClient.class);
                    SmsTunnelClient client = context.getBean(SmsTunnelClient.class);
                    assertThat(client.getBaseUrl()).isEqualTo(api.url());

                    client.sendSms("0721 123 456", "Salut");

                    assertThat(api.lastHeaders.get("x-api-key")).isEqualTo(KEY);
                    assertThat(api.lastHeaders.get("user-agent")).contains("smstunnel-spring-boot-starter/");
                    assertThat(api.lastHeaders).doesNotContainKey("idempotency-key");
                    assertThat(api.lastBody).contains("\"defaultCountryCode\":\"+40\"");
                });
    }

    @Test
    void sendsToSmsTunnelByDefault() {
        runner.withPropertyValues("smstunnel.api-key=" + KEY)
                .run(context -> assertThat(context.getBean(SmsTunnelClient.class).getBaseUrl())
                        .isEqualTo("https://smstunnel.io"));
    }

    @Test
    void readsTheKeyFromTheSmsTunnelApiKeyEnvironmentVariable() {
        runner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                Map.of("SMSTUNNEL_API_KEY", KEY, "SMSTUNNEL_BASE_URL", api.url()))))
                .run(context -> {
                    context.getBean(SmsTunnelClient.class).sendSms("+14155550123", "Hi");
                    assertThat(api.lastHeaders.get("x-api-key")).isEqualTo(KEY);
                });
    }

    @Test
    void failsAtStartupWithAClearReportWhenTheKeyIsMissing() {
        runner.run(context -> {
            assertThat(context).hasFailed();
            Throwable failure = context.getStartupFailure();
            assertThat(failure).hasRootCauseInstanceOf(SmsTunnelApiKeyMissingException.class);

            FailureAnalysis analysis = new SmsTunnelApiKeyMissingFailureAnalyzer().analyze(failure);
            assertThat(analysis).isNotNull();
            assertThat(analysis.getDescription()).contains("smstunnel.api-key is not set");
            assertThat(analysis.getAction()).contains("smstunnel.api-key=${SMSTUNNEL_API_KEY}")
                    .contains("smstunnel.enabled=false");
        });
    }

    @Test
    void theFailureAnalyzerIsRegistered() throws Exception {
        String factories = new String(getClass().getClassLoader().getResourceAsStream("META-INF/spring.factories")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(factories).contains(SmsTunnelApiKeyMissingFailureAnalyzer.class.getName());
        assertThat(new SmsTunnelApiKeyMissingFailureAnalyzer()
                .analyze(new BeanCreationException("smsTunnelClient", new SmsTunnelApiKeyMissingException())))
                .isNotNull();
    }

    @Test
    void canBeTurnedOff() {
        runner.withPropertyValues("smstunnel.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SmsTunnelClient.class);
                });
    }

    @Test
    void backsOffWhenTheApplicationHasItsOwnClient() {
        runner.withUserConfiguration(OwnClient.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(SmsTunnelClient.class);
                    assertThat(context.getBean(SmsTunnelClient.class).getBaseUrl()).isEqualTo("https://own.example");
                });
    }

    @Test
    void appliesCustomizers() {
        runner.withPropertyValues("smstunnel.api-key=" + KEY, "smstunnel.base-url=" + api.url())
                .withUserConfiguration(Customized.class)
                .run(context -> {
                    context.getBean(SmsTunnelClient.class).sendSms("07911 123456", "Hello");
                    assertThat(api.lastBody).contains("\"defaultCountryCode\":\"+44\"");
                    assertThat(api.lastHeaders.get("user-agent")).endsWith("acme-shop/2.1");
                });
    }

    @Test
    void refusesAWrongCountryCodeAtStartup() {
        runner.withPropertyValues("smstunnel.api-key=" + KEY, "smstunnel.default-country-code=Romania")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .rootCause().hasMessageContaining("defaultCountryCode"));
    }

    @Test
    void neverShowsTheKeyInThePropertiesToString() {
        SmsTunnelProperties properties = new SmsTunnelProperties();
        properties.setApiKey(KEY);
        assertThat(properties.toString()).doesNotContain(KEY);
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnClient {
        @Bean
        SmsTunnelClient ownClient() {
            return SmsTunnelClient.builder().apiKey(KEY).baseUrl("https://own.example").build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Customized {
        @Bean
        SmsTunnelClientCustomizer ukNumbers() {
            return builder -> builder.defaultCountryCode("+44").userAgentSuffix("acme-shop/2.1");
        }
    }
}
