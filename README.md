# cheker-status

Java 21 service for tracking an NSI dictionary version and loading dictionary rows into PostgreSQL.

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
APP_PASSWORD=change-me NSI_USER_KEY=<nsi-user-key> java -jar target/cheker-status-0.0.1-SNAPSHOT.jar
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
APP_SECURITY_ENABLED=true
APP_USERNAME=admin
APP_PASSWORD=
NSI_USER_KEY=
NSI_PAGE_SIZE=100
NSI_POLL_FIXED_DELAY_MS=3600000
NSI_POLL_INITIAL_DELAY_MS=60000
NSI_TRUST_ALL_SSL=false
NSI_SYNC_ON_STARTUP=true
GITLAB_ENABLED=true
GITLAB_REQUIRE_TOKEN=true
GITLAB_BASE_URL=https://git.minzdrav.gov.ru
GITLAB_TOKEN=
GITLAB_PER_PAGE=100
GITLAB_MAX_PAGES=1000
GITLAB_MAX_DOCUMENTS_PER_RUN=0
GITLAB_FILE_HISTORY_ENABLED=true
GITLAB_FETCH_FILE_CONTENT=true
GITLAB_MAX_FILES_PER_COMMIT=1000
GITLAB_MAX_FILE_SIZE_BYTES=1048576
GITLAB_RETRY_COUNT=3
GITLAB_RETRY_DELAY_MS=1000
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

File history loading is controlled separately:

- `GITLAB_FILE_HISTORY_ENABLED=false` keeps commit loading enabled but skips commit file history.
- `GITLAB_FETCH_FILE_CONTENT=false` stores only file metadata and diff, without raw file content.
- `GITLAB_MAX_FILE_SIZE_BYTES` limits raw content stored in PostgreSQL.
- `GITLAB_RETRY_COUNT` and `GITLAB_RETRY_DELAY_MS` control retry/backoff for GitLab `429` and `5xx` responses.

## Useful SQL

Find documents with Git links:

```sql
SELECT document_oid, document_name, project_path, tree_ref, last_sync_status
FROM nsi_document_git_link
ORDER BY document_oid;
```

Show file changes for one document:

```sql
SELECT l.document_oid,
       c.commit_id,
       f.change_type,
       f.old_path,
       f.new_path,
       f.content_fetch_status,
       f.committed_date
FROM nsi_document_git_commit_file f
JOIN nsi_document_git_link l ON l.id = f.git_link_id
JOIN nsi_document_git_commit c ON c.id = f.commit_id
WHERE l.document_oid = '1.2.643.5.1.13.13.15.100.1'
ORDER BY f.committed_date, f.id;
```

Show full history for one file path:

```sql
SELECT f.commit_sha,
       f.parent_sha,
       f.change_type,
       f.old_path,
       f.new_path,
       f.content_before_sha256,
       f.content_after_sha256,
       f.content_fetch_status,
       f.content_fetch_error
FROM nsi_document_git_commit_file f
WHERE f.file_path = 'path/to/file.xml'
ORDER BY f.committed_date, f.id;
```

Show current file state:

```sql
SELECT l.document_oid,
       s.file_path,
       s.last_commit_sha,
       s.content_sha256,
       s.content_size,
       s.deleted,
       s.updated_at
FROM nsi_document_git_file_state s
JOIN nsi_document_git_link l ON l.id = s.git_link_id
WHERE l.document_oid = '1.2.643.5.1.13.13.15.100.1'
ORDER BY s.file_path;
```
