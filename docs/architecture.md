# PharmAssist — Overall System Architecture

**Issue:** [SCRUM-48 (P-01)](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-48)  
**Status:** Architecture design baseline (AD-01–AD-09).  
**Diagram:** [Architecture overview](architecture.png) · [Editable draw.io source](architecture.drawio)

## 1. Architecture description (one-page core)

### Summary

- One feature-based React Native/Expo/TypeScript client and a Spring Boot modular monolith serve Android, iOS and web.
- MySQL 8 is authoritative for persistent application data; a separate Python pipeline imports verified, versioned TİTCK records through the medication dataset module.
- Rule-based clinical information processing, least-privilege access, and isolated service adapters keep external providers outside assessment decisions.

| Component | Responsibility | Communication |
|---|---|---|
| Cross-platform client | Patient UI, read-only personnel UI, admin provisioning; shared components and central API client | REST/JSON/HTTPS (TLS 1.2+); multipart media |
| Spring Boot backend | Nine feature modules; controllers, services and owned repositories; Spring Security | Java service interfaces internally; OpenAPI; intended `/api/v1/` |
| MySQL 8 | Accounts, encrypted profiles/TCKN, medications, sources, reminders, history and audit | Spring Data JPA, Connector/J, Flyway; cloud JDBC/TLS |
| Independent data pipeline | Acquire, extract, normalize, validate and version KÜB/KT data; no automatic refresh | Controlled JSON import through dataset module; no direct database writes |
| Search recognition adapters | Server-side Tesseract/Tess4J OCR and Turkish speech recognition; text fallback | Search module → local OCR library or speech provider via HTTPS |
| Information adapter | Turkish explanations from verified findings; validation and template fallback | Information module → LLM via HTTPS; source text and anonymized findings only |
| Email verification adapter | Verify account reactivation; fail closed | Authentication module → email provider via HTTPS; no health data |
| Device capabilities | Camera, microphone, Turkish TTS, offline notifications and pending intake events | Platform APIs; authenticated, idempotent synchronization |

**Security and data flow.** Patients manage their own records; personnel read active patient profiles only; admins provision personnel accounts and assign roles without patient-profile access. Product confirmation precedes rule-based assessment; known risks remain visible even with insufficient information. Deactivation revokes sessions, reactivation requires registered-email verification, media is temporary, and secrets stay outside Git. Synthetic data, encryption, audit logging and verification apply system-wide.

**Deployment.** Local Docker MySQL supports development/tests; Aiven MySQL Developer is recommended for shared development for Sprint 5. The API runs locally initially; Render is planned for final API deployment in Sprint 12 and is a candidate for web hosting, whose configuration remains open. Expo builds target Android/iOS; distribution details remain open. See Appendix C for resolution links.

## 2. Appendix A — Architecture decisions

The following nine decisions define the architecture design baseline; they do not imply completed implementation.

| ID | Decision | Rationale and implementation boundaries |
|---|---|---|
| AD-01 | Modular monolith | One Spring Boot deployable with explicit module boundaries; lower operational complexity than microservices. |
| AD-02 | Layered backend, package by feature | Controllers handle API requests; services implement business rules; repositories manage owned data. DTOs and entities where needed. No cross-module repository calls. |
| AD-03 | Feature-based cross-platform client | Shared React Native/Expo/TypeScript code, reusable components, central API client and intended Expo Router navigation. Preserve compatible existing repository structure; platform-specific capabilities where needed. |
| AD-04 | Component communication | REST/JSON/HTTPS, multipart media, internal Java service interfaces, adapter interfaces, JDBC/JPA, OpenAPI and intended `/api/v1/` versioning. No additional message broker or gateway required. |
| AD-05 | Shared MySQL 8 schema per environment | Module-level data ownership, relational constraints, Flyway-only schema evolution, Hibernate validation, environment-based settings. Local Docker tests isolated from shared cloud. Aiven recommendation is not a provisioned service. |
| AD-06 | Independent medication pipeline | Python, JSON, automated and manual verification, versioned dataset, controlled importer, source version/access/review dates. Invalid records withheld; no runtime TİTCK fetch or automated refresh. Raw files subject to source terms and storage limits. |
| AD-07 | Adapter pattern | Server-side OCR; search → speech; information → LLM; authentication → email; device/browser TTS. Timeouts and text-search, template or written-information fallbacks; failed email verification never bypassed. Reminders use device notifications, not email; email reminders are outside the SoW scope. |
| AD-08 | Defense in depth and least privilege | Spring Security, RBAC, ownership/status checks, masked TCKN, AES-256 or equivalent health-data protection, salted password hashes, 30-minute idle expiry, session invalidation, secure errors, input validation, parameterized queries, CORS, rate limiting and privacy-preserving audit logs. Session mechanism and encryption/key details remain implementation decisions. |
| AD-09 | Data flow and storage | MySQL authoritative for persistent application records; no direct client DB access. Temporary media processing, device-based offline notifications, securely queued intake confirmations and idempotent synchronization. Account deactivation retains records but blocks patient/personnel access. |

### Data flow and storage boundaries

| Flow / data | Architecture boundary |
|---|---|
| Search and assessment | Client input → search/recognition → verified candidates → explicit patient confirmation → rule-based risk/equivalent services reading the profile and dataset through module interfaces → source-linked result → optional validated Turkish explanation / TTS. Known risks remain visible even if information is insufficient. |
| Personnel access | Authorized personnel → API/security → personnel module → active-account and profile services → limited read-only response; access is logged. No personnel medication search, assessment, equivalent recommendations or intake-history reports. |
| Administrator | Personnel account creation and role assignment only within agreed SoW scope; no patient-profile access or clinical tools. Retained-record internal access policy remains to be documented separately. |
| Reminders | MySQL schedules → mobile local notifications (permission required); securely queued offline confirmations → authenticated API → idempotent sync to MySQL. Unconfirmed dose after two hours is “not marked as taken,” not confirmed non-adherence. |
| Medication data | Official documents and raw/processed files in a controlled pipeline workspace (physical storage location open under OD-09); versioned validated JSON plus provenance; authorized importer calls the dataset module, which validates and persists the records through its owned repository. No direct pipeline-to-persistence/database writes. Runtime reads verified MySQL data only. |
| Sensitive data and secrets | MySQL: protected health profiles/TCKN, salted password hashes, audit metadata without full health profiles. Environment variables or secret manager: credentials/API keys. Images/audio temporary, not persistently retained by default. Session storage to be selected under SCRUM-105. |

## 3. Appendix B — Epic-to-component mapping

| Epic | Architecture component | Responsibility |
|---|---|---|
| [SCRUM-20](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-20) | Cross-platform client; backend foundation | Shared UI, API and platform integration |
| [SCRUM-21](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-21) | Authentication & roles | Registration, login, TCKN, role assignment, deactivation/reactivation |
| [SCRUM-22](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-22) | Health profile | Conditions, allergies, demographics, current medications and dosage |
| [SCRUM-23](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-23) | Medication dataset; independent pipeline; MySQL | Verified data, provenance and controlled imports |
| [SCRUM-24](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-24) | Medication search; OCR/speech adapters | Text, image, voice, candidates and confirmation |
| [SCRUM-25](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-25) | Rule-based risk assessment | Documented risks and insufficient information |
| [SCRUM-26](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-26) | Equivalent assessment | All ingredients, respective strengths, form, route; profile screening |
| [SCRUM-27](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-27) | Information presentation; LLM adapter; device TTS | Source-linked Turkish explanations and validation/fallback |
| [SCRUM-28](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-28) | Personnel panel | Active-patient search and read-only profile access |
| [SCRUM-29](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-29) | Cross-cutting verification, security & deployment | Testing, security, performance, deployment and recovery |
| [SCRUM-96](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-96) | Reminders & intake history; device APIs | Schedules, offline notifications, confirmation and history |

## 4. Appendix C — Open decisions and resolution tracking

The architecture decisions are selected; the following lower-level choices remain open or require confirmation. Jira references identify related work; where a resolution task is not confirmed, the reference is marked as provisional.

| ID | Remaining decision / verification | Related Jira issues | Status |
|---|---|---|---|
| OD-01 | Select and provision shared Aiven MySQL plan/region, verify TLS and backup configuration; later evaluation plan and restore tests | [SCRUM-102](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-102), [SCRUM-89](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-89) | Aiven Developer recommended; provisioning not confirmed |
| OD-02 | Confirm existing Expo/Java versions, API route compatibility, Render API/web hosting configuration and Expo mobile build/distribution approach | [SCRUM-50](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-50), [SCRUM-98](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-98), [SCRUM-100](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-100), [SCRUM-101](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-101), [SCRUM-89](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-89) | Foundation tasks reported Done; versions not fully verified |
| OD-03 | Select session/token mechanism, secure TCKN lookup/key management | [SCRUM-105](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-105), [SCRUM-52](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-52), [SCRUM-53](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-53), [SCRUM-55](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-55), [SCRUM-51](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-51) | Authentication design in progress; email provider tracked separately in OD-08 |
| OD-04 | Confirm schema, source access, extraction, validation and import implementation against selected pipeline | [SCRUM-61](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-61), [SCRUM-62](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-62), [SCRUM-63](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-63), [SCRUM-64](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-64), [SCRUM-65](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-65), [SCRUM-106](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-106), [SCRUM-107](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-107), [SCRUM-108](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-108) | Schema design reported Done; extraction/access under review; exact contracts not verified |
| OD-05 | OCR configuration and Turkish speech recognition provider/feasibility | [SCRUM-67](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-67), [SCRUM-68](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-68) | Open; provider and implementation tests pending |
| OD-06 | LLM provider, factual validation implementation and TTS platform compatibility | [SCRUM-80](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-80), [SCRUM-81](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-81), [SCRUM-82](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-82) | Open; LLM cannot determine assessment results |
| OD-07 | Mobile notification library, offline sync conflict handling, web reminder behavior | [SCRUM-96](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-96) | Open; provisional epic-level reference (specific resolution task not confirmed). |
| OD-08 | Select email verification provider, expiry/retry behavior and secure integration for account reactivation | [SCRUM-21](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-21), [SCRUM-105](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-105) (candidate) | Open; SCRUM-105 is a candidate resolution task; email scope not confirmed. |
| OD-09 | Decide physical storage for source PDFs and raw/processed data (controlled local workspace, private object storage or repository for permitted small files); define access, provenance, retention and reproducibility | [SCRUM-62](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-62), [SCRUM-65](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-65), [SCRUM-107](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-107) (candidate) | Open; related pipeline tasks are candidate resolution references. |

## 5. Requirements baseline and implementation evidence

- SoW §§3, 6.1–6.6, 7.1, 8, 9, 11, 12 and 15 form the requirements baseline. MySQL 8 supersedes the PostgreSQL references in P-01 and SoW §§9 (T1) and 12; those references require a separate documentation correction. P-01 groups OCR with external services; this design places Tesseract/Tess4J inside the backend behind an adapter, without requiring a remote OCR service.
- [SCRUM-102](https://stu-team-mnqnew35.atlassian.net/browse/SCRUM-102) and [merged backend PR #2](https://github.com/pharmassist-yu/pharmassist-backend/pull/2) report MySQL/JPA/Connector/J/Flyway integration, successful V1 migration and 12 passing tests. [Backend README](https://github.com/pharmassist-yu/pharmassist-backend/blob/main/README.md) documents local Docker MySQL (host port 3307), Hibernate `ddl-auto=validate`, credentials outside Git, cloud comparison and Aiven recommendation. Tests were not independently rerun for this architecture issue.
- The README recommends Aiven Developer only for shared development, not as proof of NFR-5.x compliance. Evaluation-grade backups, restoration, TLS and availability require configuration and measured tests. No tests against shared cloud.
