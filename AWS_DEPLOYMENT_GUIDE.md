# AWS Deployment Guide – DA EMI Locker Backend

Complete guide to deploy the Spring Boot backend on AWS with S3, RDS PostgreSQL, Docker, and EC2.

---

## Architecture Overview

```
┌─────────────┐     ┌─────────────┐     ┌──────────────────┐
│  Admin/App  │────▶│  EC2 (Docker)│────▶│  RDS PostgreSQL  │
│   Clients   │     │   Backend    │     │   (da_emilocker) │
└─────────────┘     └──────┬───────┘     └──────────────────┘
                           │
                           ▼
                  ┌──────────────────┐
                  │   S3 Bucket      │
                  │ (APK, images)    │
                  └──────────────────┘
```

**Components:**
- **EC2**: Runs the backend in a Docker container
- **RDS PostgreSQL**: Database (same VPC as EC2, no public access)
- **S3**: APK and image uploads (see `AWS_S3_SETUP_GUIDE.md`)
- **ECR**: Docker image registry

---

## Prerequisites

- AWS account
- AWS CLI installed and configured (`aws configure`)
- Docker installed locally
- Git (for cloning repo)
- Existing S3 bucket and RDS PostgreSQL (or follow setup below)

---

## Part 1: AWS Resources Setup

### 1.1 S3 Bucket

Follow `AWS_S3_SETUP_GUIDE.md` to create the S3 bucket (e.g. `da-emilocker-uploads`).

### 1.2 RDS PostgreSQL

Follow `AWS_RDS_POSTGRES_SETUP.md` with these production settings:

| Setting | Production Value |
|---------|------------------|
| Public access | **No** (EC2 in same VPC) |
| VPC | Default or same VPC as EC2 |
| Security group | Create `da-emilocker-db-sg` (see below) |
| Initial database | `da_emilocker` or use `postgres` |

**RDS Security Group** – allow EC2 only:
- Type: PostgreSQL, Port: 5432
- Source: Security group of EC2 (e.g. `sg-ec2-backend`)

### 1.3 EC2 Security Group

1. **EC2** → **Security Groups** → **Create security group**
2. Name: `da-emilocker-backend-sg`
3. **Inbound rules**:

| Type | Port | Source | Use |
|------|------|--------|-----|
| SSH | 22 | Your IP | Admin access |
| Custom TCP | 8080 | 0.0.0.0/0 | Backend API (or restrict to LB/your domain) |
| Custom TCP | 443 | 0.0.0.0/0 | HTTPS (if using Nginx) |

4. **Outbound**: All traffic (default)

5. **Edit RDS security group** – add inbound rule:
   - Type: PostgreSQL, Port: 5432
   - Source: `da-emilocker-backend-sg` (EC2 security group)

---

## Part 2: EC2 Instance Setup

### 2.1 Launch EC2

1. **EC2** → **Launch Instance**
2. **Name**: `da-emilocker-backend`
3. **AMI**: Amazon Linux 2023
4. **Instance type**: `t3.small` (or `t3.micro` for dev)
5. **Key pair**: Create new or select existing (save `.pem` file)
6. **Network**:
   - VPC: Same as RDS
   - Subnet: Public subnet (for SSH access)
   - Security group: `da-emilocker-backend-sg`
7. **Storage**: 20 GB gp3
8. **Launch**

### 2.2 Connect to EC2

```bash
chmod 400 your-key.pem
ssh -i your-key.pem ec2-user@<EC2_PUBLIC_IP>
```

### 2.3 Install Docker on EC2

```bash
sudo yum update -y
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user
# Log out and back in for docker group to apply
```

---

## Part 3: Docker Image – Build and Push to ECR

### 3.1 Create ECR Repository

```bash
aws ecr create-repository --repository-name da-emilocker-backend --region eu-north-1
```

Note the repository URI: `123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend`

### 3.2 Authenticate Docker to ECR

```bash
aws ecr get-login-password --region eu-north-1 | docker login --username AWS --password-stdin 123456789.dkr.ecr.eu-north-1.amazonaws.com
```

Replace `123456789` with your AWS account ID.

### 3.3 Build Docker Image Locally

```bash
cd /path/to/backend

# Build (from project root)
docker build -t da-emilocker-backend:latest .

# Tag for ECR
docker tag da-emilocker-backend:latest 123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend:latest
```

### 3.4 Push to ECR

```bash
docker push 123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend:latest
```

---

## Part 4: Deploy on EC2

### 4.1 Create Environment File on EC2

SSH to EC2, then:

```bash
sudo mkdir -p /opt/da-emilocker-backend
sudo tee /opt/da-emilocker-backend/.env << 'EOF'
# Database (RDS - use private endpoint from RDS console)
DB_HOST=da-emilocker-db.xxxxx.eu-north-1.rds.amazonaws.com
DB_PORT=5432
DB_NAME=da_emilocker
DB_USERNAME=postgres
DB_PASSWORD=YOUR_RDS_PASSWORD
DB_SSLMODE=require

# S3
AWS_S3_REGION=eu-north-1
AWS_S3_BUCKET=da-emilocker-uploads
AWS_ACCESS_KEY=YOUR_S3_ACCESS_KEY
AWS_SECRET_KEY=YOUR_S3_SECRET_KEY

# JWT
JWT_SECRET=your-256-bit-secret-key-change-in-production-minimum-32-chars

# Admin
ADMIN_PASSWORD=your-admin-password

# Profile
SPRING_PROFILES_ACTIVE=prod
EOF

sudo chmod 600 /opt/da-emilocker-backend/.env
```

Replace placeholders with real values. Get RDS endpoint from **RDS** → **Databases** → your instance → **Connectivity**.

### 4.2 Authenticate EC2 to ECR

EC2 needs an IAM role to pull from ECR:

1. **IAM** → **Roles** → **Create role**
2. Trusted entity: AWS service → EC2
3. Permissions: attach `AmazonEC2ContainerRegistryReadOnly`
4. Add inline policy for `ecr:GetAuthorizationToken`:
   - **Add permissions** → **Create inline policy** → JSON:
   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Action": "ecr:GetAuthorizationToken",
       "Resource": "*"
     }]
   }
   ```
   - Name: `ecr-login`
5. Name role: `da-emilocker-ec2-ecr-role`
6. **EC2** → **Instances** → select instance → **Actions** → **Security** → **Modify IAM role** → attach this role

### 4.3 Pull and Run Container on EC2

```bash
# Login to ECR (one-time per session, or use IAM role)
aws ecr get-login-password --region eu-north-1 | sudo docker login --username AWS --password-stdin 123456789.dkr.ecr.eu-north-1.amazonaws.com

# Pull image
sudo docker pull 123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend:latest

# Run
sudo docker run -d \
  --name da-emilocker-backend \
  --restart unless-stopped \
  -p 8080:8080 \
  --env-file /opt/da-emilocker-backend/.env \
  123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend:latest
```

### 4.4 Verify

```bash
curl http://localhost:8080/api/health
curl http://localhost:8080/api/health/db
```

From your machine:
```bash
curl http://<EC2_PUBLIC_IP>:8080/api/health
```

---

## Part 5: Systemd Service (Optional – Auto-restart)

Create a systemd unit for cleaner management:

```bash
sudo tee /etc/systemd/system/da-emilocker-backend.service << 'EOF'
[Unit]
Description=DA EMI Locker Backend
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStartPre=-/usr/bin/docker stop da-emilocker-backend
ExecStartPre=-/usr/bin/docker rm da-emilocker-backend
ExecStartPre=/usr/bin/docker pull 123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend:latest
ExecStart=/usr/bin/docker run --name da-emilocker-backend -d --restart unless-stopped -p 8080:8080 --env-file /opt/da-emilocker-backend/.env 123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend:latest
ExecStop=/usr/bin/docker stop da-emilocker-backend

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable da-emilocker-backend
sudo systemctl start da-emilocker-backend
```

Replace `123456789` with your AWS account ID.

---

## Part 6: HTTPS with Nginx (Optional)

### 6.1 Install Nginx and Certbot

```bash
sudo yum install -y nginx
sudo amazon-linux-extras install -y nginx1
# Or: sudo dnf install -y nginx
```

### 6.2 Configure Nginx

```bash
sudo tee /etc/nginx/conf.d/da-emilocker.conf << 'EOF'
server {
    listen 80;
    server_name api.yourdomain.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

sudo nginx -t && sudo systemctl restart nginx
```

### 6.3 SSL with Let's Encrypt

```bash
sudo yum install -y certbot python3-certbot-nginx
sudo certbot --nginx -d api.yourdomain.com
```

---

## Part 7: Deployment Scripts

### 7.1 Build and Push (run locally)

```bash
export ECR_URI=123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend
./scripts/deploy-push.sh
```

### 7.2 Update on EC2 (run on EC2)

Copy the script to EC2:
```bash
scp -i your-key.pem scripts/deploy-update-ec2.sh ec2-user@<EC2_IP>:~/
```

On EC2:
```bash
chmod +x deploy-update-ec2.sh
export ECR_URI=123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend
./deploy-update-ec2.sh
```

---

## Part 8: Environment Variables Reference

| Variable | Required | Description |
|----------|----------|-------------|
| DB_HOST | Yes | RDS endpoint |
| DB_PORT | Yes | 5432 |
| DB_NAME | Yes | `da_emilocker` or `postgres` |
| DB_USERNAME | Yes | RDS master username |
| DB_PASSWORD | Yes | RDS master password |
| DB_SSLMODE | No | `require` for RDS |
| AWS_S3_REGION | Yes | e.g. `eu-north-1` |
| AWS_S3_BUCKET | Yes | Bucket name |
| AWS_ACCESS_KEY | Yes | IAM access key for S3 |
| AWS_SECRET_KEY | Yes | IAM secret key |
| JWT_SECRET | Yes | 32+ char secret |
| ADMIN_PASSWORD | Yes | Admin panel password |
| SPRING_PROFILES_ACTIVE | Yes | `prod` |
| SERVER_PORT | No | 8080 (default) |

---

## Part 9: Database Migrations

Flyway runs automatically on app startup. Ensure:
- `da_emilocker` database exists on RDS (run `./create-rds-da-emilocker-db.sh` or create manually)
- Or use `DB_NAME=postgres` and tables will be created in the default database

---

## Part 10: Cost Estimate

| Resource | Type | Approx. Monthly |
|----------|------|-----------------|
| EC2 | t3.small | ~$15 |
| RDS | db.t3.micro | ~$15 |
| S3 | 10 GB | ~$0.25 |
| ECR | 1 GB | ~$0.10 |
| **Total** | | ~$30–35 |

---

## Troubleshooting

| Issue | Check |
|-------|-------|
| Container exits | `sudo docker logs da-emilocker-backend` |
| DB connection failed | RDS security group allows EC2 SG on 5432; same VPC |
| S3 upload fails | IAM keys valid; bucket name correct |
| 502 Bad Gateway | Backend running on 8080; Nginx proxy_pass correct |

---

## Quick Reference

- **Health**: `GET /api/health`
- **DB Health**: `GET /api/health/db`
- **Admin Panel**: Point to `http://<EC2_IP>:8080` or your domain
