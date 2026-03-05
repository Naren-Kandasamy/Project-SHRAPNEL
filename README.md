# Project SHRAPNEL

## Overview
Project SHRAPNEL is a secure ephemeral file storage system built with Spring Boot 4.0.2 and Java 25. It uses JPA/Hibernate Envers for metadata auditing and a custom ShatteringEngine for file fragmentation. The system includes a background service that monitors file expiration and securely "nukes" expired data.

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