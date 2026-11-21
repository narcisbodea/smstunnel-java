# Changelog

## 1.1.1 (2026-11-21)

`isFinal()` now counts a text the phone sent as final.

### smstunnel-java

- `MessageStatus.isFinal()` is now `true` for `SENT`. The SMSTunnel app gets no delivery reports, so a text that went out stays `SENT`, and code waiting "until final" never stopped. `SENT` is the last good status today; `DELIVERED` is reserved and still counts as final. `PENDING` and `UNKNOWN` are still not final.
- Only `isFinal()` changes. If your code waits for `isFinal()` and then looks for `DELIVERED`, treat `SENT` as success too.
- The docs no longer promise a delivery confirmation.

### smstunnel-spring-boot-starter

- 1.1.1, with the client 1.1.1. Nothing else changed.

## 1.1.0 (2026-11-15)

Scheduled texts: SMSTunnel keeps a text and sends it at the time you give, 1 minute to 30 days ahead.

### smstunnel-java

- `scheduleSms(to, message, sendAt)` and `scheduleSms(to, message, sendAt, options)`, with `sendAt` an `Instant`, an `OffsetDateTime` or a `ZonedDateTime` (a time without a zone does not compile). It sends `sendAt` to `POST /api/v1/sms/send` in whole seconds with its offset, and returns a `ScheduledSendResult`: `getScheduledId()`, `getSendAt()`, `getStatus()`, `getScheduledText()`, `isReplayed()`, `getRaw()`. Retries and the Idempotency-Key work as for `sendSms`. `sendSms` and `sendSignInCode` are unchanged.
- `getScheduledTexts()` and `getScheduledTexts(ScheduledTextsQuery)` (`ScheduledTextScope.WAITING` or `DONE`, page, limit) return `ScheduledTexts` (with `getWaiting()` and the plan's `getMax()`); `getScheduledText(id)` and `cancelScheduledText(id)` return a `ScheduledText` (`ScheduledTextStatus` `SCHEDULED`, `SENT`, `FAILED`, `CANCELED`; `getMessageId()` once sent, `getError()` once failed). A cancel is tried again only when the connection could not be made, since a second cancel of a canceled text answers 409.
- `SmsTunnelApiException`: the `SCHEDULE_*` code constants, and `getMax()` for `SCHEDULE_LIMIT_REACHED` (403, `SmsTunnelPermissionException`).
- A server that sends the text at once instead of scheduling it makes `scheduleSms` throw `SmsTunnelApiException`, with the message id in its message; one from before scheduled texts answers 400 "property sendAt should not exist".

### smstunnel-spring-boot-starter

- 1.1.0, with the client 1.1.0. The `SmsTunnelClient` bean has the new methods; nothing else changed.

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
