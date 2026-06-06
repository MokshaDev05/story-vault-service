# story-vault-service

Spring Boot REST API for a private personal story archive. Track, organise, and back up downloaded stories with notes, download history, and file storage. Metadata can be shared publicly; uploaded files and personal notes are never exposed.

Each user account has an isolated vault — stories, notes, and files are never visible across accounts.

A companion Chrome extension (`story-vault-extension`) lets you save any story page to the vault in one click without leaving the browser.

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

**2. Build and run the service**

```bash
cd story-vault-service
mvn spring-boot:run
```

Wait for:

```
Started StoryVaultApplication in 2.x seconds
```

The API is now live at `http://localhost:8080`. Flyway creates all tables automatically on first startup — no manual SQL needed.

A `demo` user (password: `demo123`) is created automatically on first boot and adopts any pre-existing stories.

**3. Open the web UI**

Navigate to `http://localhost:8080` in your browser.

- If you are a new user, click **Create an account** on the login screen.
- If you already have an account (or want to use the demo account), enter your credentials and click **Enter the Vault**.

**4. (Optional) Install the Chrome extension**

See `story-vault-extension/README.md` for browser install steps. Once installed, you can save any story page to the vault directly from Chrome. The extension must be logged in separately — sign in from the extension popup using the same credentials.

---

## Web UI

The built-in UI is served at `http://localhost:8080` from static files bundled with the service. No separate install is needed.

### Login screen

- **Sign in:** enter your username and password, then click **Enter the Vault**.
- **Create an account:** click **Create an account** below the sign-in form. Enter a username (min. 3 characters) and a password (min. 6 characters), confirm the password, and click **Create Account**. On success you are logged in automatically.
- **Switching between modes:** click the inline link to toggle between sign-in and create-account views.

### Library

Once signed in you see your vault:

| Control | What it does |
|---------|-------------|
| Search bar | Filters cards by title, author, or fandom as you type (no API call) |
| Platform dropdown | Narrows results to one platform |
| Status dropdown | Narrows results to one status |
| ↺ Refresh | Re-fetches from the API |
| + Add Story | Opens a form to save a new story |
| Story card | Click to open a full detail view |
| ◑ (top right) | Toggles dark / light mode (persisted) |
| Sign out | Clears the token and returns to login |

### Error messages

| Message | Meaning |
|---------|---------|
| Incorrect username or password. | Wrong credentials on login |
| That username is already taken. | Duplicate username on registration |
| Passwords do not match. | Confirm-password field mismatch |
| Username must be at least 3 characters. | Validation (client-side) |
| Password must be at least 6 characters. | Validation (client-side) |
| Could not reach StoryVault… | Service is not running |

---

## Authentication

All story, note, file, and download endpoints require a JWT bearer token. Auth endpoints and the web UI static files are public.

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

Pass it as a `Bearer` header on every protected request:

```bash
TOKEN="eyJhbGci…"

curl -s http://localhost:8080/api/v1/stories \
  -H "Authorization: Bearer $TOKEN" | jq
```

Tokens are valid for 24 hours. A 401 response means the token is missing, expired, or invalid.

### Demo account

On first boot, a `demo` user is created with password `demo123`. Any stories that existed before auth was introduced are automatically assigned to this account.

---

## End-to-End Flow

```
Browser (story page)
    └── story-vault-extension popup  ← or: the built-in web UI at localhost:8080
            │  POST /api/v1/auth/login  →  JWT token
            │  POST /api/v1/stories (Bearer token)  →  story saved, ID returned
            │  POST /api/v1/stories/{id}/notes (if provided)
            ▼
        StoryController / NoteController / AuthController
            └── StoryServiceImpl / NoteServiceImpl / AuthServiceImpl
                    └── Spring Data JPA
                            └── PostgreSQL (storyvaultdb, port 5434)
                                    ├── users
                                    ├── stories     (user_id FK)
                                    ├── notes
                                    └── tags / story_files / download_records
```

Everything stays on your local machine. Nothing is sent to any external server.

---

## Architecture

```
HTTP Request
    └── JwtAuthFilter               (validates Bearer token before hitting controllers)
    └── AuthController              (register / login — /api/v1/auth/**)
    └── StoryController             (CRUD + search — /api/v1/stories/**)
    └── NoteController              (personal notes — /api/v1/stories/{id}/notes)
    └── DownloadController          (download history — /api/v1/stories/{id}/downloads)
    └── StoryFileController         (private file upload/download/delete — /api/v1/stories/{id}/file)
    └── PublicController            (metadata only, no auth — /api/v1/public/stories/{id})
            └── AuthService / StoryService / NoteService / DownloadService / StoryFileService
                    └── *ServiceImpl
                            └── Repositories  (Spring Data JPA)
                                    └── HikariCP pool
                                            └── PostgreSQL
                                                  ├── users
                                                  ├── stories
                                                  ├── tags
                                                  ├── story_tags   (join table)
                                                  ├── notes
                                                  ├── download_records
                                                  └── story_files  (BYTEA — inline binary)
```

Schema is managed exclusively by Flyway. Hibernate is set to `ddl-auto=validate` and will refuse to start if the entity does not match the database.

---

## Tech Stack

| Component | Library |
|-----------|---------|
| Framework | Spring Boot 3.2.5 |
| Language | Java 17+ |
| Persistence | Spring Data JPA + PostgreSQL |
| Migrations | Flyway |
| Connection pool | HikariCP |
| Validation | Jakarta Bean Validation |
| Security | Spring Security + JWT (jjwt 0.12.6) + BCrypt |
| Boilerplate | Lombok 1.18.38 |
| Observability | Spring Boot Actuator |
| Build | Maven 3.8+ |

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker (for PostgreSQL)

---

## Database Migrations

Migrations live in `src/main/resources/db/migration/`.

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__create_stories_table.sql` | Creates `stories` table with indexes |
| V2 | `V2__create_tags_table.sql` | Creates `tags` table |
| V3 | `V3__create_story_tags_table.sql` | Creates `story_tags` join table |
| V4 | `V4__create_notes_table.sql` | Creates `notes` table |
| V5 | `V5__create_download_records_table.sql` | Creates `download_records` table |
| V6 | `V6__create_story_files_table.sql` | Creates `story_files` table with BYTEA column |
| V7 | `V7__add_unique_index_title_author.sql` | Global title+author uniqueness index (dropped by V10) |
| V8 | `V8__create_users_table.sql` | Creates `users` table |
| V9 | `V9__add_user_id_to_stories.sql` | Adds `user_id` FK to `stories` |
| V10 | `V10__update_unique_index_per_user.sql` | Per-user title+author unique index (replaces V7) |

---

## Entities

| Entity | Key fields |
|--------|-----------|
| `User` | username (unique), password (BCrypt) |
| `Story` | title, author, fandom, platform, status, rating, summary, originalUrl, wordCount, chapterCount, tags, user |
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

---

## API Reference

### Auth endpoints (no token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/auth/register` | 201 | Register a new user — returns JWT |
| `POST` | `/api/v1/auth/login` | 200 | Login — returns JWT |

**Register request body:**

```json
{ "username": "myuser", "password": "mypassword" }
```

Constraints: username 3–100 chars, password min. 6 chars.

### Story endpoints (Bearer token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/stories` | 201 | Create a story |
| `GET` | `/api/v1/stories` | 200 | Get all stories for the authenticated user |
| `GET` | `/api/v1/stories/{id}` | 200 | Get story by ID (must belong to user) |
| `PUT` | `/api/v1/stories/{id}` | 200 | Update a story |
| `DELETE` | `/api/v1/stories/{id}` | 200 | Delete a story (cascades to notes, files, download records) |
| `GET` | `/api/v1/stories/search` | 200 | Search within the user's vault — all params optional |

Search query params: `fandom`, `platform`, `status`, `rating`, `tag` — all optional and combinable.

### Note endpoints (Bearer token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/stories/{id}/notes` | 201 | Add a personal note |
| `GET` | `/api/v1/stories/{id}/notes` | 200 | Get all notes for a story |

### Download history endpoints (Bearer token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/stories/{id}/downloads` | 201 | Log a download |
| `GET` | `/api/v1/stories/{id}/downloads` | 200 | Get download history |

### File endpoints (Bearer token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `POST` | `/api/v1/stories/{id}/file` | 201 | Upload private file (multipart/form-data, field name: `file`) |
| `GET` | `/api/v1/stories/{id}/file` | 200 | Download private file |
| `DELETE` | `/api/v1/stories/{id}/file` | 200 | Delete private file |

Max file size: **50 MB**. One file per story — delete before replacing.

### Public endpoint (no token required)

| Method | Path | Status | Description |
|--------|------|--------|-------------|
| `GET` | `/api/v1/public/stories/{id}` | 200 | Metadata only — safe for sharing |

Returns: title, author, fandom, platform, status, rating, summary, originalUrl, wordCount, chapterCount, tags.

Does **not** return: notes, download records, `hasFile`, `createdAt`, `updatedAt`, or user information.

### HTTP status codes

| Status | Scenario |
|--------|----------|
| `200 OK` | Successful GET, PUT, DELETE |
| `201 Created` | Successful POST |
| `400 Bad Request` | Validation failure |
| `401 Unauthorized` | Missing, expired, or invalid token; wrong credentials on login |
| `404 Not Found` | Story or file not found (or belongs to a different user) |
| `409 Conflict` | Duplicate story; file already exists; username taken |
| `413 Payload Too Large` | File exceeds 50 MB |
| `500 Internal Server Error` | Unexpected error |

---

## curl Examples

All story/note/file examples require a token. Get one first:

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

### Get all stories

```bash
curl -s http://localhost:8080/api/v1/stories \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Search

```bash
# By platform and status
curl -s "http://localhost:8080/api/v1/stories/search?platform=AO3&status=COMPLETE" \
  -H "Authorization: Bearer $TOKEN" | jq

# By tag (partial match, case-insensitive)
curl -s "http://localhost:8080/api/v1/stories/search?tag=magic" \
  -H "Authorization: Bearer $TOKEN" | jq

# By fandom (partial match)
curl -s "http://localhost:8080/api/v1/stories/search?fandom=raven" \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Add a note

```bash
curl -s -X POST http://localhost:8080/api/v1/stories/1/notes \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"content": "Chapter 7 is a masterpiece."}' | jq
```

### Upload a file

```bash
curl -s -X POST http://localhost:8080/api/v1/stories/1/file \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/story.epub" | jq
```

### Download a file

```bash
curl -s http://localhost:8080/api/v1/stories/1/file \
  -H "Authorization: Bearer $TOKEN" \
  -o story.epub
```

### Delete a story

```bash
curl -s -X DELETE http://localhost:8080/api/v1/stories/1 \
  -H "Authorization: Bearer $TOKEN" | jq
```

### Public metadata view (no token needed)

```bash
curl -s http://localhost:8080/api/v1/public/stories/1 | jq
```

### Verify data in PostgreSQL directly

```bash
docker exec -it storyvaultdb psql -U postgres -d storyvaultdb
```

```sql
SELECT id, username FROM users;
SELECT id, title, author, status, user_id FROM stories ORDER BY id;
SELECT * FROM tags;
SELECT id, story_id, content FROM notes;
SELECT version, description, installed_on FROM flyway_schema_history;
\q
```

---

## Actuator

```bash
curl -s http://localhost:8080/actuator/health | jq
curl -s http://localhost:8080/actuator/info | jq
```

---

## Duplicate detection

StoryVault considers a story a duplicate when **both** the title and the author match an existing entry for the same user, case-insensitively. The URL is not used as the duplicate key.

A duplicate returns `409 Conflict` with the existing story in the response body so the caller can surface the existing record ID.

Duplicates are per-user: two different users can save the same title/author pair.

---

## Lessons Learned

**`@Lob` on `byte[]` maps to PostgreSQL OID, not BYTEA.** Hibernate's `@Lob` annotation uses PostgreSQL's large-object (OID) system. If the Flyway migration creates the column as `BYTEA`, `ddl-auto=validate` refuses to start with a schema mismatch. Omit `@Lob` — plain `byte[]` maps to `BYTEA` correctly.

**Lombok requires an explicit `annotationProcessorPaths` entry.** Maven's compiler plugin will not pick up Lombok annotation processing from the regular `<dependencies>` block alone. It must be declared separately under `<annotationProcessorPaths>` in the `maven-compiler-plugin` configuration.

**Circular dependency with `AuthenticationManager`.** `SecurityConfig` needs `AuthService` as `UserDetailsService`; `AuthServiceImpl` needs `AuthenticationManager` from `SecurityConfig`. Breaking it with `@Lazy` on the `AuthenticationManager` injection in `AuthServiceImpl` resolves the cycle. `PasswordEncoder` is isolated in a separate `AppConfig` bean to avoid a second cycle.

**`ON DELETE CASCADE` keeps orphaned data from accumulating.** All child tables reference `stories(id)` with `ON DELETE CASCADE`. Deleting a story cleans up its notes, files, tags, and download records automatically.

**Per-user duplicate detection needs a partial unique index.** A global `(lower(title), lower(author))` index would block different users from saving the same work. The correct constraint is `(lower(title), lower(author), user_id) WHERE user_id IS NOT NULL`, which enforces uniqueness only within a single user's vault.

**Search uses JPA `Specification` for composable optional filters.** Each parameter is independently optional and combined with `AND`, so any combination works without a separate query for each case.

**`findAllWithTags()` avoids N+1 on the list endpoint.** Without `JOIN FETCH`, accessing tags on each story triggers one extra query per story. The custom JPQL query fetches everything in one round trip.
