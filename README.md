# Project SHRAPNEL

## Overview
Project SHRAPNEL is a secure ephemeral file storage system built with Spring Boot 4.0.2 and Java 25. It uses JPA/Hibernate Envers for metadata auditing and a custom ShatteringEngine for file fragmentation. The system includes a background service that monitors file expiration and securely "nukes" expired data.

### System flow (high level)
1. **Upload** – a user POSTs a file to `/api/SHRAPNEL/shatter` along with an expiration period.
2. **Temporary storage & encryption** – the controller writes the upload to a temp path and, in a real deployment, would encrypt the payload.
3. **Fingerprinting (FR6)** – before further processing the SHA‑256 hash of the encrypted blob is calculated and a tiny transaction containing the digest is sent to Polygon; MLFlow logs cost/latency.
4. **Shattering** – the `ShatteringEngine` slices the file into random‑sized shards using Project Panama and virtual threads, persisting them to disk and recording metadata.
5. **Persistence** – a `FileMetaData` record capturing filename, size, expiry, shard list and blockchain fields is stored in PostgreSQL; Hibernate Envers audits every change.
6. **Expiry & nuking** – `NukeScheduler` periodically looks for expired, non‑nuked entries; `NukeService` overwrites the first kilobyte of each shard, deletes it from disk and marks the metadata as nuked.  The blockchain fingerprint remains untouched.
7. **Reassembly** – a separate endpoint can recombine shards for a given metadata ID if the file has not yet expired.

## Features
- File shattering into secure shards
- Ephemeral storage with automatic expiration
- Background nuke service for expired files
- Anti-forensic data destruction (overwrites headers before deletion)
- Audit logging via Hibernate Envers
- Virtual threads for high-performance I/O operations

## Requirements
- Java 25
- Spring Boot 4.0.2
- PostgreSQL database
- Maven for build management

## Database Configuration
The application uses PostgreSQL. Update the connection details in `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/shrapnel_db
    username: postgres
    password: your_password
    driver-class-name: org.postgresql.Driver
```

## Storage Configuration
Files are stored in the `./shrapnel_data` directory. Configure the storage path in `application.yaml`:

```yaml
shrapnel:
  engine:
    storage-path: ./shrapnel_data
    shard-size-mb: 1
```

## Building the Project
1. Ensure you have Java 25 and Maven installed.
2. Clone the repository.
3. Navigate to the project directory.
4. Run the following command to build:

```bash
mvn clean compile
```

## Running the Application
1. Ensure PostgreSQL is running and the database is created.
2. Update the database credentials in `application.yaml`.
3. Run the application:

```bash
mvn spring-boot:run
```

The application will start on the default port (8080). The background nuke scheduler will run every minute to check for expired files.

## API Endpoints
- Upload files via the ShatterController
- Reassemble files via the ReassemblyController

## Security
- Basic authentication is configured with default credentials (naren/123)
- Update security settings in `application.yaml` and `SecurityConfig.java`

## File Expiration and Nuking
- Files have an `expirationTime` set when uploaded
- The `NukeScheduler` runs every minute to find expired files
- Expired files are "nuked": first 1024 bytes overwritten with zeros, then files deleted
- The `isNuked` flag is set to true for audit purposes
- All operations use virtual threads for performance

## Audit Logging
Hibernate Envers captures all changes to FileMetaData and FileShard entities in audit tables with `_AUDIT_LOG` suffix.

---

## Feature FR6: Immutable Blockchain Fingerprinting

When a file is successfully encrypted and before it is shredded, the system
calculates a SHA‑256 fingerprint of the encrypted blob.  That digest is pushed
onto the Polygon Layer‑2 network using Web3j; only the hash itself is recorded
(no actual file bytes or secrets ever touch the chain).  The transaction hash
returned by the network is stored in the same `FileMetaData` record and is
covered by Envers so that an immutable audit trail exists even after the
physical shards have been nuked.

To help control costs the application logs each blockchain interaction to an
MLFlow experiment: gas price &amp; limit, file size, confirmation time, success
status, etc.  A companion Optuna script (`optuna_tuner.py`) explores the
`maxPriorityFeePerGas` and virtual thread‑pool size parameters looking for the
most economical combination.

### Runtime sequence (FR6 specific)
1. User uploads a file via `/api/SHRAPNEL/shatter`.  The controller saves it to
a temporary path and (optionally) encrypts it.
2. SHA‑256 hash of the encrypted file is computed by
   `BlockchainFingerprintService.computeSha256()`.
3. A simple transaction containing the hash as its data payload is sent to
   Polygon; a virtual thread waits for the receipt so OS threads are not
   consumed.
4. MLFlow logs parameters and metrics for the run; the transaction hash is
   persisted on the `FileMetaData` entity and the entity is saved.
5. The regular shattering engine splits the file and the metadata (including
   blockchain fields) is written to Postgres.
6. Even if the file is later nuked, the blockchain hash remains as evidence of
   what used to exist.

### Running the system with FR6
1. Make sure your `application.yaml` is populated with a valid Polygon
   provider URL and a private key/or mnemonic; do **not** check secrets into
   source control.  Example:

```yaml
shrapnel:
  blockchain:
    provider-url: https://polygon-mumbai.infura.io/v3/<your-key>
    private-key: ${BLOCKCHAIN_PRIVATE_KEY}
```

2. Start an MLFlow tracking server (e.g. `mlflow server --backend-store-uri sqlite:///mlflow.db`).
3. Build and run the Spring Boot app as usual (`mvn clean compile spring-boot:run`).
4. Upload a file via the REST API or Swagger UI.  After the upload completes,
   check MLFlow UI (http://localhost:5000 by default) for a new run with the
   parameters/metrics above.
5. Use your blockchain explorer to inspect the transaction hash stored in the
   metadata record – it should contain the SHA‑256 digest in the data field.

### Tuning with Optuna (AI part)
1. Make sure Python 3 and `pip` are available, then install dependencies:

```bash
pip install optuna web3
```

2. Export your provider URL/private key as environment variables:

```bash
export PROVIDER_URL="https://polygon-mumbai.infura.io/v3/<key>"
export PRIVATE_KEY="0x..."
```

3. Run the tuner script:

```bash
python optuna_tuner.py
```

4. After the study completes it will print the best trial parameters and the
   corresponding cost reduction.  You can tweak the Java defaults (`
   shrapnel.blockchain.default-gas-price` and
   `shrapnel.blockchain.default-gas-limit`) based on the results.

---
