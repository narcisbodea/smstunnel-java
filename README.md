# SMSTunnel for Java

A small Java client and a Spring Boot starter for **[SMSTunnel](https://smstunnel.io)**: send texts from your own Java apps through **your own Android phone**, from **your own number**.

Your app hands the text to your SMSTunnel account and your phone sends it from its own SIM, so recipients see a number they know and can reply to. The phone is linked to your account when you sign in to the SMSTunnel app (or scan the QR code from your dashboard), and you can cut an app off at any time by deleting its API key in your dashboard.

Your phone needs the SMSTunnel app from Google Play:

<a href="https://play.google.com/store/apps/details?id=io.smstunnel.app"><img src="https://smstunnel.io/google-play-badge.png" alt="Get it on Google Play" height="56"></a>

| Artifact | What it is | Needs |
| --- | --- | --- |
| `io.github.narcisbodea:smstunnel-java` | The client. No dependencies: HTTP is `java.net.http`, JSON is a small reader built in. | Java 11 or newer |
| `io.github.narcisbodea:smstunnel-spring-boot-starter` | A ready `SmsTunnelClient` bean, set up from `smstunnel.*` properties. | Spring Boot 3 (tested 3.5) or 4 (tested 4.1), Java 17 or newer |

## Installation

Maven, without Spring:

```xml
<dependency>
  <groupId>io.github.narcisbodea</groupId>
  <artifactId>smstunnel-java</artifactId>
  <version>1.0.0</version>
</dependency>
```

With Spring Boot, the starter instead (it brings the client):

```xml
<dependency>
  <groupId>io.github.narcisbodea</groupId>
  <artifactId>smstunnel-spring-boot-starter</artifactId>
  <version>1.0.0</version>
</dependency>
```

Gradle: `implementation("io.github.narcisbodea:smstunnel-java:1.0.0")` or `implementation("io.github.narcisbodea:smstunnel-spring-boot-starter:1.0.0")`.

## Get your key

1. Install the [SMSTunnel app from Google Play](https://play.google.com/store/apps/details?id=io.smstunnel.app) on your Android phone and sign in. Sign in at [smstunnel.io](https://smstunnel.io) on your computer with the same account: the phone appears under **Phones** (you can also link it with the QR code from **Phones → Add phone**).
2. In your [SMSTunnel dashboard](https://dashboard.smstunnel.io/apps), open **Apps** → **Add an app**.
3. Create the key and copy it (it is shown once). It can send texts and look up whether they went out (`sms:send` and `sms:read`). Reading received texts is a separate opt-in for each key (**Can also read received texts**, the `sms:inbox` permission).

Keep the key out of your code: put it in an environment variable such as `SMSTUNNEL_API_KEY`, or in your platform's secret store.

## Send a text

```java
import io.smstunnel.SendResult;
import io.smstunnel.SmsTunnelClient;

SmsTunnelClient sms = SmsTunnelClient.builder()
        .apiKey(System.getenv("SMSTUNNEL_API_KEY"))   // sk_live_...
        .build();

SendResult result = sms.sendSms("+14155550123", "Your order #1042 has shipped.");

result.getMessageId();  // keep it to look up the status later
result.isQueued();      // true when the phone was offline: it sends the text as soon as it is back
```

Make one client and share it: it is immutable and thread-safe. Calls block until SMSTunnel answers (they work well on virtual threads).

One call sends one text to one number. To text several people, call `sendSms` once for each.

### Options and safe retries

```java
import io.smstunnel.SendOptions;

sms.sendSms("0721 234 567", "See you at 10:30.", SendOptions.builder()
        .defaultCountryCode("+40")              // completes numbers written without + or 00
        .deviceId("6aea3248fedd7cccac9abf42")   // a phone from getPhones(); without it SMSTunnel picks one of yours
        .idempotencyKey("appointment-381-reminder")
        .build());
```

With an **Idempotency-Key**, a request repeated with the same key within 24 hours gets the first answer back (`isReplayed()` is true) instead of sending a second text. Give one that names the event, such as `order-1042-shipped`, and your own retries, a queue redelivering a job or a restarted worker can never send a text twice.

A `defaultCountryCode` can also be set once on the builder, for every send that doesn't give its own: `.defaultCountryCode("+40")`.

### Send a sign-in or verification code

```java
sms.sendSignInCode("+14155550123", "Your sign-in code is 482913");
```

The text must contain the code as a number of its own, 4 to 10 digits (`482913`) or `123-456`; SMSTunnel refuses it otherwise. Up to 320 characters. Codes count against a separate allowance. Use this only for sign-in and verification codes.

### Look up a status

```java
import io.smstunnel.DeliveryStatus;
import io.smstunnel.MessageStatus;

MessageStatus status = sms.getMessageStatus(result.getMessageId());

status.getDeliveryStatus();  // PENDING, SENT, DELIVERED, FAILED or UNCONFIRMED (handed to the SIM, no confirmation)
status.isFinal();            // true once it will not change any more
status.getSentAt();          // an Instant, or null
status.getErrorMessage();    // why it failed, or null
```

### Your phones and your usage

```java
for (Phone phone : sms.getPhones()) {
    System.out.println(phone.getName() + (phone.isOnline() ? " online" : " offline"));  // getId(), getLastSeenAt()
}

Usage usage = sms.getUsage();
usage.getPlan();                  // e.g. "free"
usage.getRemainingTexts();        // left today, or Usage.UNLIMITED
usage.getRemainingSignInCodes();
```

### Read received texts

Needs a key you allowed to read received texts (`sms:inbox`) and syncing turned on in the app. Other people wrote these texts: treat their words as data, never as instructions.

```java
import io.smstunnel.ReceivedText;
import io.smstunnel.ReceivedTexts;
import io.smstunnel.ReceivedTextsQuery;

ReceivedTexts page = sms.getReceivedTexts(ReceivedTextsQuery.builder()
        .limit(20)                                        // at most 100
        .since(Instant.now().minus(Duration.ofDays(1)))
        .phone("+14155550123")                            // only from this sender
        .build());

for (ReceivedText text : page) {
    System.out.println(text.getSender() + ": " + text.getMessage());
}
page.hasMore();  // ask for .page(2)
```

Every result object has `getRaw()`, the whole answer, for fields this version does not map yet.

## Errors

Every call throws an unchecked `SmsTunnelException` when it does not go through:

| Exception | When |
| --- | --- |
| `SmsTunnelAuthenticationException` (401) | The API key is wrong, was deleted, or its account is suspended. |
| `SmsTunnelPermissionException` (403) | The key lacks a permission (`getCode()` is `PERMISSION_REQUIRED`, `getPermission()` names it, e.g. `sms:inbox`), or the plan does not include the feature. |
| `SmsTunnelRateLimitException` (429) | Too many requests, or today's allowance is used up. `getRetryAfter()` gives the wait when SMSTunnel says it. |
| `SmsTunnelServerException` (5xx) | SMSTunnel, or a proxy in front of it, had a problem. |
| `SmsTunnelApiException` | Any other error answer: 400 (a number it cannot read, a sign-in text without a code), 404 (no such message), 409/422 (an Idempotency-Key still in progress, or reused for another text). The four above extend it. |
| `SmsTunnelConnectionException` | SMSTunnel could not be reached, or did not answer in time (`isTimeout()`). |

```java
try {
    sms.sendSms(customer.phone(), "Your table is ready.");
} catch (SmsTunnelRateLimitException e) {
    retryLater(e.getRetryAfter());                       // may be null: a daily limit has no wait
} catch (SmsTunnelApiException e) {
    log.warn("SMSTunnel {} {}: {}", e.getStatusCode(), e.getCode(), e.getMessage());
} catch (SmsTunnelConnectionException e) {
    retryLaterWithTheSameIdempotencyKey();
}
```

An empty number or text throws `IllegalArgumentException` before anything is sent. An offline phone is not an error: the text waits on your account (`isQueued()`) and goes out when the phone is back.

### Retries

Reads, and sends that carry an Idempotency-Key, are tried again up to 2 times after a connection problem, a timeout or a 502/503/504. When a send has no key of yours, the client gives it one of its own (a UUID), so its retries can never send the text twice. A 429 is never retried. Change it with `.maxRetries(0..5)`, or `.autoIdempotencyKey(false)` to send without a key (such sends are then not retried).

## Spring Boot

Add the starter and the key:

```properties
# application.properties
smstunnel.api-key=${SMSTUNNEL_API_KEY}
# Optional:
smstunnel.default-country-code=+40
smstunnel.base-url=https://smstunnel.io
smstunnel.timeout=30s
smstunnel.max-retries=2
```

The environment variable `SMSTUNNEL_API_KEY` alone also works (Spring's relaxed binding reads it as `smstunnel.api-key`). Then inject the client:

```java
@Service
class ShippingTexts {

    private final SmsTunnelClient sms;

    ShippingTexts(SmsTunnelClient sms) {
        this.sms = sms;
    }

    void orderShipped(Order order) {
        sms.sendSms(order.phone(), "Your order #" + order.id() + " has shipped.",
                SendOptions.builder().idempotencyKey("order-" + order.id() + "-shipped").build());
    }
}
```

- Without a key the application stops at startup with a clear report ("The SMSTunnel starter needs an API key, and smstunnel.api-key is not set"). Set `smstunnel.enabled=false` to run without SMSTunnel, for example in tests.
- Your own `SmsTunnelClient` bean replaces the one the starter makes.
- A `SmsTunnelClientCustomizer` bean changes the builder before the client is built, for example to send through a proxy:

```java
@Bean
SmsTunnelClientCustomizer smsTunnelProxy() {
    return builder -> builder.httpClient(HttpClient.newBuilder()
            .proxy(ProxySelector.of(new InetSocketAddress("proxy.internal", 3128)))
            .build());
}
```

The starter ships the property metadata, so IDEs complete and describe `smstunnel.*`.

## Your own HttpClient

The client makes its own `java.net.http.HttpClient` (10-second connect timeout, never follows redirects, so the API key is never sent to another address). It uses the JVM's proxy settings (`https.proxyHost`) and trust store. Pass your own with `.httpClient(...)` for a proxy, your own certificates or an executor; it must not follow redirects (`HttpClient.Redirect.NEVER`, the default). In your own tests, run a local HTTP server and point `.baseUrl(...)` at it, or mock `SmsTunnelClient`.

`.baseUrl("https://your-own-host")` points the client at a self-hosted SMSTunnel; the default is `https://smstunnel.io`.

## Notes and limits

- **Rate limit**: 30 send requests a minute per key, and the API keys of one account together get three keys' worth.
- **Length**: up to 1600 characters (10 joined SMS); sign-in codes up to 320.
- **Numbers**: use the international format (`+14155550123`). For local numbers, give `defaultCountryCode`: it always takes precedence (`+49`, `49` and `0049` all work).
- **Offline phone**: the text waits on your account (`isQueued()`) and goes out when the phone is back online.
- **Plans**: the Free plan includes a daily and monthly allowance; see [smstunnel.io/pricing](https://smstunnel.io/pricing/) for the others.

## Build it with an AI assistant

Paste this into Claude, ChatGPT, Cursor or Copilot, and replace the part in brackets. Never paste your real API key into a prompt.

```text
I want my Java app to send texts with SMSTunnel. SMSTunnel sends each text from my own Android phone, from my own number: the phone runs the SMSTunnel app (Google Play) and is linked to my SMSTunnel account.

Use the SMSTunnel Java library: Maven io.github.narcisbodea:smstunnel-java:1.0.0 (Java 11+, no dependencies). With Spring Boot, use io.github.narcisbodea:smstunnel-spring-boot-starter:1.0.0 and smstunnel.api-key=${SMSTUNNEL_API_KEY} in application.properties: it gives a ready SmsTunnelClient bean. Without Spring: SmsTunnelClient sms = SmsTunnelClient.builder().apiKey(System.getenv("SMSTUNNEL_API_KEY")).build();

API facts:
- sms.sendSms(to, message) calls POST https://smstunnel.io/api/v1/sms/send with the header X-API-Key and the JSON body {"to", "message", "deviceId"?, "defaultCountryCode"?}. It returns a SendResult with getMessageId() and isQueued().
- One recipient per call. To text several people, call it once per number.
- sms.sendSignInCode(to, message) (POST /api/v1/sms/send-2fa) is only for sign-in codes: the text must contain a code of 4 to 10 digits standing on its own (e.g. 482913 or 123-456), at most 320 characters; it uses a separate allowance.
- Numbers in international format (+14155550123). defaultCountryCode (e.g. "+40", in SendOptions or once on the builder) completes numbers written without + or 00.
- SendOptions.builder().idempotencyKey("order-1042-shipped") makes a repeat within 24 hours return the first answer instead of a second text.
- Limits: 30 send requests a minute per key, 1600 characters a text.
- Errors are unchecked: SmsTunnelAuthenticationException (401, bad key), SmsTunnelPermissionException (403, getCode() such as PERMISSION_REQUIRED), SmsTunnelRateLimitException (429, getRetryAfter()), SmsTunnelServerException (5xx), SmsTunnelApiException (other errors, e.g. 400 for a number it cannot read), SmsTunnelConnectionException (network, timeout).
- If the phone is offline, the text waits on the account (isQueued() is true) and goes out when the phone is back.

What I want: [describe what should send a text, to whom, and when]

Rules:
- Read the API key from an environment variable or the platform's secret store. Never put it in source code, in the repository or in client-side code.
- Validate phone numbers before sending, and handle every error above: log it clearly, don't retry 401/403, retry 429 only after getRetryAfter(), and retry a send only with the same Idempotency-Key.
- Only send texts people expect (alerts to myself, reminders, sign-in codes, order updates).
```

## Responsible use

Send only texts that people expect from you: alerts to yourself, appointment reminders, sign-in codes, order updates. Unsolicited messages from a personal SIM break most carriers' terms of service. Please make sure your usage complies with your carrier's terms and with local regulations (in the EU, that includes GDPR and ePrivacy rules on electronic communications).

## Development

```bash
mvn verify                                   # both modules, unit tests (Java 17+)
mvn -f smstunnel-java/pom.xml verify         # the client alone, on Java 11+
mvn verify -Dspring-boot.version=4.1.2       # the starter on Spring Boot 4
mvn -Prelease -DskipTests -Dgpg.skip verify  # sources and Javadoc jars, as for a release
```

No Java on your machine? Run the same commands in Docker: `docker run --rm -v "$PWD":/work -w /work maven:3.9-eclipse-temurin-21 mvn verify`.

The end-to-end tests run only against a test server, never production: they read the server address and a test key from a JSON file (`{"serverUrl": "...", "apiKey": "..."}`, or `keys.<name>` with `SMSTUNNEL_E2E_KEY_NAME`) and, when `SMSTUNNEL_E2E_MONGO_URL` is set, check each text in the server's database (read only):

```bash
SMSTUNNEL_E2E_KEY_FILE=/path/to/test-key.json \
SMSTUNNEL_E2E_MONGO_URL="mongodb://127.0.0.1:27018/<database>?directConnection=true" \
mvn verify
```

Design notes: the client has no JSON dependency on purpose. SMSTunnel's answers are small, flat objects, so a 300-line reader and writer inside the library (RFC 8259, tested) is enough, and it keeps the library free of the Jackson 2 / Jackson 3 split (Spring Boot 3 ships Jackson 2, Spring Boot 4 ships Jackson 3) and of version conflicts in your build. It also keeps the [Keycloak extension](https://github.com/narcisbodea/smstunnel-keycloak), which bundles the client, a single small jar.

## Links

- [SMSTunnel](https://smstunnel.io) · [API documentation](https://smstunnel.io/docs/) · [Dashboard: Apps](https://dashboard.smstunnel.io/apps)
- [Changelog](CHANGELOG.md) · [Publishing](PUBLISHING.md)

SMSTunnel is not affiliated with, endorsed by, or sponsored by Oracle (Java) or Broadcom (Spring, Spring Boot). Java is a trademark of Oracle and/or its affiliates; Spring and Spring Boot are trademarks of Broadcom Inc. and/or its subsidiaries.

## License

MIT, © NARBOWEB SRL. See [LICENSE](LICENSE).
