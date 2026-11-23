# PharmAssist – Backend
Spring Boot (Java) REST API for the PharmAssist application.

Setup instructions will be added in P-02d.

## Local database (MySQL 8)

The backend uses a real MySQL 8 database in all environments, including tests.
For local development, run MySQL in Docker on port **3307**:

```sh
docker run -d --name pharmassist-mysql \
  -e MYSQL_ROOT_PASSWORD=<root-password> \
  -e MYSQL_DATABASE=pharmassist \
  -e MYSQL_USER=pharmassist \
  -e MYSQL_PASSWORD=<app-password> \
  -p 3307:3306 \
  mysql:8.0
```

If the container already exists, start it with `docker start pharmassist-mysql`.

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3307/pharmassist` | JDBC URL of the MySQL database |
| `DB_USERNAME` | `pharmassist` | Database user |
| `DB_PASSWORD` | *(none, required)* | Database password |
| `PHARMASSIST_CORS_ALLOWED_ORIGINS` | `http://localhost:8081,http://localhost:19006` | Origins allowed to call `/api/**` |

For local development, copy `.env.example` to `.env` and set `DB_PASSWORD`.
The backend reads `.env` automatically when started from the project root.
`.env` is git-ignored: never commit real passwords. Real environment variables take precedence over `.env`.

## Running

```sh
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Then check:

- `GET http://localhost:8080/api/ping` → `{"status":"ok"}`
- `GET http://localhost:8080/actuator/health` → `{"status":"UP", ...}`

## Tests

```sh
./mvnw clean verify           # Windows: mvnw.cmd clean verify
```

The integration tests connect to the MySQL database configured above, so MySQL must be running
and `DB_PASSWORD` must be set (via `.env` or the environment).

## Database migrations (Flyway)

- The schema is managed only by Flyway. Hibernate runs with `ddl-auto=validate` and never changes the schema.
- Migrations live in `src/main/resources/db/migration` and are named `V<version>__<description>.sql`,
  e.g. `V2__create_users_table.sql`.
- Pending migrations run automatically when the application starts. Applied migrations are recorded
  in the `flyway_schema_history` table.
- Never edit a migration that has already been applied; add a new migration instead.
