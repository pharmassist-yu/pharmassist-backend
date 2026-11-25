# PharmAssist – Backend
Spring Boot (Java) REST API for the PharmAssist application.

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

The integration tests must run against the **local Docker MySQL 8** database described above
(`pharmassist-mysql` on port 3307), so it must be running and `DB_PASSWORD` must be set (via `.env`
or the environment). **Never run the tests against the shared cloud database**: they connect to
whatever `DB_URL` points to, apply Flyway migrations and write test data.

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

### Regions and connection limits

| Service | Region options | Concurrent connection limit |
|---|---|---|
| **Render** | Oregon, Ohio, Virginia (USA), Frankfurt, Singapore. Chosen at creation; cannot be changed later. Each region has its own private network, and private services are not reachable from the public internet. | Render does not manage MySQL. The MySQL 8 server default `max_connections` is 151 (configurable by the team); the practical limit depends on the instance RAM. |
| **Aiven** | Developer: **no choice of cloud or region** (DigitalOcean; Aiven may change provider or region). Hobbyist: DigitalOcean, Google Cloud, OVH. Startup and above: AWS, Azure, Google Cloud, DigitalOcean, OVH, UpCloud. Available regions can differ per project. | Depends on RAM and is not configurable: `75 × RAM (GiB) + 1` below 4 GiB, `100 × RAM (GiB) + 1` from 4 GiB. For Developer (1 GB) this formula gives about 76; this number is our calculation, not stated by Aiven. |
| **Railway** | US West (California), US East (Virginia), EU West (Amsterdam), Southeast Asia (Singapore). Region can be changed later, but moving a volume causes downtime. Which plans may choose a region: not verified. | Not verified. |
| **Amazon RDS** | Most AWS Regions; chosen at creation. Whether the Free plan covers every Region: not verified. | Default `{DBInstanceClassMemory/12582880}` (about RAM in MB / 12), configurable 1–100000. AWS gives about **60** for `db.t3.micro`; `db.t4g.micro` not stated. |
| **Google Cloud SQL** | About 40 regions; chosen at creation and **cannot be changed** later. | Depends on the machine type's memory; default values not verified. |
| **TiDB Cloud Starter** | Not verified (the official region page was unavailable). | **400** concurrent connections; **5,000** with a spending limit. Connections open longer than 30 minutes may be terminated. |

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

### 4. Connecting to the Shared Cloud Database

The backend already reads `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` (see `application.properties`),
so connecting to the shared database needs **no code change**: each team member only changes their
own git-ignored `.env` file.

1. Get the connection details (host, port, database name, user, password, CA certificate if needed)
   from the person who manages the database, via the team's secret manager or another secure,
   one-to-one channel.
2. Put them in `.env` in the project root (placeholders shown, never commit real values):

   ```properties
   DB_URL=jdbc:mysql://<db-host>:<db-port>/<db-name>?sslMode=REQUIRED
   DB_USERNAME=<db-user>
   DB_PASSWORD=<db-password>
   ```

3. Start the backend (`./mvnw spring-boot:run`) and check `GET /actuator/health` returns `UP`.
   Switching back to the local Docker database only requires restoring the local values in `.env`.

**TLS.** Spring Boot 4.2.0 uses MySQL Connector/J 26.10.0, which supports the `sslMode` property.
Its default is `PREFERRED`, which silently falls back to an unencrypted connection if the server
does not offer TLS, so always set `sslMode` explicitly:

- `sslMode=REQUIRED`: the connection must be encrypted, otherwise it fails. The server certificate is
  **not** verified, so this does not protect against a fake server.
- `sslMode=VERIFY_CA` / `sslMode=VERIFY_IDENTITY`: also verify the server certificate against a
  trusted CA (`VERIFY_IDENTITY` additionally checks the host name). If the provider's CA is not in
  the Java system trust store, import its CA certificate into a trust store and add
  `trustCertificateKeyStoreUrl=file:<path-to-truststore.p12>&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=<truststore-password>`
  to `DB_URL`. Aiven requires its project CA certificate for these modes; TiDB Cloud uses Let's
  Encrypt certificates. `.env` is read as a properties file, so use forward slashes in the path
  (e.g. `file:///C:/<folder>/truststore.p12` on Windows), because backslashes are treated as escapes.
- The **trust store password is also a secret**: keep it only in your own `.env` (or the secret
  manager), never in the README, Git or chat. Do not commit the trust store file either.
- Connector/J allows only TLS 1.2 and 1.3 by default (`tlsVersions`), matching NFR-3.1.
- Enforce TLS on the server side as well: RDS `require_secure_transport=ON` (off by default up to
  MySQL 8.4), Cloud SQL SSL mode `ENCRYPTED_ONLY`. Aiven states that all traffic is TLS-protected,
  and TiDB Cloud Starter accepts only TLS 1.2/1.3 connections.

**Recommendation:** use `sslMode=VERIFY_IDENTITY` for the shared database. It **requires** the
provider's CA certificate to be trusted (Java system trust store or the trust store configured
above). Until that is set up, `sslMode=REQUIRED` is the minimum: it encrypts the connection but does
not prove that the client is talking to the real database server.

**IP allowlist.** Where possible, allow only the team's addresses:

- Aiven: the IP filter is `0.0.0.0/0` (open to all) by default; replace it with the team's IP ranges.
- Cloud SQL: public-IP clients must be added as authorized networks (IPv4 CIDR), or connect through
  the Cloud SQL Auth Proxy.
- RDS: enable public access and allow the team's IPs in the VPC security group on the instance's
  database port (3306 by default for MySQL, but it can be set differently when the instance is
  configured).
- TiDB Cloud Starter: all IPs are allowed by default; firewall rules can restrict this (max 200).
- Railway: public access is a TCP proxy; IP allowlisting not verified.
- Render: a MySQL private service is **not reachable from developers' machines** (private services
  have no public internet access), so it is **not a direct option for the team's shared database**.
  Only other Render services in the same region can connect to it.

Team members with changing home IPs may need their address updated in the allowlist.

**Rules for the shared database.**

- Never put real connection details in GitHub, the README, issues, pull requests or group chats.
  Share them only through a secret manager or a secure channel with authorized members, and rotate
  the password if it is exposed.
- When the backend starts, Flyway applies pending migrations to the database it connects to. Only
  start it against the shared database from code whose migrations are already merged to `main`.
- Run `./mvnw clean verify` against the local Docker database, not the shared one.

### 5. Sprint 5 next steps

1. The team agrees on the provider and plan (recommended: Aiven for MySQL Developer) and one member
   creates the shared database.
2. The database owner shares the connection details through a secret manager or a secure channel,
   and restricts access with the IP allowlist.
3. Each team member connects with their own `.env`, confirms `/actuator/health` is `UP` and confirms
   the connection uses TLS:
   - Preferred: `sslMode=VERIFY_IDENTITY` (the provider's CA trust chain must be configured).
   - Minimum until the CA is configured: `sslMode=REQUIRED`.
4. Plan and run the SOW checks: TLS 1.2+ verification, backup and restore test (measured RTO/RPO),
   and the 50-concurrent-user load test. Record the results; do not mark them as met before testing.

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
- Render – Regions: <https://render.com/docs/regions>
- Render – Private services: <https://render.com/docs/private-services>
- Aiven – Developer plan: <https://aiven.io/docs/platform/concepts/free-plan>
- Aiven – List of clouds: <https://aiven.io/docs/platform/reference/list_of_clouds>
- Aiven – MySQL max connections: <https://aiven.io/docs/products/mysql/concepts/max-number-of-connections>
- Aiven – Restrict access (IP filter): <https://aiven.io/docs/platform/howto/restrict-access>
- Aiven – TLS/SSL certificates: <https://aiven.io/docs/platform/concepts/tls-ssl-certificates>
- Aiven – Connect with Java: <https://aiven.io/docs/products/mysql/howto/connect-with-java>
- Railway – Deployment regions: <https://docs.railway.com/reference/deployment-regions>
- Railway – TCP proxy: <https://docs.railway.com/reference/tcp-proxy>
- AWS – RDS Regions: <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/Concepts.RegionsAndAvailabilityZones.html>
- AWS – RDS quotas and max connections: <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_Limits.html>
- AWS – RDS in a VPC: <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/USER_VPC.WorkingWithRDSInstanceinaVPC.html>
- AWS – RDS require TLS: <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/mysql-ssl-connections.require-ssl.html>
- AWS – RDS for MySQL TLS versions: <https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/MySQL.Concepts.SSLSupport.html>
- Google Cloud – Cloud SQL locations: <https://docs.cloud.google.com/sql/docs/mysql/locations>
- Google Cloud – Cloud SQL quotas and limits: <https://docs.cloud.google.com/sql/docs/quotas>
- Google Cloud – Cloud SQL authorized networks: <https://docs.cloud.google.com/sql/docs/mysql/authorize-networks>
- TiDB Cloud – Plans: <https://docs.pingcap.com/tidbcloud/select-cluster-tier>
- TiDB Cloud – Secure connections (TLS): <https://docs.pingcap.com/tidbcloud/secure-connections-to-serverless-clusters>
- TiDB Cloud – Firewall rules: <https://docs.pingcap.com/tidbcloud/configure-serverless-firewall-rules-for-public-endpoints>
- MySQL Connector/J – Security properties (`sslMode`, `tlsVersions`): <https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html>
- MySQL 8.0 – `max_connections`: <https://dev.mysql.com/doc/refman/8.0/en/server-system-variables.html>
