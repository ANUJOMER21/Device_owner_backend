# AWS RDS PostgreSQL Setup Guide for DA EMI Locker Backend

This guide walks you through setting up Amazon RDS for PostgreSQL and connecting it to your backend application.

---

## Prerequisites

- AWS account
- Backend application configured for PostgreSQL (already done)

---

## Step 1: Create RDS PostgreSQL Instance

1. Log in to [AWS Console](https://console.aws.amazon/) and go to **RDS** (Relational Database Service).

2. Click **Create database**.

3. Choose engine options:
   - **Engine type**: PostgreSQL
   - **Engine version**: Choose a supported version (e.g. PostgreSQL 15.x or 16.x)
   - **Templates**: 
     - **Production** – for production workloads
     - **Dev/Test** – lower cost, good for staging
     - **Free tier** – eligible accounts only, 12 months free

4. **Settings**:
   - **DB instance identifier**: e.g. `da-emilocker-db`
   - **Master username**: e.g. `postgres` (or your preferred admin user)
   - **Master password**: Strong password – save it securely (you will need it for the backend)

5. **Instance configuration**:
   - **DB instance class**: e.g. `db.t3.micro` (free tier) or `db.t3.small` (production)
   - **Storage**: 20–100 GB, enable auto-scaling if desired

6. **Connectivity**:
   - **VPC**: Default VPC or your custom VPC
   - **Subnet group**: Default or custom
   - **Public access**: 
     - **Yes** – for development or when backend runs outside AWS (e.g. local)
     - **No** – for production when backend runs in same VPC (EC2, ECS, Lambda)
   - **VPC security group**: Create new (e.g. `da-emilocker-db-sg`) or select existing
   - **Availability Zone**: No preference (default) or select a specific AZ

7. **Database authentication**:
   - **Password authentication** – simplest, recommended to start

8. **Additional configuration** (expand if needed):
   - **Initial database name**: e.g. `da_emilocker` (matches your `DB_NAME`)
   - **Backup retention**: 7 days (prod) or 1 day (dev)
   - **Encryption**: Enable for production

9. Click **Create database**. Creation takes 5–15 minutes.

---

## Step 2: Configure Security Group (Allow Inbound Access)

RDS is blocked by default. Allow your backend to connect:

1. Go to **EC2** → **Security Groups**.

2. Find the security group attached to your RDS instance (e.g. `da-emilocker-db-sg`).

3. **Edit inbound rules** → **Add rule**:

   | Type     | Protocol | Port | Source                       |
   |----------|----------|------|------------------------------|
   | PostgreSQL | TCP    | 5432 | Your IP or CIDR (see below) |

   **Source options**:
   - **Development**: `My IP` – only your current IP
   - **EC2/ECS in same VPC**: Security group of the compute resource (e.g. `sg-xxx`)
   - **Allow all** (not recommended): `0.0.0.0/0` – use only for testing

4. Save the rules.

---

## Step 3: Get RDS Connection Details

1. Go to **RDS** → **Databases**.

2. Click your DB identifier (e.g. `da-emilocker-db`).

3. In the connectivity section, note:
   - **Endpoint**: e.g. `da-emilocker-db.xxxxx.ap-south-1.rds.amazonaws.com`
   - **Port**: `5432`
   - **Database name**: Default RDS creates `postgres`. Use `DB_NAME=postgres` unless you created `da_emilocker`.
   - **Username**: `postgres` (or what you set)

---

## Step 4: Configure Backend

Your `application.properties` already supports environment variables. Configure as follows.

### Option A: Environment Variables (recommended for production)

```bash
export DB_HOST=da-emilocker-db.xxxxx.ap-south-1.rds.amazonaws.com
export DB_PORT=5432
export DB_NAME=postgres
export DB_USERNAME=postgres
export DB_PASSWORD=your_secure_password
export DB_SSLMODE=require
```

**Note:** RDS default database is `postgres`. Use `DB_NAME=postgres` unless you created `da_emilocker`:
```bash
./create-rds-da-emilocker-db.sh
export DB_NAME=da_emilocker
./start-backend-aws-rds.sh
```

### Option B: Override Datasource URL

If your backend uses a single URL property:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://da-emilocker-db.xxxxx.ap-south-1.rds.amazonaws.com:5432/da_emilocker
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=your_secure_password
```

### Current Application Properties

Your backend uses:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/${DB_NAME:da_emilocker}
```

To use RDS, set the full URL via:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR-RDS-ENDPOINT:5432/da_emilocker
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=your_secure_password
```

### Optional: Update `application.properties` for RDS

You can also add RDS-specific placeholders:

```properties
# Use DB_HOST for flexible deployment (requires URL to be built from components)
# Or use SPRING_DATASOURCE_URL directly (recommended)
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:da_emilocker}
```

Then:

```bash
export DB_HOST=da-emilocker-db.xxxxx.ap-south-1.rds.amazonaws.com
export DB_PORT=5432
export DB_NAME=da_emilocker
export DB_USERNAME=postgres
export DB_PASSWORD=your_secure_password
```

---

## Step 5: Enable SSL (Optional but Recommended for Production)

RDS supports SSL. To use it:

1. **Download RDS CA bundle** (if needed):  
   [Amazon RDS CA certificates](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html)

2. **JDBC URL with SSL**:

```properties
spring.datasource.url=jdbc:postgresql://YOUR-RDS-ENDPOINT:5432/da_emilocker?sslmode=require
```

   For full verification (with CA bundle):

```properties
spring.datasource.url=jdbc:postgresql://YOUR-RDS-ENDPOINT:5432/da_emilocker?sslmode=verify-full&sslrootcert=/path/to/rds-ca-bundle.pem
```

---

## Step 6: Run Migrations

Flyway will run on startup. Ensure:

- `spring.flyway.enabled=true` (default)
- `spring.flyway.baseline-on-migrate=true` (already set)

Start the backend:

```bash
./gradlew bootRun
```

Flyway will create the schema and run migrations if the database is empty.

---

## Verification

1. **Check connectivity**:

```bash
psql -h YOUR-RDS-ENDPOINT -p 5432 -U postgres -d da_emilocker
```

2. **Backend health**:

```bash
curl http://localhost:8080/actuator/health
```

3. **Database endpoint** (if exposed):

```bash
curl http://localhost:8080/api/health
```

---

## Network Scenarios

| Backend runs on | RDS public access | Security group source |
|-----------------|-------------------|------------------------|
| Local machine   | Yes               | Your IP or `0.0.0.0/0` (dev only) |
| EC2 (same VPC)  | No                | EC2 security group |
| ECS (same VPC)  | No                | ECS task security group |
| Lambda (VPC)    | No                | Lambda execution role + VPC config |

---

## Cost Notes

- **db.t3.micro**: ~\$15–20/month (or free tier for 12 months)
- **db.t3.small**: ~\$30–35/month
- Storage: ~\$0.115/GB/month (gp3)

See `AWS_MONTHLY_COST_REPORT.md` for overall cost estimates.

---

## Security Notes

- Never commit DB credentials to version control.
- Use AWS Secrets Manager or Parameter Store in production.
- Prefer IAM database authentication for EC2/ECS.
- Use SSL (`sslmode=require` or `verify-full`) in production.
- Restrict security group to specific IPs or security groups.
- Use a separate DB user with limited privileges (avoid `postgres` in production).

---

## Troubleshooting

### "Connect timed out" / SocketTimeoutException (most common)

This usually means your machine **cannot reach** the RDS instance over the network.

**Quick workaround – use local PostgreSQL:**
```bash
./run-local-db.sh
```
Or: `unset DB_HOST DB_PASSWORD DB_SSLMODE` then `./gradlew bootRun`

**Checklist:**

1. **RDS Public access**  
   - Go to RDS → Databases → your instance → Connectivity  
   - Ensure **Publicly accessible** = **Yes**.  
   - If it’s **No**, the DB is only reachable from inside the VPC (e.g. EC2).

2. **Security Group**  
   - Go to EC2 → Security Groups  
   - Open the security group used by your RDS instance  
   - Add an inbound rule: Type = **PostgreSQL**, Port = **5432**, Source = **My IP** (or your current IP)  
   - For temporary testing only: Source = **0.0.0.0/0**  
   - Save the rules.

3. **Network**  
   - Test from the same network: `nc -zv YOUR-RDS-ENDPOINT 5432` (or `telnet`)  
   - If this fails, a firewall or VPN may be blocking outbound port 5432.

4. **Export variables correctly (no comments on the same line)**  
   ```bash
   export DB_HOST="da-emilocker-db.xxx.eu-north-1.rds.amazonaws.com"
   export DB_PASSWORD="your_password"
   export DB_SSLMODE="require"
   export DB_NAME="da_emilocker"
   ./gradlew bootRun
   ```
   Do not put `# comments` on the same line as `export` commands.

### Other issues

| Issue | Possible cause | Fix |
|-------|----------------|-----|
| Connection timeout | Security group blocking | Add inbound rule for port 5432, ensure Publicly accessible = Yes |
| Authentication failed | Wrong password | Reset master password in RDS console |
| SSL handshake error | SSL misconfigured | Try `sslmode=disable` first, then `require` |
| Unknown database | DB not created | Set initial database name when creating RDS, or use `DB_NAME=postgres` |
| Too many connections | Pool too large | Reduce `maximum-pool-size` in HikariCP config |
