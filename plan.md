# MedOps Enhancement Plan

**Last updated:** 2026-11-05
**Verified against:** live source code (full codebase scan + direct file inspection)
**Live deployment:** https://medops.duckdns.org

This plan is grounded in direct code inspection, not AI-generated assumptions.
Every item is marked with its verified status before any work begins.

---

## Verified implementation status

The following were claimed as missing by one or more AI tools and have been
confirmed as **already implemented** by reading the source:

| Area | Status | Evidence |
|---|---|---|
| Appointment conflict prevention | ✅ Implemented | `BookAppointmentService` — `SELECT FOR UPDATE` on doctor row + overlap query + `DataIntegrityViolationException` catch. `AppointmentBookingConcurrencyIT` exists. |
| Appointment reconciliation | ✅ Implemented | `AppointmentReconciliationService` — scheduled, auto-completes past BOOKED appointments |
| Refresh-token rotation | ✅ Implemented | `AuthService.refresh()` — revokes old token, issues new one. `RefreshToken.revoke()` sets `revokedAt`. |
| Refresh-token revocation on logout | ✅ Implemented | `AuthService.logout()` — finds token by hash, calls `revoke()` |
| Rate limiting | ✅ Implemented | `AuthRateLimitFilter` — covers login, refresh, registration, appointments, reports, prescriptions. Redis-backed in prod. |
| Audit logging | ✅ Implemented | `AuditService` + 25+ `AuditEventType` values |
| IDOR protection (appointments, reports, prescriptions) | ✅ Implemented | `ClinicalAccessService.assertPatientOwns()`, `assertDoctorOwns()`, `requireTreatingDoctor()` |
| Billing IDOR — patient-scoped reads | ✅ Implemented | `BillingController` calls `clinicalAccess.assertPatientOwns(patientId, auth.getName())` before every read |
| Billing IDOR — invoice ownership on payment | ✅ Implemented | `InvoiceService.payableTotal()` checks `invoice.getPatientProfileId().equals(patientProfileId)` and throws on mismatch |
| Refund endpoint | ✅ Implemented | `AdminBillingController.refund()` + `PaymentService.refund()` — sets status REFUNDED, recalculates invoice status |
| Stripe webhook signature verification | ✅ Implemented | `StripeClient.constructEvent` uses official SDK `Webhook.constructEvent` |
| Webhook idempotency | ✅ Implemented | `paymentRepository.findByReference()` check + unique constraint catch in `StripePaymentService.settle()` |
| Transaction boundary (no Stripe call inside tx) | ✅ Implemented | `validateAndGetBalance()` commits before HTTP call; no `@Transactional` spans Stripe |
| Transactional outbox | ✅ Implemented | `DomainEventOutboxDispatcher` — polls, sends to Kafka with `.get(10s)`, marks sent only after ack |
| Notification system | ✅ Implemented | `NotificationEventConsumer` — Kafka consumer for `AppointmentBooked` and `ReportUploaded`. SSE stream. Deduplication on `(user_id, source_event_id)`. |
| Medical report uploads + authorization | ✅ Implemented | `ClinicalReport` with storage_key, status lifecycle, AI summary. `ClinicalAccessService` enforces ownership. |
| Report summarization (AI) | ✅ Implemented | `SummarizeReportService`, `ReportSummarizer`, `RedisReportSummaryCache`, `ClinicalReport.applySummary()` |
| Resilience4j circuit breakers / retries | ✅ Implemented | `reportSummarizer` circuit breaker (50% threshold, 30s open), 20s time limiter, 2-attempt retry. `smtpMail` circuit breaker + 3-attempt retry. |
| Observability stack | ✅ Implemented | `docker-compose.observability.yml` — Prometheus 3.5.0, Grafana 12.2.0, Dynatrace OneAgent (profile), OTel Collector (profile). `ops/prometheus.yml`, `ops/grafana/dashboards/`, `ops/rules.yml`. |
| Micrometer metrics | ✅ Implemented | `micrometer-registry-prometheus` in `pom.xml`. `MeterRegistry` injected in `DomainEventOutboxDispatcher`. |
| Security headers | ✅ Implemented | HSTS, X-Frame-Options DENY, Referrer-Policy, Permissions-Policy in `SecurityConfig` |
| CI vulnerability scanning | ✅ Implemented | Trivy, CodeQL, Semgrep, OSV-Scanner, pip-audit, OWASP dependency-check, Dependabot |
| Billing IDOR — invoice ownership on payment list | ✅ Implemented | `BillingController.listPayments()` calls `invoiceService.assertInvoiceBelongsToPatient()` after `assertPatientOwns()` (P0-1, commit c255833) |
| Idempotency_key width | ✅ Implemented | `Payment.idempotencyKey` is `VARCHAR(128)` via Flyway `V11__widen_idempotency_key.sql` (P0-2, commit c3eea0e) |
| Cancellation notifications | ✅ Implemented | `AppointmentCancelledEvent` published from `AppointmentTransitionService.cancel()` via outbox → `NotificationEventConsumer.onAppointmentCancelled()` sends `APPOINTMENT_CANCELLED` to patient + doctor (P1-1, commit c3eea0e) |
| Rescheduling notifications | ✅ Implemented | `AppointmentRescheduledEvent` published from `AppointmentTransitionService.reschedule()` via outbox → `NotificationEventConsumer.onAppointmentRescheduled()` sends `APPOINTMENT_RESCHEDULED` to patient + doctor (P1-2, commit c3eea0e) |
| Payment confirmed notifications | ✅ Implemented | `PaymentConfirmedEvent` published from `StripePaymentService.settle()` after `recordPayment()` success → `NotificationEventConsumer.onPaymentConfirmed()` sends `PAYMENT_CONFIRMED` to patient (P1-3, commit 21bb98a) |

---

## Confirmed gaps

These were verified as **not yet implemented** by direct code inspection:

| # | Gap | Verified by |
|---|---|---|
| G1 | Cancellation and rescheduling notifications | ✅ Resolved — `AppointmentCancelledEvent` / `AppointmentRescheduledEvent` published via outbox, consumed by `NotificationEventConsumer` (P1-1/P1-2, commit c3eea0e) |
| G2 | Appointment reminders (scheduled) | No scheduler, no reminder entity, no reminder Kafka event |
| G3 | Payment notifications | ✅ Resolved — `PAYMENT_CONFIRMED` notification type + consumer, published from `StripePaymentService.settle()` (P1-3, commit 21bb98a) |
| G4 | Doctor availability / working hours model | `DoctorProfile` has specialty, license_number, phone — no schedule, working hours, or leave fields |
| G5 | Medical history and allergies | No `MedicalHistory` or `Allergy` entity anywhere in the codebase |
| G6 | Prescription PDF download | `Prescription` has optional `storageKey` but no download endpoint or PDF generation |
| G7 | Payment reconciliation | No scheduled reconciliation job comparing local state with Stripe |
| G8 | `idempotency_key` column width | ✅ Resolved — `Payment.idempotencyKey` is `VARCHAR(128)` via Flyway `V11__widen_idempotency_key.sql` (P0-2, commit c3eea0e) |
| G9 | Multi-hospital / multi-tenant support | No `hospital_id`, no tenant model, no tenant-scoped queries anywhere |
| G10 | Alertmanager / alert delivery | `ops/rules.yml` defines Prometheus rules but no Alertmanager config or alert-delivery path exists |
| G11 | Centralized logging (Loki or equivalent) | No Loki, no log aggregation config in any compose file |
| G12 | FastAPI production metrics | `medops-ai` has `app/utils/metrics.py` but no Prometheus scrape target in `ops/prometheus.yml` |
| G13 | Browser-level E2E test suite | No Playwright, Cypress, or equivalent. Frontend has Vitest unit tests only. |

---

## Billing authorization — resolved ✅

The plan raised a potential IDOR in billing. Direct inspection confirms it is **not present**, and the remaining gap has been fixed:

- `BillingController` calls `clinicalAccess.assertPatientOwns(patientId, auth.getName())` before
  every read (`listInvoices`, `getInvoice`, `listPayments`, `createStripeCheckoutSession`).
- `InvoiceService.payableTotal()` independently verifies `invoice.getPatientProfileId().equals(patientProfileId)`
  and throws `InvalidRequestException` on mismatch — a second defence even if the controller check were bypassed.
- **`BillingController.listPayments()` now calls `invoiceService.assertInvoiceBelongsToPatient(invoiceId, patientId)`**
  after the patient-ownership check, closing the gap where a patient who knows another patient's `invoiceId` could
  list its payments via `GET /api/v1/patients/{their-own-id}/billing/invoices/{other-invoice-id}/payments`.
  Verified by `BillingAuthorizationTest` (owner → 200, stranger → 400) in commit c255833.

---

## Prioritized roadmap

### P0 — Fix before next release ✅ All complete

| ID | Task | Status | Commit |
|---|---|---|---|
| P0-1 | Fix `listPayments` invoice ownership gap | ✅ Done | c255833 |
| P0-2 | Widen `idempotency_key` to `VARCHAR(128)` | ✅ Done | c3eea0e |

No remaining P0 items.

---

### P1 — Notifications (extend existing Kafka + outbox infrastructure)

| ID | Task | Status | Commit |
|---|---|---|---|
| P1-1 | Cancellation notifications | ✅ Done | c3eea0e |
| P1-2 | Rescheduling notifications | ✅ Done | c3eea0e |
| P1-3 | Payment confirmed notification | ✅ Done | 21bb98a |
| P1-4 | Appointment reminders | Pending | Scheduled job queries BOOKED appointments within a configurable window (e.g. 24h). Publishes reminder notifications. Tracks sent reminders to prevent duplicates. |

---

### P1 — Billing reliability

| ID | Task | Approach |
|---|---|---|
| P1-5 | Payment reconciliation | Scheduled job queries Stripe for checkout sessions in `open` or `expired` state older than a threshold. Compares with local `Payment` records. Logs discrepancies. Does not auto-settle without authoritative confirmation. |
| P1-6 | Razorpay (if required) | Reuse `Invoice`/`Payment` domain. Add `RAZORPAY` to `PaymentMethod`. New `RazorpayClient`, `RazorpayProperties`, `RazorpayPaymentService`, webhook controller. Mirror Stripe's signature verification and idempotency pattern. |

---

### P1 — Clinical workflows

| ID | Task | Approach |
|---|---|---|
| P1-7 | Doctor availability model | Add `DoctorAvailability` entity (day-of-week, start, end) and `DoctorLeave` entity (date range). `BookAppointmentService.listAvailableSlots()` filters against both. Flyway migration. |
| P1-8 | Medical history | New `MedicalHistory` entity under `patients` schema. Fields: condition, diagnosed_at, notes, status. Role-based access via `ClinicalAccessService`. Flyway migration. |
| P1-9 | Allergies | New `Allergy` entity under `patients` schema. Fields: allergen, severity, reaction, notes. Same access pattern as medical history. Flyway migration. |
| P1-10 | Prescription PDF download | Add `GET /api/v1/patients/{patientId}/prescriptions/{prescriptionId}/download` endpoint. Generate PDF via a library (e.g. OpenPDF) or serve stored file from `storageKey`. Enforce patient/doctor ownership. |

---

### P1 — Observability gaps

| ID | Task | Approach |
|---|---|---|
| P1-11 | FastAPI metrics scrape | Add `medops-ai:8000` as a scrape target in `ops/prometheus.yml`. Verify `app/utils/metrics.py` exposes a `/metrics` endpoint. |
| P1-12 | Alert delivery | Add Alertmanager service to `docker-compose.observability.yml`. Configure at least one receiver (email or webhook). Wire `ops/rules.yml` to Alertmanager. Test end-to-end alert delivery. |
| P1-13 | Centralized logging | Add Loki + Promtail to `docker-compose.observability.yml` if the hosting environment does not already provide log aggregation. Add Loki datasource to Grafana provisioning. |

---

### P2 — Commercial architecture

| ID | Task | Approach |
|---|---|---|
| P2-1 | Decide deployment model | Document whether MedOps targets single-hospital deployments, multi-branch, or multi-tenant SaaS. This decision gates all schema changes below. |
| P2-2 | Multi-tenant isolation (if SaaS) | Add `Hospital` entity. Add `hospital_id` FK to patient, doctor, appointment, invoice, report, prescription schemas. Enforce tenant scoping in all repository queries. Add cross-tenant access tests. Tenant-aware audit events, caches, and background jobs. |
| P2-3 | Department management | Add `Department` entity. Associate doctors and staff. Scope appointment slots by department if required. |
| P2-4 | CSV/PDF exports | Operational reports for admin: appointment summaries, payment summaries, patient lists. |

---

### P3 — Testing and release readiness

| ID | Task | Approach |
|---|---|---|
| P3-1 | Billing authorization regression tests | Test `GET /patients/A/billing/invoices/B` where invoice B belongs to patient B. Expect 4xx. Test `listPayments` with mismatched invoice. |
| P3-2 | Notification integration tests | Test that cancel/reschedule/payment events produce the correct notifications. Test deduplication. |
| P3-3 | Reconciliation tests | Test delayed, duplicated, and out-of-order webhook events. Test expired checkout session handling. |
| P3-4 | Browser E2E suite | Add Playwright. Cover: registration, login, appointment booking, invoice payment, report upload, prescription access. Run in CI on `dev` branch. |
| P3-5 | Release checklist | Document: run `./mvnw verify`, frontend build, AI service tests, migration verification, production config check, rollback procedure. |

---

## Implementation sequence

| Order | ID | Work item | Why first |
|---:|---|---|---|
| 1 | P1-4 | Appointment reminders | New scheduler, builds on notification system |
| 2 | P1-5 | Payment reconciliation | Closes billing reliability gap |
| 3 | P1-7 | Doctor availability model | Prerequisite for accurate slot calculation |
| 4 | P1-8, P1-9 | Medical history + allergies | Clinical completeness |
| 5 | P1-10 | Prescription PDF download | Completes prescription workflow |
| 6 | P1-11, P1-12, P1-13 | Observability gaps | Operational readiness |
| 7 | P3-1 – P3-3 | Regression + reconciliation tests | Validate all P0/P1 work |
| 8 | P2-1 | Decide deployment model | Must precede any tenant schema work |
| 9 | P2-2 – P2-4 | Commercial architecture | Depends on P2-1 decision |
| 10 | P3-4, P3-5 | E2E suite + release checklist | Final release gate |
| 11 | P1-6 | Razorpay | Conditional on business requirement |

---

## Definition of done

MedOps is commercially deployable when:

- **Security:** Every sensitive resource enforces both role and record-level authorization. Billing access cannot cross patient boundaries. Refresh-token lifecycle is tested. Medical documents are inaccessible to unauthorized users.
- **Clinical:** Appointment booking enforces availability and concurrency. Cancellation, rescheduling, and payment events produce reliable, deduplicated notifications. Reminders reflect current appointment state.
- **Financial:** Payment events are idempotent. Reconciliation detects and logs discrepancies. Invoice and payment states remain consistent after failures.
- **Operations:** Logs, metrics, traces, and alerts are reachable and actionable. AI-service failures are observable. Background job failures are surfaced.
- **Commercial:** Deployment model is documented. Tenant isolation is implemented and tested if SaaS. Rollback procedure is documented and tested.
- **Quality:** `./mvnw verify` passes in CI. Frontend build passes. Critical workflows have automated regression coverage. Migrations are verified against a clean database before release.
