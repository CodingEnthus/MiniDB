# MiniDB

A lightweight, modular educational relational database engine implemented in Java.

---

## Project Structure

```text
minidb/
├── pom.xml
├── src/
│   ├── main/java/com/minidb/storage/
│   │   └── PageFile.java
│   └── test/java/com/minidb/storage/
│       └── PageFileTest.java
└── README.md
```

---

## Components

### Storage Manager (`com.minidb.storage`)

- **`PageFile`**:
  - Encapsulates low-level physical file I/O operations directly against disk.
  - Divides the database file into fixed-size 4096-byte (4 KB) blocks called **Pages**.
  - Provides thread-safe methods to:
    - Allocate new pages (`allocatePage()`)
    - Read page contents (`readPage(int pageId, byte[] buffer)`)
    - Write page contents (`writePage(int pageId, byte[] buffer)`)
    - Retrieve page count and flush dirty pages to disk (`sync()`)
    - Safely manage file descriptors implementing `AutoCloseable`

- **`PageFileTest`**:
  - Comprehensive JUnit 5 test suite verifying:
    - Page allocation and file sizing
    - Data integrity across write-read cycles
    - Multi-page boundary isolation
    - Boundary checking (out-of-bounds page requests, negative page IDs)
    - Persistence across storage file reopens

---

## Requirements

- **Java JDK**: Version 17 or higher
- **Maven**: Version 3.8+ (optional if using an IDE or Maven wrapper)

---

## Build & Test Instructions

### Compile
```bash
mvn clean compile
```

### Run Unit Tests
```bash
mvn test
```

### Package JAR
```bash
mvn package
```
