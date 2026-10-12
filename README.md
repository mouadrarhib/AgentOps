# AgentOps

## Local PostgreSQL setup

Copy `.env.example` to `.env` in the project root and set `DB_PASSWORD` to your
local PostgreSQL password. The `.env` file is ignored by Git and is loaded by
Spring Boot when the application runs from the project root. You can also set
the same variables in your shell or IDE run configuration; environment variables
take precedence over `.env` values.

The default connection is `localhost:5432/agentops` with the `postgres` user.
Create the `agentops` database before starting the application.

Run with Java 21:

```powershell
.\mvnw.cmd spring-boot:run
```

Run the tests:

```powershell
.\mvnw.cmd test
```
