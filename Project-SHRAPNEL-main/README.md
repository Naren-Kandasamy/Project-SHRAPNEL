# Project-SHRAPNEL
## Below is the *temporary* file structue that Im following:
``` shrapnel-project/
├── src/
│   ├── main/
│   │   ├── java/com/shrapnel/
│   │   │   ├── ShrapnelApplication.java        <-- The "Power Switch"
│   │   │   ├── model/                          <-- D3 & D4 "ID Cards"
│   │   │   │   ├── FileMetadata.java
│   │   │   │   └── AuditLog.java
│   │   │   ├── service/                        <-- The "Brain" (Logic)
│   │   │   │   ├── FileEngineService.java      
│   │   │   │   ├── EncryptionService.java
│   │   │   │   └── AuditService.java
│   │   │   ├── repository/                     <-- The "Librarian" (DB Access)
│   │   │   │   ├── MetadataRepository.java
│   │   │   │   └── AuditRepository.java
│   │   │   └── controller/                     <-- The "Front Desk" (API)
│   │   │       └── FileController.java
│   │   └── resources/
│   │       └── application.properties          <-- Settings (DB info)
└── pom.xml                                     <-- The "Shopping List" ```
