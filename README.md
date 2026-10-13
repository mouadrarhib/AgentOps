# AgentOps

## Local PostgreSQL setup

Copy `.env.example` to `.env` in the project root and set `DB_PASSWORD` to your
local PostgreSQL password. The `.env` file is ignored by Git and is loaded by
Spring Boot when the application runs from the project root. You can also set
the same variables in your shell or IDE run configuration; environment variables
take precedence over `.env` values.

The default connection is `localhost:5432/agentops` with the `postgres` user.
Create the `agentops` database before starting the application.

`DB_DDL_AUTO=update` creates and updates the local development tables from the
JPA entities. It defaults to `none` when unset; use versioned migrations for
deployed environments.

Run with Java 21:

```powershell
.\mvnw.cmd spring-boot:run
```

Run the tests:

```powershell
.\mvnw.cmd test
```

## Docker

Start Docker Desktop with Linux containers enabled. Use the existing root `.env`,
or copy `.env.example` to `.env` and set `DB_PASSWORD`. Then run:

```powershell
docker compose up --build -d
docker compose logs -f backend
```

The backend is available at `http://localhost:8080`, with API documentation at
`http://localhost:8080/swagger-ui.html`. PostgreSQL is published on
`localhost:5433`, so your existing local server on port 5432 can keep running.
Change `BACKEND_PORT` or `POSTGRES_PORT` in `.env` if needed.

Compose creates a separate `agentops` database in a named Docker volume and waits
for PostgreSQL's health check before starting the backend. It overrides the
backend database host and internal port to `database:5432`; your local `.env`
values for `DB_HOST` and `DB_PORT` still work when running outside Docker.
Database credentials come from `.env` and are injected at runtime; `.env` is
excluded from the image build. The Java 21 image runs the app as a non-root user.

`DB_DDL_AUTO=update` supports local development. Container builds skip test
execution because the integration tests require a running database; run tests
separately before building. Initialization variables such as the database name,
username, and password apply when the Docker database volume is first created.

Stop the containers while retaining database data:

```powershell
docker compose down
```

## Data model

Entities live in `com.AgentOps.entities`; enums live in `com.AgentOps.entities.enums`.
Enum values are stored as strings. IDs use database-generated `BIGINT` values,
and timestamps use `Instant` (PostgreSQL timestamps with time zone).

- Users have unique emails, password hashes, and a `CUSTOMER`, `SUPPORT`, or `ADMIN` role.
- Orders reference a customer (a user) and have a status and creation/update timestamps.
- Tickets reference an order and a customer, with a category and status.
- Conversations reference a user; messages reference a conversation and have a message role.
- Tool audit entries reference the user who invoked the tool and record its result status.
- Approvals reference the requesting user and optionally the approving user, with an action
  type, status, creation/update timestamps, and an optional decision timestamp.

All relationships are lazy many-to-one associations without cascading deletes.
`Approval.targetId` is a polymorphic ID interpreted according to `actionType`,
so it has no single-table foreign key. The service layer must validate the target,
ensure a ticket's customer matches its order, and set approval decision details.

The enum values are initial workflow choices and can be adjusted as business rules develop.

## DTOs

Each entity has a DTO in `com.AgentOps.dto.request` and `com.AgentOps.dto.response`.
Relationships are represented by IDs. Requests validate required fields, positive
IDs, enum presence, email format, and applicable length limits. User requests
accept a plaintext password (8–128 characters), which must be hashed by the service;
passwords are write-only in JSON and user responses contain no password or hash.

Approval requests contain the requester, action type, and target ID. The service
must initialize the status to `PENDING` and manage approver and decision timestamps.
Generated IDs and timestamps appear only in responses. Controllers must use
`@Valid` on request bodies to trigger validation; authorization, referenced-record
existence, unique emails, and workflow rules still require service-layer checks.

## API errors

`GlobalExceptionHandler` uses Spring `ProblemDetail` and returns
`application/problem+json` with `title`, `status`, `detail`, `instance`
(the request path), and `timestamp`. Validation failures also include an `errors`
array of `{ "field": "email", "message": "Email must be valid" }` objects.
Rejected values are omitted, and multiple failures for the same field are preserved.
Spring omits the default `type`; its implied value is `about:blank` under RFC 9457.
Handlers return `ProblemDetail` directly, with Spring deriving the HTTP status
from it. Framework error headers are preserved explicitly. The custom advice
handles MVC errors, so Boot's separate problem-details advice is not enabled.

- `BadRequestException`: 400 for invalid business input.
- `ForbiddenException`: 403 for operations the user is not allowed to perform.
- `ResourceNotFoundException`: 404, for example `new ResourceNotFoundException("Order", orderId)`.
- `ConflictException`: 409 for conflicts such as duplicate emails or invalid state transitions.
- Database integrity and optimistic locking failures: 409 with generic details.
- Unexpected exceptions: 500 with a generic response; the exception is logged server-side.

Only put client-safe messages in custom exceptions. Spring MVC errors retain their
HTTP statuses and headers, including malformed requests (400), missing routes (404),
unsupported methods (405), and unsupported media types (415). Use `@Valid` on DTO
request bodies to activate validation. The exception MVC tests use test-only routes
with security filters disabled; authentication integration tests exercise the real filters.

## JWT authentication

Authentication follows `AuthController` → `AuthService` → `AuthServiceImpl` →
`UserRepository`. Requests use validated DTOs, and responses never expose passwords
or password hashes. Registration always assigns `CUSTOMER`; privileged roles must
be assigned through a trusted administrative process. Email addresses are stored
in lowercase and looked up without case sensitivity. Passwords use salted PBKDF2.

Set `JWT_SECRET` in your ignored `.env` file to a Base64-encoded random key of at
least 32 bytes. Generate a key before starting a fresh checkout. In PowerShell, run:

```powershell
$keyBytes = New-Object byte[] 32
$random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$random.GetBytes($keyBytes)
$random.Dispose()
[Convert]::ToBase64String($keyBytes)
```

Copy the generated value into `JWT_SECRET=...`. Optional settings are
`JWT_ISSUER=agentops` and `JWT_EXPIRATION_SECONDS=3600` (60–86400 seconds).
`REFRESH_TOKEN_EXPIRATION_SECONDS=604800` configures the refresh/session lifetime
(3600–2592000 seconds, seven days by default).
Docker Compose passes these settings to the backend. Production requires its own
secret; the fixed key in `src/test/resources/application.properties` is for tests
only and is excluded from the application JAR. CI tests use that test configuration
and do not require a JWT secret in GitHub.

| Method | Endpoint | Access | Result |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | 201, token and user |
| POST | `/api/auth/login` | Public | 200, token and user |
| GET | `/api/auth/me` | Access cookie or Bearer token | 200, current user |
| PATCH | `/api/auth/me` | Access cookie or Bearer token + current password | 200, updated user |
| POST | `/api/auth/refresh` | Refresh cookie (no body) or refresh token in JSON body | 200, replacement tokens and user |
| POST | `/api/auth/logout` | Access cookie or Bearer token | 204, current session revoked |
| GET | `/api/auth/csrf` | None | 200, CSRF token and header name |

Registration and login accept `{"email":"customer@example.com","password":"password123"}`.
The authentication response contains `accessToken`, `tokenType` (`Bearer`),
`expiresIn` (seconds), `refreshToken`, `refreshExpiresIn` (seconds), and `user`
(`id`, `email`, `role`). For example:

```powershell
$body = @{ email = 'customer@example.com'; password = 'password123' } | ConvertTo-Json
$auth = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/register -ContentType application/json -Body $body
Invoke-RestMethod -Uri http://localhost:8080/api/auth/me -Headers @{ Authorization = "Bearer $($auth.accessToken)" }
```

Spring Security validates HS256 signatures, issuer, expiry, user ID, and role.
Every other API endpoint requires authentication; Swagger documentation remains
public and its **Authorize** button accepts the token. Role claims become
`ROLE_CUSTOMER`, `ROLE_SUPPORT`, or `ROLE_ADMIN`; apply
`@PreAuthorize("hasRole('ADMIN')")` where privileged access is required.

Missing or invalid tokens and invalid credentials return 401 `ProblemDetail`;
insufficient permissions return 403, duplicate emails 409, and invalid requests
400 with field errors. Both security-filter errors and controller errors use
`application/problem+json`. No HTTP session or authentication cookie is created.
Authentication sessions are persisted in `auth_sessions`, associated with users;
each bearer request checks that its session is active and its role still matches
the account. This database lookup makes logout and account deletion effective
for existing access tokens. JWTs issued before this change lack a session ID and
require a new login.
Use HTTPS when serving the API outside localhost.

Access tokens expire after the configured lifetime and use Spring Security's
default clock-skew allowance, bounded by the session's absolute expiry.
Existing accounts need PBKDF2 hashes to log in. The new `auth_sessions` table must
exist: local `DB_DDL_AUTO=update` creates it, while deployments using `none` or
`validate` need the corresponding schema change before starting the application.

Send `{"refreshToken":"..."}` to `/api/auth/refresh` without an Authorization
header; no unexpired access token is needed. Refresh tokens use 32 random bytes;
only SHA-256 hashes are stored. Each successful refresh replaces the refresh
token, and the previous one returns 401. Save both returned tokens on the client.
Rotation is serialized with a database row lock, so concurrent attempts cannot
both consume the same token. Rotation preserves the session's original expiry;
revoked and expired tokens return the same safe 401 error. Rotation does not revoke
existing access tokens in that session; logout revokes all access and refresh
tokens belonging to the current session. Other login sessions remain active.
Logout clears the authentication cookies; password changes also clear them.
Clients using JSON tokens should discard their stored tokens after logout. Old revoked/expired
session rows are retained; periodic cleanup can be added when needed.

### Cookie authentication in Postman

Import `docs/postman-cookie-auth.json`. Set the collection variables `email` and
`password` to your registered account and keep `baseUrl=http://localhost:8080`
(or change it to your API URL). Send **Login**, then **Current user** or any other
request. Postman's cookie jar saves and sends the cookies automatically; leave
Authorization set to **No Auth**. Use the same hostname consistently, since
`localhost` and `127.0.0.1` have separate cookie jars. Keep the cookie jar enabled.
Do not share/export a collection containing your real password.

Register, login, and refresh set `AGENTOPS_ACCESS` (path `/`) and
`AGENTOPS_REFRESH` (path `/api/auth`). Both are HttpOnly, host-only, and
SameSite=Strict, with lifetimes matching their tokens. Set
`AUTH_COOKIE_SECURE=true` when serving over HTTPS; the local HTTP default is false.
The existing JSON response and Bearer authentication remain available. An explicit
Authorization header takes precedence over the access cookie.

Cookie-authenticated POST/PATCH/PUT/DELETE requests need CSRF protection. The
collection's pre-request script automatically calls `GET /api/auth/csrf`, which
sets the `XSRF-TOKEN` cookie, then sends the returned token in `X-XSRF-TOKEN`.
If adding requests to another collection, copy that pre-request script as well.
Requests using only an explicit Bearer token do not require CSRF. Refresh requests
carrying the refresh cookie still require CSRF even with an Authorization header.

When the access cookie expires, send **Refresh** (`POST /api/auth/refresh`, no
body and no Authorization header); the refresh cookie rotates both cookies.
Refresh is explicit, not automatic. **Logout** revokes the current session and
deletes both authentication cookies. Refresh and login ignore stale access cookies
so they still work after access-token expiry.

`PATCH /api/auth/me` accepts optional `email` and `password` fields, with at least
one required, plus required `currentPassword`. Omitted/null fields remain unchanged.
For example, `{"email":"new@example.com","currentPassword":"password123"}`
changes the email, and `{"password":"new-password123","currentPassword":"password123"}`
changes the password. Email updates preserve sessions, normalize the new email,
and reject duplicate addresses with 409. Password updates store a new PBKDF2 hash
and revoke **all** of the user's sessions, including the current one; log in again
with the new credentials after the successful 200 response. Roles and IDs cannot
be changed through this endpoint. Incorrect current passwords return 401.

## Orders

Orders are persisted in PostgreSQL and exposed through `OrderController`,
`OrderService`/`OrderServiceImpl`, and `OrderRepository`. Every endpoint requires
an active bearer token and returns `OrderResponse` rather than the JPA entity.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| POST | `/orders` | Create a `PENDING` order; returns 201 and a `Location` header |
| GET | `/orders?page=0&size=20` | List orders visible to the current user |
| GET | `/orders/{orderId}` | Read one visible order |

Customers create their own orders with `{}` or `{"customerId":123}` where the ID
is their own. Creating an order for someone else returns 403. SUPPORT and ADMIN
can create orders for customers by supplying `customerId`; the target must exist
and have the CUSTOMER role. Missing customers return 404 and staff accounts
cannot own orders. The creation endpoint uses `CreateOrderRequest`; order status
is assigned by the service and always starts as `PENDING`.

Customers can list and retrieve only their own orders. SUPPORT and ADMIN can
list and retrieve all orders. Inaccessible and nonexistent order IDs both return
404, so a customer cannot determine whether another customer's order exists.
Ownership filters are applied in the database before pagination and counting.

List responses contain `content`, `page`, `size`, `totalElements`, and `totalPages`.
Pages start at zero; size defaults to 20 and must be between 1 and 100. Results
are sorted by `createdAt` descending, then `id` descending. Each order contains
`id`, `customerId`, `status`, `createdAt`, and `updatedAt`. Empty lists and pages
beyond the last page return 200 with empty `content`. Invalid IDs, pagination, or
JSON return 400 `ProblemDetail`; unauthenticated requests return 401.

```powershell
$headers = @{ Authorization = "Bearer $($auth.accessToken)" }
$order = Invoke-RestMethod -Method Post -Uri http://localhost:8080/orders -Headers $headers -ContentType application/json -Body '{}'
Invoke-RestMethod -Uri http://localhost:8080/orders -Headers $headers
Invoke-RestMethod -Uri "http://localhost:8080/orders/$($order.id)" -Headers $headers
```

## Support tickets

Ticket APIs use `TicketController`, `TicketService`/`TicketServiceImpl`, and
`TicketRepository`. All endpoints require an active bearer token.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| POST | `/tickets` | Create a ticket for an authorized order; returns 201 and `Location` |
| GET | `/tickets` | List visible tickets with optional filters and pagination |
| GET | `/tickets/{ticketId}` | Read one visible ticket |
| PATCH | `/tickets/{ticketId}` | Staff update of status, priority, or assignment |

Create with `{"orderId":123,"category":"DELIVERY"}`. `orderId` must be positive
and `category` is required. The service derives the customer from the order and
sets status to `OPEN`, priority to `NORMAL`, and assignment to null. Customers
can create tickets only for their own orders; SUPPORT and ADMIN can create them
for any customer order. Missing or inaccessible orders return 404.

Customers see only their own tickets. SUPPORT and ADMIN see all tickets and can
patch them. Inaccessible and nonexistent ticket IDs both return 404. Customer
patch requests return 403. Filters are applied together with visibility in the
database before pagination and counting; filtering by another customer cannot
expose that customer's tickets.

Optional query filters are `status`, `priority`, `category`, `orderId`, `customerId`,
`assignedToId`, and `unassigned` (`true` for unassigned, `false` for assigned).
For example, `/tickets?status=OPEN&priority=HIGH&unassigned=true&page=0&size=20`.
All supplied filters are combined with AND. Supplying both an assignee and
`unassigned=true` returns 400. Paging starts at zero, defaults to 20 rows, and
allows sizes 1–100. Results are sorted by ID descending and contain `content`,
`page`, `size`, `totalElements`, and `totalPages`.

Patch examples:

```json
{"status":"IN_PROGRESS","priority":"HIGH","assignedToId":7}
```

```json
{"unassign":true}
```

Omitted/null update fields remain unchanged. At least one change is required;
assignment and `unassign=true` cannot be combined. Assignees must be existing
SUPPORT or ADMIN accounts. Ticket ownership, order, and category cannot be changed
through PATCH. Responses include `id`, `orderId`, `customerId`, `category`, `status`,
`priority`, and `assignedToId`, without exposing user entities or credentials.
Statuses are `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`; priorities are `LOW`,
`NORMAL`, `HIGH`, `URGENT`. Categories use the existing `TicketCategory` enum.

Validation errors return 400 `ProblemDetail`, missing resources return 404, and
unauthenticated requests return 401. Optimistic locking prevents concurrent ticket
updates from silently overwriting each other; conflicting updates return 409.

The ticket table now includes `priority` (default `NORMAL`), nullable
`assigned_to_id`, and `version` (default 0), plus indexes for customer, order, and
assignee. Local `DB_DDL_AUTO=update` applies these changes and defaults existing
rows. Deployments using `none` or `validate` must apply these schema changes first.

## AI agent and conversations

The Spring Boot agent uses DeepSeek's non-streaming
[Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion/)
and [tool-call protocol](https://api-docs.deepseek.com/guides/tool_calls/).
Set `DEEPSEEK_API_KEY` in the ignored `.env` file to enable live chat. The backend
and other endpoints can start without it; chat returns 503 while the key is blank.
Never put the key in client requests or commit it. Docker Compose forwards the
DeepSeek settings to the backend. Conversation text and authorized tool results
are sent to DeepSeek to generate replies.

| Setting | Default |
| --- | --- |
| `DEEPSEEK_BASE_URL` | `https://api.deepseek.com` |
| `DEEPSEEK_MODEL` | `deepseek-flash` |
| `DEEPSEEK_TIMEOUT_SECONDS` | `30` per provider request |
| `DEEPSEEK_MAX_OUTPUT_TOKENS` | `2048` |
| `AGENT_MAX_TOOL_ROUNDS` | `4` |
| `AGENT_MAX_TOOL_CALLS` | `8` total per chat turn |
| `AGENT_CONTEXT_MESSAGES` | `40` recent messages |

The model is configurable; thinking is disabled for this tool loop. Provider
requests have timeouts and are not automatically retried. API redirects are not
followed, so credentials are never forwarded to a redirect target.

| Method | Endpoint | Behavior |
| --- | --- | --- |
| POST | `/agent/chat` | Send a message and receive the AI reply |
| GET | `/agent/conversations` | Paginated list of the current user's conversations |
| GET | `/agent/conversations/{conversationId}` | Conversation metadata and paginated messages |

All routes require an active bearer token. Start a conversation with:

```json
{"message":"Where is my order 123?"}
```

Continue it using the returned ID:

```json
{"conversationId":42,"message":"Create a delivery support ticket for that order."}
```

Messages must be nonblank and at most 4000 characters. Clients cannot choose
message roles, conversation owners, provider settings, or a system prompt.
Chat returns `conversationId`, `reply` (a `MessageResponse`), and `toolCalls`.
Conversations are private to their owner, including against other ADMIN/SUPPORT
users; missing and inaccessible conversations both return 404. Conversation
lists use `content`, `page`, `size`, `totalElements`, and `totalPages`, newest
first. Details use `conversation`, `messages`, and the same pagination metadata,
with messages in chronological order. Defaults are 20 conversations or 50 messages;
pages start at zero and sizes are limited to 1–100.

The backend exposes a fixed allowlist:

| Tool | Arguments | Access |
| --- | --- | --- |
| `get_order` | `orderId` | Authorized order only |
| `list_orders` | Optional `page`, `size` | Visible orders only |
| `get_ticket` | `ticketId` | Authorized ticket only |
| `list_tickets` | Optional ticket filters, `page`, `size` | Visible tickets only |
| `create_ticket` | `orderId`, `category` | Customer's own order, or staff on behalf of a customer |
| `update_ticket` | `ticketId` plus status, priority, assignment/unassignment | SUPPORT/ADMIN only |

AI tool pages are capped at 20 rows. Tool names, allowed argument names, JSON
types, positive IDs, enum values, bounds, and DTO validators are checked before
dispatch. String IDs, fractions, unknown fields, unknown tools, and conflicting
assignments are rejected. Tools invoke the existing order/ticket services under
the caller's authentication, so model instructions cannot bypass ownership or
staff permissions. The staff update tool is also hidden from customer tool lists.
The system instructions require explicit user intent before ticket creation or
updates.

Each actual tool execution is recorded in `tool_audit` with the caller, tool name,
validated argument summary, and SUCCESS/FAILURE/DENIED status. Rejected raw
arguments are not stored. Tool errors are sent back to the model with safe details,
and successful identical write calls within a single turn reuse the first result
to avoid duplicate tickets. The agent cannot execute SQL, arbitrary URLs, code,
order changes, user changes, or approvals.

User messages are saved before contacting the provider, and completed assistant
replies are saved afterward. Intermediate tool-call protocol messages stay in the
current request; tool executions remain in the audit table. Follow-up context uses
recent user/assistant messages, capped at 64000 characters; complete conversation
history remains available through paginated reads. Successful backend tools are
committed independently of the final model reply. A later provider failure does
not undo a created or updated ticket: check existing records before retrying a
write request. There are no automatic retries across HTTP chat requests.

Database transactions are short and do not span DeepSeek network requests.
A persisted turn token serializes replies to one conversation; overlapping chat
turns return 409. Provider failures clear the token, and a stale token can be
reclaimed after the configured provider/tool budget plus a recovery allowance.
Conversation rows add nullable `processing_token` and `processing_started_at`
columns, plus conversation/message lookup indexes. Local `DB_DDL_AUTO=update`
applies these schema changes; `none`/`validate` deployments require a schema update.

Invalid input returns 400 `ProblemDetail`; provider failures or malformed/truncated
replies return 502, provider rate limits or missing configuration return 503, and
provider timeouts return 504. Provider error bodies and credentials are never
included in these responses. Exceeding the tool budget returns 502 without executing
additional calls. Live DeepSeek availability and account credentials are external
requirements; automated tests use a local HTTP provider stub and no paid calls.

## User helpers

Inject `UserHelper` from `com.AgentOps.helpers` to reuse `getUserById`,
`getUserByEmail`, `normalizeEmail`, and `ensureEmailAvailable`. Missing users
return 404; invalid IDs or blank emails return 400; duplicate emails return 409.
The email availability check supplements the database unique constraint.
Registration and login reuse these helpers; login retains its generic credentials
error and dummy password comparison for unknown accounts.

Inject `SecurityHelper` for `getCurrentUser`, `getCurrentUserId`,
`isCurrentUser(Long userId)`, and `hasRole(UserRole role)`. Current-user getters
require an authenticated JWT and return 401 otherwise. `getCurrentUser` also
checks the database and returns 401 for a deleted account. Boolean checks return
false for anonymous requests; role checks use the JWT's granted authorities,
matching Spring Security. ID and role checks do not query the database.
Use the resource's actual owner ID with `isCurrentUser`; user lookups alone do
not authorize access to another user's records.

## Tests

Tests use JUnit Jupiter. `GlobalExceptionHandlerUnitTests` uses Mockito's
`MockitoExtension`, `@Mock`, stubbing, and interaction verification to test error
handling without starting Spring or connecting to PostgreSQL. The Spring Boot
test starters provide JUnit and Mockito dependencies.

`AuthServiceImplTests` uses Mockito to verify password hashing, registration,
duplicate handling, and login failures. `AuthIntegrationTests` uses PostgreSQL,
MockMvc, and the actual security filters to verify signed tokens, expiry, tampering,
issuer/claim validation, role restrictions, and authentication errors. Authentication
integration test data is rolled back after each test; the concurrent refresh test
uses committed transactions and explicitly removes its account afterward. Tests
also cover refresh rotation/replay, concurrent refresh, logout isolation, partial
profile updates, and session invalidation after password changes.

`OrderServiceImplTests` uses Mockito to verify ownership filtering and creation
rules. `OrderIntegrationTests` exercises real JWT filters and PostgreSQL persistence
for customer isolation, staff access, pagination, validation, and API responses.
`TicketServiceImplTests` and `TicketIntegrationTests` cover ticket ownership,
staff permissions, filtered pagination, assignments, partial updates, validation,
and concurrent-update conflicts. The concurrency test explicitly cleans up its
committed rows; other ticket integration tests roll their data back.
`AgentToolExecutorTests` uses Mockito to check tool allowlisting, strict validation,
service dispatch, and auditing. `AgentIntegrationTests` runs the real DeepSeek HTTP
adapter against a local stub, with JWT security and PostgreSQL, to verify multi-turn
history, privacy, tool results, ticket writes, denied actions, duplicate-write
protection, provider failures, conversation locking, and execution limits.
One agent integration test uses committed transactions to verify that successful
ticket changes and audit records survive a later provider failure, then cleans up
its records explicitly. The other agent integration tests roll their data back.

Run the unit tests and DTO validation tests:

```powershell
.\mvnw.cmd "-Dtest=GlobalExceptionHandlerUnitTests,RequestValidationTests" test
```

`GlobalExceptionHandlerTests` tests HTTP responses through MockMvc. The full suite
also includes application startup and persistence tests against the configured
PostgreSQL database; run all tests with `.\mvnw.cmd test`.

## GitHub Actions CI/CD

The workflow is `.github/workflows/ci-cd.yml`:

- Pull requests targeting `main` run CI: Java 21 setup, all JUnit/Mockito and
  PostgreSQL integration tests, Maven packaging, and a Docker image build.
- Pushes to `main` run the same CI, then CD publishes the image to Docker Hub.
- Manual runs execute CI only.

CD declares `needs: ci` and explicitly requires a successful CI result. Failed,
cancelled, or skipped CI cannot publish an image. CI uses a disposable PostgreSQL
18 service with test-only credentials and `create-drop` schema management; it
does not require your local database or Docker Hub secrets.

In the GitHub repository, open **Settings → Secrets and variables → Actions**
and add these repository secrets:

| Secret | Value |
| --- | --- |
| `DOCKERHUB_USERNAME` | Your Docker Hub username |
| `DOCKERHUB_PASSWORD` | Your Docker Hub password, or an access token if required by your account |

Create an `agentops` repository under that Docker Hub account. To use another
repository name, set the optional GitHub Actions variable `DOCKERHUB_REPOSITORY`
to that name (without the username prefix).

Successful publishing produces `<username>/agentops:latest` and
`<username>/agentops:sha-<full-commit-sha>`. Test reports are saved as workflow
artifacts, and Maven/Docker layers are cached between runs. CD currently publishes
the image; deployment to a server can be added once hosting is selected.
