package io.smstunnel.spring.boot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;

import io.smstunnel.DeliveryStatus;
import io.smstunnel.MessageStatus;
import io.smstunnel.Phone;
import io.smstunnel.SendResult;
import io.smstunnel.SmsTunnelClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Starts the demo app with the starter against the SMSTunnel test server and sends a real text through the simulated
 * phone. Needs SMSTUNNEL_E2E_KEY_FILE (and SMSTUNNEL_E2E_KEY_NAME / SMSTUNNEL_E2E_SERVER_URL); never production.
 */
@SpringBootTest(classes = DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "SMSTUNNEL_E2E_KEY_FILE", matches = ".+")
class DemoApplicationE2ETest {

    @DynamicPropertySource
    static void smstunnel(DynamicPropertyRegistry registry) throws IOException {
        File file = new File(System.getenv("SMSTUNNEL_E2E_KEY_FILE"));
        String keyName = System.getenv("SMSTUNNEL_E2E_KEY_NAME");
        String apiKey = keyName != null && !keyName.isEmpty()
                ? JsonPath.parse(file).read("$.keys['" + keyName + "']", String.class)
                : JsonPath.parse(file).read("$.apiKey", String.class);
        String serverUrl = System.getenv("SMSTUNNEL_E2E_SERVER_URL");
        String baseUrl = serverUrl != null && !serverUrl.isEmpty()
                ? serverUrl
                : JsonPath.parse(file).read("$.serverUrl", String.class);
        String host = URI.create(baseUrl).getHost();
        if (host.equals("smstunnel.io") || host.endsWith(".smstunnel.io")) {
            throw new IllegalStateException("The end-to-end tests never run against production");
        }
        registry.add("smstunnel.api-key", () -> apiKey);
        registry.add("smstunnel.base-url", () -> baseUrl);
        registry.add("smstunnel.default-country-code", () -> "+1");
    }

    @Autowired
    DemoApplication.ShippingTexts shippingTexts;

    @Autowired
    SmsTunnelClient sms;

    @Test
    void theAppSendsAShippingTextThroughThePhone() throws Exception {
        List<Phone> phones = sms.getPhones();
        assertThat(phones).anyMatch(Phone::isOnline);

        String orderId = "e2e-" + UUID.randomUUID();
        // A local number: smstunnel.default-country-code=+1 completes it.
        SendResult result = shippingTexts.orderShipped(orderId, "415 555 7311");
        SendResult again = shippingTexts.orderShipped(orderId, "415 555 7311");

        assertThat(result.getMessageId()).isNotBlank();
        assertThat(again.getMessageId()).as("the same order key sends one text").isEqualTo(result.getMessageId());
        assertThat(again.isReplayed()).isTrue();

        MessageStatus status = waitUntilFinalOrSent(result.getMessageId());
        assertThat(status.getPhoneNumber()).isEqualTo("+14155557311");
        assertThat(status.getDeliveryStatus()).isIn(DeliveryStatus.SENT, DeliveryStatus.DELIVERED);
    }

    private MessageStatus waitUntilFinalOrSent(String messageId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        MessageStatus status = sms.getMessageStatus(messageId);
        while (status.getDeliveryStatus() == DeliveryStatus.PENDING && System.nanoTime() < deadline) {
            Thread.sleep(500);
            status = sms.getMessageStatus(messageId);
        }
        return status;
    }
}
