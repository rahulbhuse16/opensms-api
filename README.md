# OpenSMS

## Project purpose
OpenSMS is an independent SMS communications platform intended to let businesses send and manage SMS through a provider API.

## Product vision
Build a reliable, developer-friendly SMS CPaaS inspired by platforms such as Twilio and Brevo, with the long-term goal of offering it as a service. The platform will be developed incrementally and should avoid paid third-party SMS APIs and paid infrastructure during development.

## Technology roadmap
- Backend: Java 21, Spring Boot 3.x, Maven
- Data storage: PostgreSQL
- Frontend (later): React and TypeScript
- Local infrastructure (later): Docker Compose
- Version control: Git

## Current development status
Project initialization

## Webhook security note
Webhook subscription management accepts only HTTPS URLs and does not make outbound requests. HTTPS and URL syntax checks alone do not prevent server-side request forgery (SSRF). Before enabling webhook delivery, implement destination protections for private, loopback, link-local, and other internal IP ranges; validate DNS results at connection time to address DNS rebinding; and revalidate redirect targets. Never connect to a user-supplied URL until these protections are in place.

Webhook signing encryption uses `OPENSMS_WEBHOOK_ENCRYPTION_KEY`, which must be Base64 encoding of exactly 32 random bytes (AES-256). Keep this key outside source control and protect backups; losing it prevents decryption of existing signing secrets.

## Webhook event queue foundation
The backend records these supported event types when the corresponding message lifecycle transition commits:

| Event | Message transition |
| --- | --- |
| `message.queued` | A message is created with `QUEUED` status |
| `message.simulated` | The mock gateway accepts a message and it transitions to `SIMULATED` |
| `message.sent` | A non-simulated gateway accepts a message and it transitions to `SENT` |
| `message.failed` | A message transitions to `FAILED` |

For each matching active tenant subscription, OpenSMS stores a durable `PENDING` delivery row containing the destination URL and encrypted signing-secret/IV snapshots. The live subscription foreign key is set to null on subscription deletion; the immutable subscription ID snapshot and delivery data remain for audit and idempotency. The unique event-ID/subscription-snapshot pair prevents duplicate queue rows for the same logical message event. This is a persisted queue foundation only: it does not perform delivery, and no outbound HTTP requests are made.

Each payload has version 1 and this shape: `{ "version": 1, "eventId": "uuid", "eventType": "message.queued", "occurredAt": "ISO-8601 timestamp", "messageId": "uuid", "tenantId": "uuid", "destinationPhoneNumber": "+...", "messageStatus": "QUEUED" }`. The event ID is stable for a given message ID and event type and is shared by all subscriptions receiving that event.

## Message usage analytics
Authenticated API-key requests can access:

- `GET /api/v1/analytics/summary?from=2026-01-01&to=2026-01-30`
- `GET /api/v1/analytics/messages/daily?from=2026-01-01&to=2026-01-30`

The `from` and `to` parameters are optional ISO dates (`yyyy-MM-dd`). Both dates are inclusive in the response; database filtering uses `[from at 00:00 UTC, day after to at 00:00 UTC)`. If omitted, the range is the last 30 UTC calendar days including today. If only one is supplied, the missing endpoint defaults to today (`to`) or 29 days before `to` (`from`). Future dates, reversed ranges, and ranges over 366 days are rejected with HTTP 400.

The summary response contains lifetime `totalMessages` and `messagesByStatus`, plus `periodStart`, `periodEnd`, `periodTotalMessages`, and `periodMessagesByStatus`. Status maps include every current `MessageStatus`, with zero counts included. Daily responses contain `periodStart`, `periodEnd`, and chronological `days`; each day has `date`, `totalMessages`, and a complete `messagesByStatus` map, including zero-message dates. Example summary: `{ "totalMessages": 42, "messagesByStatus": { "QUEUED": 2, "PROCESSING": 0, "SENT": 10, "DELIVERED": 0, "SIMULATED": 28, "FAILED": 2 }, "periodStart": "2026-01-01", "periodEnd": "2026-01-30", "periodTotalMessages": 12, "periodMessagesByStatus": { "QUEUED": 1, "PROCESSING": 0, "SENT": 5, "DELIVERED": 0, "SIMULATED": 5, "FAILED": 1 } }`.

These endpoints report message usage metrics only. They do not calculate billing, pricing, revenue, or confirmed carrier-delivery analytics; the `DELIVERED` count reflects only the existing message status value.

## Contact management
Contact APIs require the existing API-key authentication through `X-API-Key`; the tenant is always taken from the authenticated principal. No contact endpoint accepts a tenant ID.

- `POST /api/v1/contacts` — create a contact; returns `201 Created` and the contact response.
- `GET /api/v1/contacts/{id}` — retrieve a tenant-owned contact.
- `GET /api/v1/contacts?page=0&size=20&search=ali` — list tenant contacts.
- `PUT /api/v1/contacts/{id}` — replace contact name, phone number, email, and description.
- `DELETE /api/v1/contacts/{id}` — delete a tenant-owned contact; returns `204 No Content`.

Example create request: `{ "name": "Alice Smith", "phoneNumber": "+14155552671", "email": "alice@example.com", "description": "Billing contact" }`. Example response: `{ "id": "uuid", "name": "Alice Smith", "phoneNumber": "+14155552671", "email": "alice@example.com", "description": "Billing contact", "createdAt": "2026-11-23T12:00:00Z", "updatedAt": "2026-11-23T12:00:00Z" }`. List responses wrap contact entries in `content` with `page`, `size`, `totalElements`, `totalPages`, `hasNext`, and `hasPrevious` metadata. Name is trimmed, required, and limited to 150 characters. Phone number is required and must match the existing E.164 rule (8–15 digits after `+`). Email is optional, must be valid when supplied, and is limited to 254 characters. Description is optional and limited to 1000 characters. Blank optional values are stored as null; PUT replaces all editable fields, so omitted optional fields become null.

Phone numbers must be unique within a tenant; PostgreSQL enforces this with a `(tenant_id, phone_number)` unique constraint, and duplicates return HTTP 409. Different tenants may use the same phone number. Contact IDs belonging to another tenant return the same 404 response as missing IDs. Listing defaults to page 0 and size 20, supports page sizes 1–100, and returns the standard contact page metadata. Results are ordered by creation time descending with ID as a stable tie-breaker. `search` matches case-insensitive name prefixes or phone-number prefixes; `%`, `_`, and `!` are treated literally. Search and pagination are applied in tenant-scoped database queries.

## Idempotent message submission
`POST /api/v1/messages` accepts an optional `Idempotency-Key` header. Existing clients may omit it. When supplied, the key must contain 1–128 visible ASCII characters with no whitespace or control characters. Keys are scoped to the authenticated tenant; clients cannot supply a tenant ID. Do not place secrets in this header.

Example request:
```http
POST /api/v1/messages
X-API-Key: <tenant-api-key>
Idempotency-Key: order-8452-message-1
Content-Type: application/json

{"destinationPhoneNumber":"+14155552671","body":"Your order has shipped."}
```

The first request returns the existing `202 Accepted` message response, for example `{ "id": "<message-uuid>", "destinationPhoneNumber": "+14155552671", "body": "Your order has shipped.", "status": "QUEUED", "createdAt": "<timestamp>", "updatedAt": "<timestamp>" }`. Repeating the same key with the same destination and exact message body returns that original response, including its original message ID and initial queued timestamps. Reusing the key with a different request returns `409 Conflict` with the standard API error response, for example `{ "status": 409, "error": "Conflict", "message": "Idempotency-Key was already used with a different message request.", "path": "/api/v1/messages", "timestamp": "<timestamp>" }`. The request fingerprint is SHA-256 over a versioned, length-prefixed UTF-8 encoding of the validated E.164 destination and exact body; body whitespace remains significant. No raw request body, API key, or idempotency key is logged or included in the fingerprint.

PostgreSQL enforces uniqueness on `(tenant_id, idempotency_key)`. The service claims a key with `INSERT ... ON CONFLICT DO NOTHING` inside the same transaction that writes the message, initial status history, outbox event, and webhook delivery records. A concurrent duplicate waits on that unique-key conflict; after the winning transaction commits, it reads and replays the winner's response (or returns 409 if the fingerprint differs). If the winner rolls back, another request can claim the key. A deferred foreign key ensures a committed idempotency record references its message. The database constraint is the final duplicate-prevention guard. PostgreSQL concurrency behavior is designed into the transaction but has not been integration-tested in this workspace.

Idempotency records do not expire automatically. They remain until an explicit retention and cleanup policy is designed and implemented; deleting them would permit an old key to create a new message.
