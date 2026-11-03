package io.smstunnel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Against a real SMSTunnel test server with a simulated phone (see {@link E2eSettings}). The recipients are
 * +1 415 555 73xx test numbers.
 */
@EnabledIfEnvironmentVariable(named = "SMSTUNNEL_E2E_KEY_FILE", matches = ".+")
class SmsTunnelClientE2ETest {

    private static final String TO = "+14155557301";

    private static E2eSettings settings;
    private static SmsTunnelClient client;
    private static MongoClient mongo;
    private static MongoCollection<Document> messages;

    @BeforeAll
    static void connect() throws Exception {
        settings = E2eSettings.load();
        client = settings.client();
        if (settings.mongoUrl != null) {
            mongo = MongoClients.create(settings.mongoUrl);
            String database = new ConnectionString(settings.mongoUrl).getDatabase();
            messages = mongo.getDatabase(database).getCollection("messages");
        }
    }

    @AfterAll
    static void close() {
        if (mongo != null) {
            mongo.close();
        }
    }

    @Test
    void listsTheTestPhoneAsOnline() {
        List<Phone> phones = client.getPhones();

        assertFalse(phones.isEmpty(), "the test account has a phone");
        assertTrue(phones.stream().anyMatch(Phone::isOnline), "the simulated phone is online: " + phones);
    }

    @Test
    void sendsATextThatThePhoneSends() throws Exception {
        String text = "smstunnel-java e2e " + UUID.randomUUID() + ": comanda ta a plecat, ăîșț ÂÎȘȚ";

        SendResult result = client.sendSms(TO, text);

        assertNotNull(result.getMessageId());
        MessageStatus status = waitUntilSent(result.getMessageId());
        assertEquals(TO, status.getPhoneNumber());
        assertStored(result.getMessageId(), TO, text);
    }

    @Test
    void theSameIdempotencyKeySendsOnlyOnce() {
        String text = "smstunnel-java e2e idempotency " + UUID.randomUUID();
        SendOptions options = SendOptions.builder().idempotencyKey("smstunnel-java-e2e-" + UUID.randomUUID()).build();

        SendResult first = client.sendSms(TO, text, options);
        SendResult second = client.sendSms(TO, text, options);

        assertEquals(first.getMessageId(), second.getMessageId());
        assertFalse(first.isReplayed());
        assertTrue(second.isReplayed(), "the second answer is the stored first one");
        if (messages != null) {
            assertEquals(1, messages.countDocuments(Filters.eq("message", text)), "one text in the database");
        }
    }

    @Test
    void sendsASignInCode() throws Exception {
        String text = "Your smstunnel-java test code is 482913";

        SendResult result = client.sendSignInCode(TO, text);

        waitUntilSent(result.getMessageId());
        assertStored(result.getMessageId(), TO, text);
    }

    @Test
    void refusesASignInTextWithoutACode() {
        SmsTunnelApiException e = assertThrows(SmsTunnelApiException.class,
                () -> client.sendSignInCode(TO, "Your code is ABCDEF"));

        assertEquals(400, e.getStatusCode());
        assertTrue(e.getMessage().contains("No valid verification code found"), e.getMessage());
    }

    @Test
    void completesALocalNumberWithTheDefaultCountryCode() throws Exception {
        String text = "smstunnel-java e2e country code " + UUID.randomUUID();

        SendResult result = client.sendSms("415 555 7302", text, SendOptions.builder().defaultCountryCode("+1").build());

        MessageStatus status = waitUntilSent(result.getMessageId());
        assertEquals("+14155557302", status.getPhoneNumber());
        assertStored(result.getMessageId(), "+14155557302", text);
    }

    @Test
    void readsTheUsage() {
        Usage usage = client.getUsage();

        assertNotNull(usage.getPlan());
        assertTrue(usage.getRemainingTexts() > 0);
    }

    @Test
    void readsReceivedTexts() {
        ReceivedTexts page = client.getReceivedTexts(ReceivedTextsQuery.builder()
                .limit(5).since(Instant.now().minus(Duration.ofDays(7))).build());

        assertTrue(page.getTotal() >= page.getTexts().size());
        assertEquals(1, page.getPage());
    }

    @Test
    void aWrongKeyIsRefused() {
        // Built from pieces: written in one piece it looks like a real key to secret scanners.
        SmsTunnelClient wrong = SmsTunnelClient.builder().apiKey("sk_" + "live_" + "not_a_real_key_000000000000000")
                .baseUrl(settings.serverUrl).build();

        SmsTunnelAuthenticationException e = assertThrows(SmsTunnelAuthenticationException.class, wrong::getPhones);

        assertEquals(401, e.getStatusCode());
    }

    private static MessageStatus waitUntilSent(String messageId) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        MessageStatus status;
        do {
            status = client.getMessageStatus(messageId);
            if (status.getDeliveryStatus() == DeliveryStatus.SENT || status.isFinal()) {
                break;
            }
            Thread.sleep(500);
        } while (System.nanoTime() < deadline);
        assertTrue(status.getDeliveryStatus() == DeliveryStatus.SENT
                || status.getDeliveryStatus() == DeliveryStatus.DELIVERED, "the phone sent it: " + status);
        return status;
    }

    private static void assertStored(String messageId, String to, String text) {
        if (messages == null) {
            return;
        }
        Document stored = messages.find(Filters.eq("_id", new ObjectId(messageId))).first();
        assertNotNull(stored, "the server stored the text");
        assertEquals(to, stored.getString("phoneNumber"));
        assertEquals(text, stored.getString("message"));
        assertEquals("outgoing", stored.getString("type"));
    }
}
