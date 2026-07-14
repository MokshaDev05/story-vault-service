# story-vault-service

Spring Boot REST API for a private personal story archive. Track, organise, and back up downloaded stories with notes, download history, and file storage. Metadata can be shared publicly; uploaded files and personal notes are never exposed.

Each user account has an isolated vault — stories, notes, and files are never visible across accounts.

A companion Chrome extension (`story-vault-extension`) auto-tracks AO3 reading: every time you open an AO3 work page, the extension records which chapter you read and when, with no clicks required. It also saves new stories to the vault automatically on first visit.

---

## Quick Start

**1. Start the database (one-time setup)**

```bash
docker run --name storyvaultdb \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=storyvaultdb \
  -p 5434:5432 \
  -d postgres:16-alpine
```

On subsequent runs, just start the existing container:

```bash
docker start storyvaultdb
```

**2. Set required environment variables**

`JWT_SECRET` is required — the app will refuse to start without it:

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
```

**3. Build and run the service**

```bash
cd story-vault-service
mvn spring-boot:run
```

Wait for:

```
Started StoryVaultApplication in 2.x seconds
```

The API is live at `http://localhost:8080`. Flyway creates all tables automatically on first startup — no manual SQL needed.

A `demo` user (password: `demo123`) is created automatically on first boot and adopts any pre-existing stories.

**4. Open the web UI**

Navigate to `http://localhost:8080` in your browser.

- New user: click **Create an account** on the login screen.
- Existing user: enter your credentials and click **Enter the Vault**.

**5. (Optional) Install the Chrome extension**

See `story-vault-extension/README.md` for install steps. Once installed and signed in, every AO3 work page you open is tracked automatically — no clicks needed.

---

## Web UI

The built-in UI is served at `http://localhost:8080` from static files bundled with the service.

### Library

| Control | What it does |
|---------|-------------|
| Search bar | Text search across title, author, fandom, tags, ships — no API call |
| Platform dropdown | Narrows results to one platform |
| Status dropdown | Narrows results to one official status |
| ↺ Refresh | Re-fetches from the API and clears all active filters |
| + Add Story | Opens a form to save a new story manually |
| ⊞ Advanced | Expands the advanced search panel (POST /search) |
| Story card | Click anywhere on the card to open the full detail view |
| ◑ (top right) | Toggles dark / light mode (persisted per browser) |
| Sign out | Clears the token and returns to login |

### Clickable filter elements

Every fandom label, author name, ship, and tag is a click-to-filter button — on both cards and in the detail modal.

| Element | What clicking it does |
|---------|----------------------|
| Fandom (card or detail) | Filters to stories in that exact fandom |
| Author (card or detail) | Filters to stories by that exact author |
| Ship (card or detail) | Filters to stories with that exact relationship |
| Tag (card or detail) | Filters to stories with that exact freeform tag |

Active filters appear as **chips** in a bar below the search controls, each with an × to remove it and a **Clear all** button to reset all at once. Clicking a filter from within the detail modal closes the modal and applies the filter immediately.

### Story card

Each card shows:
- Fandom (clickable)
- Reading status chip + official status chip
- Ships/relationships (first two, each clickable)
- Title
- Author (clickable)
- Platform badge
- Freeform tags (first five, each clickable)
- ◎ if a private file is attached
- ↗ link — opens the latest known chapter if Continue Reading data exists, otherwise the canonical story URL

### Detail modal

Clicking a card opens a full detail view containing:

- Fandom (clickable), status chips, title, author (clickable)
- Summary
- Original URL
- Metadata table: reading status, current chapter, last/first accessed, times read, rating, word count, chapters (published/total), language, AO3 published date, AO3 updated date, completed date, vault added/updated dates
- Tag sections: Categories, Archive Warnings, Ships (each clickable), Characters, Freeform tags (each clickable)
- ↗ Continue Reading button (opens latest chapter URL, or canonical URL)
- Reading history timeline (all access events, newest-first; empty state if none recorded)
- Edit and Delete buttons

### Advanced search

Click **⊞ Advanced** to expand a panel with full field-specific search:

| Field | Endpoint parameter |
|-------|-------------------|
| Author contains | `authorContains` |
| Title contains | `titleContains` |
| Fandom contains | `fandomContains` |
| Tag contains | `tagContains` |
| Ship contains | `relationshipContains` |
| Character contains | `characterContains` |
| Rating | `rating` |
| Reading status | `readingStatus` |
| Language | `language` |
| Word count range | `minWordCount` / `maxWordCount` |
| Chapter count range | `minChapters` / `maxChapters` |
| AO3 published/updated date ranges | `publishedAfter` … `updatedBefore` |
| Last/first accessed date ranges | `lastAccessedAfter` … `firstAccessedBefore` |
| Min times read | `minAccessCount` |
| Chapter accessed | `chapterAccessed` |
| Sort by / direction | `sortBy` / `sortDir` |

Results show as chips summarising active filters. **✕ Clear all filters** exits advanced mode and returns to the full library.

---

## Auto-Tracking

When the Chrome extension is installed and signed in, reading activity is logged automatically:

1. You open any `archiveofourown.org/works/{id}` page.
2. The content script detects the work ID and reading mode:
   - **CHAPTER** — URL contains `/chapters/{id}`; exact chapter number and title are parsed from the chapter dropdown
   - **WORK_MAIN** — root work URL with no chapter segment; chapter number is set to 1 only if the work is confirmed single-chapter
   - **FULL_WORK** — URL contains `view_full_work=true`; chapter number is not set
3. A `POST /api/v1/stories/upsert` call saves or updates the story (merging AO3 metadata without overwriting your notes).
4. A `POST /api/v1/stories/{id}/access` call logs the visit with chapter, title, URL, and `eventType: PAGE_LOAD`.
5. A brief toast notification (◆ Saved / ◆ Updated) confirms the action.

**Deduplication:** visits to the same chapter within 5 minutes are silently skipped — refreshing a page does not create duplicate history entries.

**Continue Reading:** each upsert advances `currentChapterUrl` forward (never backward), so the ↗ link in the UI always points to the furthest chapter you have reached. Stories with reading status `FINISHED_READING`, `DNF`, or `ON_HOLD` are never auto-advanced.

---

## Authentication

All story, note, file, and history endpoints require a JWT bearer token. Auth endpoints and the web UI static files are public.

### Get a token

**Register:**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"myuser","password":"mypassword"}' | jq
```

**Login:**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"myuser","password":"mypassword"}' | jq
```

Both return:

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGci…",
    "username": "myuser"
  }
}
```

### Use the token

```bash
TOKEN="eyJhbGci…"

curl -s http://localhost:8080/api/v1/stories \
  -H "Authorization: Bearer $TOKEN" | jq
```

Tokens are valid for 30 days. A 401 response means the token is missing, expired, or invalid.

### Demo account

On first boot a `demo` user is created with password `demo123`. Any stories that existed before auth was introduced are automatically assigned to this account.

---

## Architecture

```
HTTP Request
    └── JwtAuthFilter                  (validates Bearer token)
    └── AuthController                 (register / login — /api/v1/auth/**)
    └── StoryController                (CRUD + upsert + search — /api/v1/stories/**)
    └── ReadingHistoryController       (access log — /api/v1/stories/{id}/access)
    └── NoteController                 (personal notes — /api/v1/stories/{id}/notes)
    └── DownloadController             (download history — /api/v1/stories/{id}/downloads)
    └── StoryFileController            (private file upload/download — /api/v1/stories/{id}/file)
    └── PublicController               (authenticated — /api/v1/public/stories/{id})
            └── *ServiceImpl
                    └── Repositories  (Spring Data JPA + Specifications)
                            └── HikariCP pool
                                    └── PostgreSQL
                                          ├── users
                                          ├── stories
                                          ├── reading_history
                                          ├── tags / story_tags
                                          ├── story_relationships / story_characters
                                          ├── story_warnings / story_categories
                                          ├── notes
                                          ├── download_records
                                          └── story_files  (BYTEA)
```

Schema managed exclusively by Flyway. Hibernate is set to `ddl-auto=validate`.

---

## Tech Stack

| Component | Library |
|-----------|---------|
| Framework | Spring Boot 3.2.5 |
| Language | Java 17+ (tested on Java 25) |
| Persistence | Spring Data JPA + PostgreSQL |
| Migrations | Flyway |
| Connection pool | HikariCP |
| Validation | Jakarta Bean Validation |
| Security | Spring Security + JWT (jjwt 0.12.6) + BCrypt |
| Boilerplate | Lombok 1.18.38 |
| Observability | Spring Boot Actuator |
| Build | Maven 3.8+ |
| Test — unit | JUnit 5 + Mockito 5 + AssertJ |
| Test — integration | Spring Boot Test + H2 in-memory |

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker (for PostgreSQL)

---

## Running Tests

Tests use H2 in-memory and do not require PostgreSQL or the Docker container to be running.

```bash
mvn test
```

36 tests across four classes:

| Class | Tests | What it covers |
|-------|-------|----------------|
| `StoryVaultApplicationTests` | 1 | Context loads with H2 |
| `ReadingHistoryServiceTest` | 21 | Dedup logic, field mapping, `listByStory` |
| `StoryServiceTest` | 6 | `findById` response, `advanceReadingProgress` |
| `StorySearchIntegrationTest` | 8 | Advanced search filtering with real H2 queries |

---

## Database Migrations

Migrations live in `src/main/resources/db/migration/`.

| Version | Description |
|---------|-------------|
| V1 | `stories` table |
| V2 | `tags` table |
| V3 | `story_tags` join table |
| V4 | `notes` table |
| V5 | `download_records` table |
| V6 | `story_files` table (BYTEA) |
| V7 | Global title+author uniqueness index (replaced by V10) |
| V8 | `users` table |
| V9 | `user_id` FK on `stories` |
| V10 | Per-user title+author unique index |
| V11 | `reading_status`, `current_chapter`, `current_chapter_url`, `last_accessed_at` on `stories` |
| V12 | `reading_history` table |
| V13 | Rating enum rename (`GENERAL_AUDIENCES` → `GENERAL`, etc.) |
| V14 | `source_work_id` on `stories` |
| V15 | `story_relationships`, `story_characters`, `story_warnings`, `story_categories` element-collection tables |
| V16 | AO3 metadata fields on `stories` (`ao3_published_date`, `ao3_updated_date`, `language`, `total_chapters`, `word_count`, `chapter_count`, `completed_at`) |
| V17 | AO3 fields on `reading_history` (`chapter_ao3_id`, `reading_mode`, `chapter_url`, `chapter_title`) |
| V18 | Event fields on `reading_history` (`user_id`, `work_id`, `event_type`); backfills `PAGE_LOAD` |

---

## Entities

| Entity | Key fields |
|--------|-----------|
| `User` | username (unique), password (BCrypt) |
| `Story` | title, author, fandom, platform, status, rating, summary, originalUrl, sourceWorkId, wordCount, chapterCount, totalChapters, ao3PublishedDate, ao3UpdatedDate, language, relationships, characters, archiveWarnings, categories, tags, readingStatus, currentChapter, currentChapterUrl, lastAccessedAt, user |
| `ReadingHistory` | story, userId, workId, chapterNumber, chapterTitle, chapterUrl, chapterAo3Id, readingMode, sourcePlatform, eventType, accessedAt |
| `Tag` | name (unique, lowercase) — many-to-many with Story |
| `Note` | content, createdAt — private, never exposed publicly |
| `DownloadRecord` | source, notes, downloadedAt — private |
| `StoryFile` | filename, contentType, fileSize, fileData (BYTEA) — one per story, private |

### Enums

| Field | Values |
|-------|--------|
| `platform` | `AO3`, `FFN`, `WATTPAD`, `OTHER` |
| `status` | `ONGOING`, `COMPLETE`, `HIATUS`, `ABANDONED` |
| `rating` | `GENERAL`, `TEEN`, `MATURE`, `EXPLICIT`, `NOT_RATED` |
| `readingStatus` | `WANT_TO_READ`, `STILL_READING`, `CAUGHT_UP`, `FINISHED_READING`, `ON_HOLD`, `DNF`, `REREADING` |
| `readingMode` | `CHAPTER`, `WORK_MAIN`, `FULL_WORK` |
| `eventType` | `PAGE_LOAD` (auto-tracked), `MANUAL` (future) |

---

## API Reference

### Auth (no token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/auth/register` | 201 | Register — returns JWT |
| `POST` | `/api/v1/auth/login` | 200 | Login — returns JWT |

### Stories (Bearer token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/stories` | 201 | Create a story |
| `POST` | `/api/v1/stories/upsert` | 201/200 | Idempotent create-or-merge (used by extension) |
| `GET` | `/api/v1/stories` | 200 | List all stories for the authenticated user |
| `GET` | `/api/v1/stories/{id}` | 200 | Get one story (must belong to user) |
| `PUT` | `/api/v1/stories/{id}` | 200 | Full update |
| `DELETE` | `/api/v1/stories/{id}` | 200 | Delete (cascades to all child records) |
| `GET` | `/api/v1/stories/search` | 200 | Simple search — query params: `fandom`, `platform`, `status`, `rating`, `tag` |
| `POST` | `/api/v1/stories/search` | 200 | Advanced search — full field-specific filtering and sorting |

**Upsert behaviour:** if the story already exists (matched by AO3 work ID, canonical URL, or title+author), AO3 metadata is merged without touching your notes or reading status. Returns 201 when a new story is created, 200 on update.

**`advanceReadingProgress` rules:**
- Reading status auto-advances: `null`/`WANT_TO_READ` → `STILL_READING` on first visit; `STILL_READING` → `CAUGHT_UP` when current chapter ≥ published chapter count; → `FINISHED_READING` if the work is also COMPLETE.
- `currentChapter` and `currentChapterUrl` only advance **forward** (higher chapter number). Rereading is the exception — any chapter is accepted.
- Stories with status `FINISHED_READING`, `DNF`, or `ON_HOLD` are never auto-modified.

### Reading history (Bearer token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/stories/{id}/access` | 200 | Log an access event |
| `GET` | `/api/v1/stories/{id}/access` | 200 | List all access events, newest-first |

**Access request body:**

```json
{
  "chapterNumber": 7,
  "chapterTitle": "Into the Storm",
  "chapterUrl": "https://archiveofourown.org/works/123/chapters/456",
  "sourcePlatform": "AO3",
  "chapterAo3Id": "456",
  "readingMode": "CHAPTER",
  "eventType": "PAGE_LOAD"
}
```

All fields optional. `eventType` defaults to `PAGE_LOAD` if omitted.

**Deduplication:** a visit is skipped when the most recent history entry matches the same chapter (by `chapterAo3Id` when available, otherwise `chapterNumber`) and was recorded less than 5 minutes ago.

### Notes (Bearer token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/stories/{id}/notes` | 201 | Add a personal note |
| `GET` | `/api/v1/stories/{id}/notes` | 200 | Get all notes for a story |

### Download history (Bearer token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/stories/{id}/downloads` | 201 | Log a download |
| `GET` | `/api/v1/stories/{id}/downloads` | 200 | Get download history |

### File storage (Bearer token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/stories/{id}/file` | 201 | Upload (multipart/form-data, field: `file`) |
| `GET` | `/api/v1/stories/{id}/file` | 200 | Download |
| `DELETE` | `/api/v1/stories/{id}/file` | 200 | Delete |

Max file size: **50 MB**. One file per story — delete before replacing.

### Authenticated endpoints (token required for all `/api/**` paths)

#### Story metadata (public view)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `GET` | `/api/v1/public/stories/{id}` | 200 | Safe metadata — no notes, files, or user info. Requires a valid JWT. |

### HTTP status codes

| Status | Scenario |
|--------|----------|
| `200 OK` | Successful GET, PUT, DELETE, or POST /access |
| `201 Created` | Successful POST (create, upsert-new, note, download, file) |
| `400 Bad Request` | Validation failure |
| `401 Unauthorized` | Missing, expired, or invalid token |
| `404 Not Found` | Story or file not found (or belongs to a different user) |
| `409 Conflict` | Duplicate story; file already exists; username taken |
| `413 Payload Too Large` | File exceeds 50 MB |
| `500 Internal Server Error` | Unexpected error |

---

## curl Examples

Get a token first:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo123"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
```

### Create a story

```bash
curl -s -X POST http://localhost:8080/api/v1/stories \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "The Raven Cycle",
    "author": "Maggie Stiefvater",
    "fandom": "The Raven Cycle",
    "platform": "AO3",
    "status": "COMPLETE",
    "rating": "TEEN",
    "summary": "A retelling of the search for Glendower.",
    "originalUrl": "https://archiveofourown.org/works/example",
    "wordCount": 45000,
    "chapterCount": 12,
    "tags": ["magic", "found-family", "slow-burn"]
  }' | jq
```

### Upsert (create or merge)

```bash
curl -s -X POST http://localhost:8080/api/v1/stories/upsert \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "The Raven Cycle",
    "author": "Maggie Stiefvater",
    "fandom": "The Raven Cycle",
    "platform": "AO3",
    "sourceWorkId": "123456",
    "chapterCount": 14,
    "currentChapter": 7,
    "currentChapterUrl": "https://archiveofourown.org/works/123456/chapters/789"
  }' | jq
```

Returns 201 if new, 200 if merged.

### Log a reading access event

```bash
curl -s -X POST http://localhost:8080/api/v1/stories/1/access \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "chapterNumber": 7,
    "chapterTitle": "Into the Storm",
    "chapterUrl": "https://archiveofourown.org/works/123456/chapters/789",
    "sourcePlatform": "AO3",
    "chapterAo3Id": "789",
    "readingMode": "CHAPTER",
    "eventType": "PAGE_LOAD"
  }' | jq
```

### Get reading history for a story

```bash
curl -s http://localhost:8080/api/v1/stories/1/access \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Advanced search

```bash
curl -s -X POST http://localhost:8080/api/v1/stories/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "authorContains": "rowling",
    "fandomContains": "harry potter",
    "tagContains": "slow burn",
    "relationshipContains": "harry/hermione",
    "minWordCount": 50000,
    "sortBy": "LAST_ACCESSED",
    "sortDir": "desc"
  }' | jq
```

### Simple search (GET)

```bash
# By fandom and status
curl -s "http://localhost:8080/api/v1/stories/search?fandom=raven&status=COMPLETE" \
  -H "Authorization: Bearer $TOKEN" | jq

# By tag
curl -s "http://localhost:8080/api/v1/stories/search?tag=slow-burn" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Add a note

```bash
curl -s -X POST http://localhost:8080/api/v1/stories/1/notes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"content": "Chapter 7 is a masterpiece."}' | jq
```

### Upload / download a file

```bash
curl -s -X POST http://localhost:8080/api/v1/stories/1/file \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/story.epub" | jq

curl -s http://localhost:8080/api/v1/stories/1/file \
  -H "Authorization: Bearer $TOKEN" \
  -o story.epub
```

### Verify data in PostgreSQL

```bash
docker exec -it storyvaultdb psql -U postgres -d storyvaultdb
```

```sql
SELECT id, title, reading_status, current_chapter, last_accessed_at FROM stories ORDER BY last_accessed_at DESC NULLS LAST;
SELECT id, story_id, chapter_number, chapter_title, reading_mode, event_type, accessed_at FROM reading_history ORDER BY accessed_at DESC LIMIT 20;
SELECT version, description, installed_on FROM flyway_schema_history;
\q
```

---

## Actuator

```bash
curl -s http://localhost:8080/actuator/health | jq
```

---

## Duplicate Detection

StoryVault considers a story a duplicate when it finds a match on any of these signals (strongest first):

1. AO3 work ID (`sourceWorkId`) — exact match per user
2. Canonical URL — normalised (query strings and trailing slashes stripped)
3. Title + author — case-insensitive, per user

A `POST /api/v1/stories` duplicate returns `409 Conflict` with the existing story in the response body. `POST /api/v1/stories/upsert` never returns a conflict — it merges metadata into the existing record instead.

---

## Lessons Learned

**`@Lob` on `byte[]` maps to PostgreSQL OID, not BYTEA.** Hibernate's `@Lob` annotation uses PostgreSQL's large-object (OID) system. If the Flyway migration creates the column as `BYTEA`, `ddl-auto=validate` refuses to start with a schema mismatch. Omit `@Lob` — plain `byte[]` maps to `BYTEA` correctly.

**Lombok requires an explicit `annotationProcessorPaths` entry.** Maven's compiler plugin will not pick up Lombok annotation processing from the regular `<dependencies>` block alone.

**Circular dependency with `AuthenticationManager`.** `SecurityConfig` needs `AuthService` as `UserDetailsService`; `AuthServiceImpl` needs `AuthenticationManager` from `SecurityConfig`. Breaking it with `@Lazy` on the `AuthenticationManager` injection resolves the cycle.

**Per-user duplicate detection needs a partial unique index.** A global `(lower(title), lower(author))` index blocks different users from saving the same work. The correct constraint is per-user.

**Search uses JPA `Specification` for composable optional filters.** Any combination of fields works without a separate query method for each case.

**`findAllWithTags()` avoids N+1 on the list endpoint.** Without `JOIN FETCH`, accessing tags on each story triggers one extra query per story.

**Mockito inline mock maker cannot instrument `java.lang.Object` on Java 21+.** The inline mock maker needs to retransform `Object`, which newer JVMs block even with `--add-opens`. The fix is to switch to the subclass mock maker via `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` containing `mock-maker-subclass`.

**H2 for tests eliminates the PostgreSQL requirement.** `spring.flyway.enabled=false` + `spring.jpa.hibernate.ddl-auto=create-drop` in `src/test/resources/application.properties` gives a self-contained test environment. Integration tests that need real SQL (JPA Specifications, element-collection JOINs) use `@SpringBootTest` + `@Transactional`, which rolls back after each test.

**Chapter number from dropdown text, not index.** AO3 allows authors to delete chapters, leaving gaps in numbering. Parsing the chapter number from the option's display text (`"12. Chapter Title"` → 12) is accurate regardless of gaps; using `selectedIndex + 1` is not.
