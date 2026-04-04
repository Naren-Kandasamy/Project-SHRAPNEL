# Project SHRAPNEL - AI Benchmark & Golden Questions

## Purpose
This document implements the **Golden Questions / Benchmark Development Method** for Project SHRAPNEL. By establishing a rigid framework of fundamental truths about the project, we prevent AI context drift, reduce unnecessary token consumption (by keeping prompt context concise), and ensure that all generated code or architecture decisions strictly align with the core project boundaries.

When interacting with AI agents or opening a new session, provide this document (or reference it) as your context anchor.

---

## The Golden Questions (Core Identity)
Before writing any code, evaluating architectural choices, or modifying logic, the AI must align with the answers to these Golden Questions:

### 1. What is the fundamental purpose of Project SHRAPNEL?
Project SHRAPNEL is a **secure ephemeral file storage system**. It is designed to automatically expire, permanently destroy ("nuke"), and audibly track data. Data intentionally self-destructs; files are shattered into shards, forensically wiped, and immutably recorded on a blockchain.

### 2. What are the strict technological constraints & stack?
- **Backend Core**: Java 21, Spring Boot 4.0.2.
- **Concurrency**: Operations like shattering and nuking MUST use Java Virtual Threads (Project Loom/Panama).
- **Persistence & Auditing**: PostgreSQL database. All metadata (`FileMetaData`, `FileShard`) changes must be audited using Hibernate Envers (`_AUDIT_LOG`).
- **Storage Strategy**: Files are not stored intact. They are shattered into pieces and saved in `shrapnel_data`.
- **Blockchain (FR6)**: Polygon Layer-2 network (via Web3j). We store *only* SHA-256 fingerprints, never the actual data.
- **AI/Tuning**: MLFlow is used for logging blockchain transaction metrics, and Optuna is used for local gas optimization tuning.
- **Frontend App**: Next.js based (ref: `shrapnel-ui`). Follow Next.js App Router/modern paradigms as strictly specified by documentation.

### 3. What is the Immutable Data Lifecycle?
Any modifications to logic MUST respect this sequence:
1. **Upload & Encrypt**: File is uploaded directly to a temporary path, encrypted.
2. **Fingerprint**: Compute SHA-256 hash -> Push hash transaction to Polygon blockchain -> Log to MLflow.
3. **Shatter**: Cut the encrypted file into non-uniform shards via virtual threads -> Persist metadata in Postgres.
4. **Nuke (Expiry)**: Unconditionally overwrite the first 1024 bytes (header) of every shard with zeros -> Delete shard from disk -> Mark `isNuked=true`. Keep Blockchain metadata intact.

### 4. What are the non-negotiable security postures?
- Zero data touches the blockchain—only hashes.
- Shards cannot simply be marked as `deleted`; they must undergo secure deletion (overwriting).
- No secrets (API keys, MNEMONIC) in source code or `application.yaml`.

---

## Token Optimization & Context Directives

To optimize token usage natively in large language models:

1. **Avoid Boilerplate Reinvention:** Do not explain Spring Boot or Java fundamentals in prompts or AI responses. Assume expert-level knowledge of Java 21 & Virtual Threads.
2. **Targeted Context Injection:** 
   - Backend logic queries: Focus solely on `shrapnel` codebase and `ShatteringEngine` / `NukeService`. 
   - Polygon/Web3 queries: Include only `BlockchainFingerprintService` and `optuna_tuner.py`.
3. **Drift Prevention (Self-Correction):** If AI starts suggesting legacy threads (`Runnable`, `Thread.start()`), Spring Boot 2.x paradigms, OR suggests keeping files intact without shattering, IT HITS A CONSTRAINT VIOLATION and must reset context.
4. **Dry Runs:** When drafting prompts, reference exact class names referenced in this benchmark rather than providing entire file dumps unless the logic inside is specifically broken.

---

## AI Prompt Prefix Template
*Use this prefix when starting a high-context task to set bounds immediately:*

> "Act as a Senior AI Architect for Project SHRAPNEL (Java 21/Spring Boot 4/Postgres). We employ secure file shattering, virtual threads, MLFlow, and Polygon blockchain fingerprinting. Adhere strictly to the BENCHMARK.md constraints. Task: [Insert specific task here]."
