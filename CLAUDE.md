# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Start the application
./gradlew bootRun

# Build (produces jar in build/libs/)
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.company.demo.SomeTest"

# Start PostgreSQL and DWH databases
docker-compose up -d
```

The app runs at http://localhost:8080. Default credentials: `admin` / `admin`.

PostgreSQL is required (no HSQLDB fallback configured). The Docker Compose file starts:
- `postgres` on port `15432` (main app DB, password: `psql1234`)
- `postgres-dwh` on port `16432` (DWH, credentials: `dwh_reporting` / `qweasd123`)
- `adminer` on port `8090` for DB admin UI

## Architecture

- Jmix 2.7.6 (Spring Boot + Vaadin FlowUI + EclipseLink) app. Root package: `com.company.demo`.
- Java 17
- PostgreSQL

### Package layout

| Package | Purpose |
|---|---|
| `entity/` | JPA entities persisted to PostgreSQL |
| `dto/` | In-memory Jmix entities (`@JmixEntity` without `@Entity`) used as non-persistent data holders |
| `enums/` | Enums with `fromId()`/`getId()` for string serialization in DB columns |
| `view/` | Jmix FlowUI views — each view has a Java class and a paired XML descriptor |
| `component/` | Custom reusable Vaadin/Jmix components with their loaders |
| `service/` | Spring services |
| `repository/` | Spring Data Jmix repositories |
| `security/` | Security roles and configuration |

### View pattern

Every view is a pair: `view/XxxView.java` ↔ `resources/com/company/demo/view/xxx/xxx-view.xml`. The XML defines layout and data containers (`<data>`, `<layout>`); the Java class handles event subscriptions via `@Subscribe`.

Data is loaded through `CollectionLoader` → `CollectionContainer`. Views inject these with `@ViewComponent`. JPQL queries run through the loader; parameters are set on the loader before calling `load()`.

### Key entities

- `DqRule` — data quality rule with JSONB config column; enums stored as strings; owned by `User`, categorized by `DqRuleCategory`
- `DqDataSource` — in-memory DTO (not a DB table); hardcoded list served by `DqDataSourceProvider`

### Database migrations

Liquibase changelogs live under `resources/com/company/demo/liquibase/changelog/`. New changelogs go in date-organized subdirectories (e.g., `2026/07/`). The root `changelog.xml` includes them.

### i18n

Message bundles: `messages_en.properties` and `messages_ru.properties` under `resources/com/company/demo/`. Both locales are enabled (`jmix.core.available-locales=en,ru`). Views reference messages via `msg://` in XML.

## Architecture rules
- Business logic only in @Service classes, NOT in View controllers
- Data access via DataManager or Repository interface (which extends from JmixDataRepository), not EntityManager
- View controllers: only UI events and delegation to Service
- Create DTO only for complex forms and filters