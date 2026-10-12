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
Docker Compose passes these settings to the backend. Production requires its own
secret; the fixed key in `src/test/resources/application.properties` is for tests
only and is excluded from the application JAR. CI tests use that test configuration
and do not require a JWT secret in GitHub.

| Method | Endpoint | Access | Result |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | Public | 201, token and user |
| POST | `/api/auth/login` | Public | 200, token and user |
| GET | `/api/auth/me` | Bearer token | 200, current user |

Registration and login accept `{"email":"customer@example.com","password":"password123"}`.
The authentication response contains `accessToken`, `tokenType` (`Bearer`),
`expiresIn` (seconds), and `user` (`id`, `email`, `role`). For example:

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
`application/problem+json`. Authentication is stateless and uses no login session.
Use HTTPS when serving the API outside localhost.

Access tokens expire after the configured lifetime and use Spring Security's
default clock-skew allowance. There is no refresh-token or logout/revocation
endpoint yet. Role changes and account deletion do not revoke already-issued
tokens; `/me` checks that the account still exists. Rotate the signing key to
invalidate all issued tokens, or add a revocation mechanism if immediate
per-account invalidation is required. Existing accounts need PBKDF2 hashes to log in.

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
integration test data is rolled back after each test.

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
