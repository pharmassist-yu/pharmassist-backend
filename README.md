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

## Cloud Database Service Comparison

> **Database decision vs. Statement of Work (SOW).** The current SOW still names PostgreSQL
> (Section 9, factor T1; Section 12, "Database and medication data"). The team has since decided to
> use **MySQL 8**, and this backend is implemented and tested against MySQL 8. The official SOW must
> be revised separately to reflect this decision. This section only covers MySQL hosting options.

The SOW requires the database to be hosted in the cloud from the beginning, while the API and
demonstrations initially run locally (Section 7.1). It lists **Render/Aiven** accounts for hosting
(Section 12), so those two are the primary candidates. The others are alternatives. Local development
still uses the Docker MySQL described above.

> Checked against the providers' official pages on **2026-11-25**. Prices depend on region, instance
> size, storage, traffic and plan, and change often. Only list or starting prices are shown; no
> monthly cost for PharmAssist is estimated. "Not verified" means the official pages we read did not
> confirm the point; it must be checked before relying on it.

### 1. Provider comparison

| Service | MySQL offering | Free option / starting price | Main limits | Backups and availability |
|---|---|---|---|---|
| **Render** (SOW) | **No managed MySQL.** Render manages only Postgres and Key Value. MySQL 8 runs as a self-managed Docker **private service** with a persistent disk (Render's example repo). The team is responsible for configuration, upgrades and backups. | No free option: private services and disks are paid. Prices: see the pricing page (not verified). | One instance per disk (no scaling, no HA); deploys cause a few seconds of downtime. Private services are reachable only from other Render services; access from developers' machines needs extra setup (not verified). | Daily disk snapshots kept 7 days, but Render advises **against** restoring snapshots of a MySQL disk (risk of corruption) and recommends `mysqldump`, which the team must schedule itself. |
| **Aiven for MySQL** (SOW) | Native MySQL, fully managed. | **Free MySQL tier retired:** closed to new services on 2026-11-06; remaining free services and their backups are deleted on 2026-12-21. Developer **$5/month**, Hobbyist from $19/month, Startup from $75/month, Business from $180/month. | Developer: 1 CPU, 1 GB RAM, 8 GB storage; region cannot be chosen. | Developer/Hobbyist: single backup for disaster recovery only, **no HA, no SLA**. Startup: 2-day PITR, 99.99% SLA listed (terms require validation). HA (automatic failover) from Business. |
| **Railway** | **MySQL Docker template**, not a fully managed DBaaS. Railway maintains the template (official `mysql` image); changing the image, start command or variables makes the database the team's responsibility. | Free $0 (incl. $1 usage/month); Hobby **$5/month** incl. $5 usage; Pro $20/month. Usage billed per resource (RAM $10/GB, CPU $20/vCPU, volume $0.15/GB per month). | Free: 0.5 GB RAM, 0.5 GB volume, single node, sleeps after 5–10 min idle. Hobby: volume up to 5 GB. | Daily/weekly/monthly backup schedules (daily kept 6 days) and 7-day PITR; plan eligibility not verified. HA cluster from Hobby. |
| **Amazon RDS for MySQL** | Native MySQL, fully managed. | New accounts: Free plan with up to $200 credits for up to 6 months (`db.t3.micro`/`db.t4g.micro`), then pay-as-you-go by region and instance. | Free plan instance classes only; IAM/VPC setup adds complexity. | Automated backups in a daily window + PITR (transaction logs uploaded every 5 min); restore creates a new instance. Multi-AZ at extra cost. |
| **Google Cloud SQL for MySQL** | Native MySQL, fully managed. | No always-free tier. 30-day free trial instance (no backups, no SLA), plus $300 / 90-day credit for new customers. Then billed by vCPU, memory, storage, region and edition. | Trial: one instance per new project, billing account required. | Automated backups (7 kept by default, 1–365), PITR, regional HA. SLA covers **HA-enabled** instances only. |
| **TiDB Cloud Starter** | **MySQL-compatible, not MySQL** (distributed TiDB engine; see below). | Free for up to 5 instances: 5 GiB row + 5 GiB columnar storage, 50M Request Units/month each. | Over quota: new connections refused until next month or a spending limit is set. 400 connections (free), transactions ≤ 30 min. | Automatic backups kept 1 day (free) or 1–30 days (paid). |

**TiDB compatibility.** TiDB does not support stored procedures/functions, triggers, events,
user-defined functions, SPATIAL types, XA or `SKIP LOCKED`. `AUTO_INCREMENT` values are unique but not
necessarily sequential, views are not updatable, the default collation is `utf8mb4_bin`, and locking
differs from InnoDB. `DatabaseMigrationTest` asserts MySQL 8, so the tests would fail against TiDB.

### 2. SOW operational requirements

A provider feature only *enables* a requirement. **No provider guarantees successful restores or a
specific recovery time:** NFR-5.2 and NFR-5.3 must be proven by the team's own restore tests before
each release, with measured recovery time and data loss recorded in the evaluation reports (D4). Likewise, a daily
backup schedule or PITR does **not by itself guarantee RPO ≤ 24 h**: backups can fail, run late or
expire. The actual RPO must be measured in restore tests.

| SOW requirement | Render (self-managed MySQL) | Aiven Developer | Aiven Startup / Business | Railway | AWS RDS | Cloud SQL | TiDB Starter (free) |
|---|---|---|---|---|---|---|---|
| Shared cloud access for the team (Section 7.1) | Not verified: private services are internal only | Yes (connection URI) | Yes | Yes, via TCP proxy | Yes (network setup needed) | Yes (network setup needed) | Yes |
| TLS 1.2+ (NFR-3.1) | Not verified (team must configure) | Not verified | Not verified | Not verified | TLS supported; version not verified | TLS can be enforced; version not verified | TLS required; version not verified |
| Backup at least every 24 h (NFR-5.1) | Only via team-run `mysqldump` | Not verified ("single backup only") | Yes (PITR 2 / 14 days) | Daily schedule available; plan not verified | Yes (daily + PITR) | Yes (daily + PITR) | Yes, 1-day retention |
| 100% successful scheduled restores, tested each release (NFR-5.2) | Team test required | Team test required; restore path not verified | Team test required | Team test required (same project only) | Team test required | Team test required | Team test required |
| RTO ≤ 4 h (NFR-5.3) | Requires validation | Requires validation | Requires validation | Requires validation | Requires validation | Requires validation | Requires validation |
| RPO ≤ 24 h (NFR-5.3) | Only if dumps run daily | Not verified | Supported by PITR; requires validation | Supported by daily backup/PITR; requires validation | Supported by PITR; requires validation | Supported by PITR; requires validation | Supported by daily backup; requires validation |
| Availability ≥ 95% in evaluation (NFR-5.4) | No HA; requires monitoring | No HA, no SLA; requires monitoring | 99.99% SLA listed for Startup on the MySQL pricing page; applicability under Aiven's SLA terms requires validation. HA from Business. Requires monitoring | Not verified; requires monitoring | Not verified; requires monitoring | SLA only with HA; requires monitoring | Not verified; requires monitoring |
| 50 concurrent users, p95 < 2.5 s (NFR-1.1) | Load test required (smallest plan 0.5 CPU / 512 MB) | Load test required (1 CPU / 1 GB) | Load test required | Load test required | Load test required | Load test required | Load test required (400 connections) |

### 3. Recommendation for PharmAssist

**Phase 1: prototype and development (now until the first-term prototype)**

- Use **Aiven for MySQL Developer** ($5/month) as the shared cloud database. Aiven is named in the
  SOW, the database is fully managed native MySQL 8, and the team can connect to it directly.
- Developer has **no HA, no SLA and only a single disaster-recovery backup**, so it is suitable only
  for shared development, not for meeting NFR-5.x. Keep migrations in Flyway so the schema can always
  be recreated, and keep test data synthetic (NFR-3.7).
- Render is **not recommended for the database**: it has no managed MySQL, its MySQL disk snapshots
  are not a safe restore method, and the team would own backups and upgrades. Render can still host
  the API later, as the SOW plans, connecting to Aiven over TLS.
- Alternative: Railway Hobby ($5/month + usage), keeping in mind it is a template container, not a
  fully managed database.

**Phase 2: acceptance testing and production-like evaluation (Sprints 11–12)**

- Move to a plan that provides at least daily automated backups and point-in-time recovery, for
  example **Aiven Startup** (2-day PITR, 99.99% SLA listed; terms require validation) or **Aiven Business** if HA is required.
  AWS RDS or Cloud SQL with automated backups enabled are the alternatives. The cost must be agreed
  by the team before upgrading.
- Before each release: run a scheduled restore into an isolated environment and record the measured
  recovery time (≤ 4 h) and data loss (≤ 24 h). Also verify TLS 1.2+ on the actual connection, monitor
  availability (≥ 95%), and run the 50-user load test.
- Do not report any of these requirements as met based on provider documentation alone.

Keep all database credentials in environment variables or a secret manager, never in the repository.

### Sources (official provider pages)

- Render – Deploy MySQL: <https://render.com/docs/deploy-mysql>
- Render – Databases: <https://render.com/docs/databases>
- Render – Persistent disks: <https://render.com/docs/disks>
- Render – Private network: <https://render.com/docs/private-network>
- Render – Compute plans: <https://render.com/docs/compute-plans>
- Render – Pricing: <https://render.com/pricing>
- Aiven – MySQL free tier retirement: <https://aiven.io/docs/products/mysql/howto/mysql-free-tier-migration-options>
- Aiven – MySQL pricing: <https://aiven.io/pricing/mysql>
- Aiven – Service backups: <https://aiven.io/docs/platform/concepts/service_backups>
- Aiven – SLA: <https://aiven.io/sla>
- Railway – Pricing plans: <https://docs.railway.com/pricing/plans>
- Railway – Free plan: <https://docs.railway.com/pricing/free-plan>
- Railway – MySQL: <https://docs.railway.com/databases/mysql>
- Railway – Backups: <https://docs.railway.com/reference/backups>
- AWS – RDS free tier: <https://aws.amazon.com/rds/free/>
- AWS – RDS for MySQL pricing: <https://aws.amazon.com/rds/mysql/pricing/>
- AWS – RDS automated backups: <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_WorkingWithAutomatedBackups.html>
- AWS – RDS point-in-time recovery: <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_PIT.html>
- AWS – RDS for MySQL SSL/TLS: <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/mysql-ssl-connections.html>
- Google Cloud – Cloud SQL pricing: <https://cloud.google.com/sql/pricing>
- Google Cloud – Cloud SQL free trial instance: <https://docs.cloud.google.com/sql/docs/mysql/free-trial-instance>
- Google Cloud – Cloud SQL high availability: <https://docs.cloud.google.com/sql/docs/mysql/high-availability>
- Google Cloud – Cloud SQL SSL/TLS: <https://docs.cloud.google.com/sql/docs/mysql/configure-ssl-instance>
- Google Cloud – Cloud SQL SLA: <https://cloud.google.com/sql/sla>
- TiDB Cloud – Starter limitations and quotas: <https://docs.pingcap.com/tidbcloud/serverless-limitations/>
- TiDB Cloud – Starter backup and restore: <https://docs.pingcap.com/tidbcloud/backup-and-restore-serverless/>
- TiDB – MySQL compatibility: <https://docs.pingcap.com/tidb/stable/mysql-compatibility>
