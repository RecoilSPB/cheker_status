# cheker-status

Java 8 service for tracking an NSI dictionary version and loading dictionary rows into PostgreSQL.

## What is stored

The schema is intentionally universal. API payloads are stored in `jsonb`, so new NSI fields do not require table changes.

- `nsi_dictionary` stores the latest passport and dictionary metadata.
- `nsi_dictionary_version` stores every seen version and marks the version as loaded with `loaded_at`.
- `nsi_dictionary_field` stores the current passport field structure.
- `nsi_record` stores the latest active row per dictionary key.
- `nsi_record_history` stores row snapshots per dictionary version.
- `nsi_document_git_link` stores parsed GitLab links from the `GIT_LINK` field.
- `nsi_document_git_commit` stores commits found for each document link.

## Run locally

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Build and run:

```bash
mvn clean package
java -jar target/cheker-status-0.0.1-SNAPSHOT.jar
```

By default the service tracks:

```text
1.2.643.5.1.13.13.99.2.638
```

The first synchronization runs on startup. Later checks run once per hour.

## Configuration

The service can be configured with environment variables:

```text
DB_URL=jdbc:postgresql://localhost:5432/checker_status
DB_USERNAME=checker_status
DB_PASSWORD=checker_status
NSI_USER_KEY=047d529e-f097-4e5d-a255-2f77ef132d3b
NSI_PAGE_SIZE=100
NSI_POLL_FIXED_DELAY_MS=3600000
NSI_POLL_INITIAL_DELAY_MS=60000
NSI_TRUST_ALL_SSL=true
NSI_SYNC_ON_STARTUP=true
GITLAB_ENABLED=true
GITLAB_REQUIRE_TOKEN=true
GITLAB_BASE_URL=https://git.minzdrav.gov.ru
GITLAB_TOKEN=
GITLAB_PER_PAGE=100
GITLAB_MAX_PAGES=1000
GITLAB_MAX_DOCUMENTS_PER_RUN=0
```

Additional dictionaries can be added in `src/main/resources/application.yml` under `nsi.dictionaries`.

`NSI_TRUST_ALL_SSL=true` is enabled by default because the current NSI HTTPS endpoint may fail Java certificate validation. Set it to `false` when the NSI certificate chain is available in the JVM trust store.

The Git integration uses the GitLab API:

```text
/api/v4/projects/{projectPath}/repository/commits?ref_name={treeRef}
```

`projectPath` and `treeRef` are parsed from `GIT_LINK`, for example:

```text
https://git.minzdrav.gov.ru/semd/1.2.643.5.1.13.13.15.98/-/tree/1.2.643.5.1.13.13.15.98.1
```

At the time of implementation `git.minzdrav.gov.ru` redirects browser requests to sign-in, so set `GITLAB_TOKEN` to a token that can read the projects.
