# AWS Monthly Cost Report — Spring Boot Backend

**Scope:** Spring Boot backend only (excludes admin-panel, configure_app, dealer_app).  
**Scale:** ~300 dealers, ~15,000 daily active customers (DAU).  
**Stack:** PostgreSQL (RDS), image/file storage (S3), compute (EC2/App Runner).

---

## 1. Backend Overview (from codebase)

### 1.1 Stack

| Component | Technology | Notes |
|-----------|------------|--------|
| **Runtime** | Spring Boot 4.x, Kotlin, Java 17 | JVM app |
| **Database** | PostgreSQL (HikariCP pool) | Prod pool: max 50, min idle 10 |
| **File storage** | Local `uploads/` today | Target: S3 for customer/signature/profile, Aadhar/PAN, ticket attachments |
| **Auth** | JWT | No AWS Cognito assumed |
| **Push** | Firebase Cloud Messaging (FCM) | Not AWS; no cost in this report |
| **Scheduling** | In-process (hourly + 10 min + 5 min jobs) | No separate queue service assumed |

RabbitMQ and Redis appear only in test dependencies (Testcontainers); production config does not use them, so they are **not** included in this estimate.

### 1.2 Storage usage (current implementation)

- **Customer:** `customer_image`, `signature_image` (max 10 MB per file).
- **Dealer:** `profile_image`.
- **Documents:** Aadhar `front_image`, `back_image`; PAN `pan_image`.
- **Support:** Ticket attachments (image or PDF, max 10 MB).
- **Config:** `spring.servlet.multipart.max-file-size=10MB`, `max-request-size=10MB`.

All currently written to local `uploads/`; for AWS we assume migration to S3 (same limits).

### 1.3 Data model (DB sizing)

- **Core:** dealers, customers, device_status, device_commands, toggle_states, activities.
- **Support:** support_tickets, ticket_messages (with attachment URLs).
- **KYC/Loan:** customer_aadhar_details, customer_pan_details, customer_loan_details, payment_history, sim_details, dealer_payments.
- **Other:** contact_submissions.

Rough row scaling used below: ~300 dealers, ~50k customers, 15k DAU, device_commands/activities in the 100k–500k range.

---

## 2. Assumptions for Cost Model

| Parameter | Value | Rationale |
|-----------|--------|-----------|
| **Dealers** | 300 | Given |
| **Daily active users (DAU)** | 15,000 | Given |
| **Total customers (over time)** | 50,000 | 300 dealers × many customers each |
| **API requests per DAU per day** | 25 | Login, dashboard, device status, commands, profile, documents |
| **Daily API requests** | 375,000 | 15,000 × 25 |
| **Monthly API requests** | ~11.25M | For compute/throughput sizing |
| **Peak concurrent users** | ~1,500 | ~10% of DAU at peak |
| **Avg image size (customer/signature/profile)** | 250 KB | Compressed JPEG/PNG |
| **Avg document image (Aadhar/PAN)** | 400 KB | Per image |
| **Ticket attachment** | 500 KB | Mixed image/PDF |
| **S3 storage per customer (images + docs)** | ~2 MB | Customer + signature + optional Aadhar/PAN |
| **Total S3 storage (initial)** | 100 GB | 50k × 2 MB |
| **S3 growth per month** | ~3 GB | New signups + updates |
| **DB size (data + indexes)** | 15–25 GB | For RDS sizing |

All costs below are **on-demand list prices** in **us-east-1** (or equivalent). Reserved/Savings Plans can reduce compute and RDS by ~30–50%.

---

## 3. AWS Services and Monthly Cost

### 3.1 Amazon RDS for PostgreSQL

- **Instance:** `db.t3.medium` (2 vCPU, 4 GB RAM).
- **Storage:** 50 GB gp3 (provisioned), 3,000 IOPS (baseline).
- **Use case:** 300 dealers, 15k DAU, HikariCP max 50 connections; single instance is sufficient.

| Item | Unit | Quantity | Unit price (approx.) | Monthly cost (USD) |
|------|------|----------|------------------------|--------------------|
| db.t3.medium | hour | 730 | $0.072 | **~52.56** |
| gp3 50 GB | GB-month | 50 | $0.115 | **~5.75** |
| **RDS subtotal** | | | | **~58.31** |

- **1-year Reserved:** ~$38–40/month for instance (saves ~$14–15).
- **Smaller option:** `db.t3.small` (2 GB RAM) ~$26 + storage; possible if DB stays &lt; 10 GB and connection count is lower.

---

### 3.2 Amazon S3 (images and file storage)

- **Storage class:** S3 Standard (frequently accessed profile/document images).
- **Storage:** 100 GB existing + ~3 GB/month growth → **~103 GB** in first month; **~130 GB** by month 12.
- **Requests:**  
  - PUT: new/updated customer images, signatures, Aadhar/PAN, profile, ticket attachments. Estimate **~8,000 PUTs/month**.  
  - GET: app and admin panel serving images. Estimate **~1.5M GETs/month** (15k DAU × ~3 image views/day × 30).

| Item | Unit | Quantity | Unit price (approx.) | Monthly cost (USD) |
|------|------|----------|------------------------|--------------------|
| S3 Standard storage | GB-month | 103 | $0.023 | **~2.37** |
| PUT (write) | 1,000 requests | 8 | $0.005 | **~0.04** |
| GET (read) | 1,000 requests | 1,500 | $0.0004 | **~0.60** |
| **S3 subtotal** | | | | **~3.01** |

- Data transfer **out** (to internet): first 100 GB/month often free; beyond that ~$0.09/GB. If 20% of GETs serve 250 KB each: ~75 GB out → **~0** in first tier. If traffic grows, add ~$5–10/month later.

---

### 3.3 Compute: Spring Boot backend

Two practical options: **EC2** or **AWS App Runner**.

#### Option A — EC2 (single instance)

- **Instance:** `t3.medium` (2 vCPU, 4 GB RAM) — matches HikariCP and scheduled jobs (FCM retries, Firebase checks).
- **Storage:** 30 GB gp3 for OS + app + logs.

| Item | Unit | Quantity | Unit price (approx.) | Monthly cost (USD) |
|------|------|----------|------------------------|--------------------|
| t3.medium | hour | 730 | $0.0416 | **~30.37** |
| EBS gp3 30 GB | GB-month | 30 | $0.08 | **~2.40** |
| **EC2 subtotal** | | | | **~32.77** |

- **1-year Reserved:** instance ~$17–18/month (saves ~$12–13).

#### Option B — AWS App Runner

- **VCPU:** 1 vCPU, **Memory:** 2 GB (min viable for this app).
- **Active time:** assume 24/7 or high availability → 730 hours.

| Item | Unit | Quantity | Unit price (approx.) | Monthly cost (USD) |
|------|------|----------|------------------------|--------------------|
| Compute | vCPU-hour | 730 | $0.064 | **~46.72** |
| Memory | GB-hour | 1,460 | $0.007 | **~10.22** |
| **App Runner subtotal** | | | | **~56.94** |

App Runner is simpler (no OS/patches) but more expensive at this “always-on” usage. EC2 is cheaper for a single long-running process.

---

### 3.4 Data transfer (simplified)

- **In:** free.
- **Out to internet:** first 100 GB free (us-east-1); beyond that ~$0.09/GB.
- **Within region (e.g. EC2 ↔ RDS, EC2 ↔ S3):** free.
- **Assumption:** API + image traffic stays mostly within first 100 GB out → **$0** in baseline. If you exceed, add **~$5–15/month** as traffic grows.

---

### 3.5 Optional: Load balancer and HTTPS

- **Application Load Balancer (ALB):** ~$16/month + LCU usage (~$2–5) → **~$18–21/month** if you put ALB in front of EC2/App Runner and terminate TLS.
- **Alternative:** Single EC2 with public IP and Nginx/Caddy (or similar) for TLS → no ALB cost; certificate via Let’s Encrypt.

---

## 4. Monthly Cost Summary

### 4.1 Minimum viable (single EC2, no ALB)

| Service | Monthly (USD) |
|---------|----------------|
| RDS PostgreSQL (db.t3.medium + 50 GB gp3) | **~58.31** |
| S3 (storage + requests) | **~3.01** |
| EC2 (t3.medium + 30 GB EBS) | **~32.77** |
| Data transfer (within free tier) | **0** |
| **Total** | **~94** |

**Rounded: ~\$95–100/month** for 300 dealers and 15k DAU with PostgreSQL and S3 for images.

### 4.2 With ALB (recommended for production HTTPS)

| Service | Monthly (USD) |
|---------|----------------|
| RDS PostgreSQL | **~58.31** |
| S3 | **~3.01** |
| EC2 | **~32.77** |
| ALB | **~18–21** |
| **Total** | **~112–115** |

**Rounded: ~\$112–120/month.**

### 4.3 If using App Runner instead of EC2

| Service | Monthly (USD) |
|---------|----------------|
| RDS PostgreSQL | **~58.31** |
| S3 | **~3.01** |
| App Runner | **~56.94** |
| **Total** | **~118** |

**Rounded: ~\$118–120/month** (no ALB in this number; add ALB if you put it in front).

---

## 5. Cost Optimisation Tips

1. **Reserved capacity**  
   - RDS 1-year Reserved: save ~\$14–15/month on db.t3.medium.  
   - EC2 1-year Reserved: save ~\$12–13/month on t3.medium.

2. **RDS**  
   - If DB stays small and connection count is low, try **db.t3.small** (2 GB RAM) to save ~\$26/month on the instance.

3. **S3**  
   - Keep hot images (e.g. profile, recent documents) in **S3 Standard**.  
   - Move old ticket attachments or rarely accessed docs to **S3 Standard-IA** or **Glacier** to cut storage cost (with retrieval cost trade-off).

4. **Compute**  
   - Prefer **EC2** over App Runner for this “always-on” Spring Boot app to save ~\$24/month at current scale.  
   - Use **Savings Plans** (1-year) for further EC2/RDS discounts.

5. **Backups**  
   - RDS automated backups (e.g. 7-day retention) add roughly **$5–8/month** for 50 GB; include if you need point-in-time recovery.

---

## 6. What’s not included

- **Firebase/FCM:** no AWS cost; remains on Google.  
- **Admin panel / dealer app / configure app hosting:** not in this backend-only report.  
- **RabbitMQ / Redis:** not in production config; add ElastiCache/Amazon MQ if you introduce them later.  
- **WAF, Shield, GuardDuty:** add if you need advanced security.  
- **Detailed backup/DR:** only rough RDS backup note above.

---

## 7. Summary table (monthly USD)

| Scenario | Compute | RDS | S3 | Other | **Total** |
|----------|---------|-----|-----|--------|-----------|
| **EC2, no ALB** | ~33 | ~58 | ~3 | 0 | **~94–100** |
| **EC2 + ALB** | ~33 | ~58 | ~3 | ~19 | **~112–120** |
| **App Runner** | ~57 | ~58 | ~3 | 0 | **~118–120** |

**Bottom line:** For **~300 dealers** and **~15k daily active customers**, running this Spring Boot backend on AWS with **PostgreSQL (RDS)** and **S3 for images** is about **\$95–120 per month** depending on whether you use a load balancer and EC2 vs App Runner. With 1-year Reserved instances and smaller RDS/EC2 where appropriate, you can target **~\$70–90/month**.
