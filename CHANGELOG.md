# Changelog

## 1.0.0 (2026-11-03)

First release.

### smstunnel-java

- `SmsTunnelClient`: `sendSms`, `sendSignInCode` (`/api/v1/sms/send-2fa`), `getMessageStatus`, `getPhones`, `getUsage` and `getReceivedTexts`, for Java 11 and newer, with no dependencies.
- `SendOptions`: the phone (`deviceId`), `defaultCountryCode` and an `Idempotency-Key` per text.
- Retries of reads, and of sends that carry an Idempotency-Key (the client gives one to sends without a key of yours), after a connection problem, a timeout or a 502/503/504; never after a 429.
- Unchecked exceptions per answer: `SmsTunnelAuthenticationException` (401), `SmsTunnelPermissionException` (403, with `getCode()` and `getPermission()`), `SmsTunnelRateLimitException` (429, with `getRetryAfter()`), `SmsTunnelServerException` (5xx), `SmsTunnelApiException` and `SmsTunnelConnectionException`.
- Never follows redirects, so the API key is never sent to another address.

### smstunnel-spring-boot-starter

- A ready `SmsTunnelClient` bean from `smstunnel.api-key`, `smstunnel.base-url`, `smstunnel.default-country-code`, `smstunnel.timeout`, `smstunnel.max-retries` and `smstunnel.enabled`, with IDE metadata.
- `SmsTunnelClientCustomizer` beans, and a clear startup report when the key is missing.
- Spring Boot 3 (tested 3.5.16) and 4 (tested 4.1.2).
