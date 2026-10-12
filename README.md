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
