# Roadmap

## Milestone 0 - Research / compliance
- [x] Choose reference projects from public READMEs.
- [ ] Clone, audit LICENSE and sources, run upstream tests on developer machine.
- [ ] Confirm real notification payloads in Colombia using anonymized samples.

## Milestone 1 - Android-only MVP
- [ ] NotificationListenerService and explicit onboarding.
- [ ] Provider-specific parser fixtures, debit/credit classifier, dedupe.
- [ ] Offline Room history; TTS; display merchant's issued Bre-B QR.
- [ ] Tests: false positive rate, missing notifications, latency, device reboots.

## Milestone 2 - Rust sync backend
- [ ] Authenticated device registration, HTTPS only, revocable credentials.
- [ ] PostgreSQL multi-tenant event store, audit log, idempotency keys.
- [ ] Background sync, report reconciliation states without claiming bank verification.

## Milestone 3 - Official confirmations
- [ ] Select contracted provider with sandbox and documented signatures.
- [ ] Provider adapters, signed webhooks, reference matching, retries/reversals.
- [ ] Preserve source provenance; never upgrade Android observation without official evidence.
