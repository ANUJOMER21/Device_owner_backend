# GitHub Actions: Deploy to AWS

This repo uses a GitHub Actions workflow to build the backend, push the image to Amazon ECR, and update the running container on EC2 whenever you push to `main`.

---

## What the workflow does

1. **Trigger**: Runs on every push to `main` (and can be run manually from the Actions tab).
2. **Build**: Builds the Docker image for `linux/amd64` (so EC2 can run it).
3. **Push**: Logs into ECR and pushes the image as `:latest`.
4. **Deploy**: SSHs into your EC2 instance, pulls the new image, restarts the `da-emilocker-backend` container with the same env file.

After a successful run, your EC2 server is serving the new code.

---

## One-time setup

### 1. GitHub repository secrets

In your GitHub repo: **Settings → Secrets and variables → Actions → New repository secret**. Add:

| Secret name | Description | Example |
|-------------|-------------|---------|
| `AWS_ACCESS_KEY_ID` | IAM access key that can push to ECR | `AKIA...` |
| `AWS_SECRET_ACCESS_KEY` | IAM secret key | (from IAM → Users → Security credentials → Create access key) |
| `EC2_HOST` | EC2 public IP or hostname | `13.63.53.154` |
| `SSH_PRIVATE_KEY` | Full contents of your `.pem` file | Paste entire content of `emu_locker.pem` |

**IAM user for ECR (GitHub runner):**

- Create an IAM user (e.g. `github-ecr-push`) with programmatic access.
- Attach a policy that allows:
  - `ecr:GetAuthorizationToken`
  - On the repo: `ecr:BatchCheckLayerAvailability`, `ecr:GetDownloadUrlForLayer`, `ecr:BatchGetImage`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`

Or use the managed policy **AmazonEC2ContainerRegistryPowerUser** (or a custom policy scoped to your ECR repo).

**SSH key:**

- `SSH_PRIVATE_KEY` must be the **entire** private key (including `-----BEGIN ... KEY-----` and `-----END ... KEY-----`). No extra spaces; one secret per key.

### 2. EC2 requirements

- **IAM role** on the EC2 instance with ECR read (e.g. `AmazonEC2ContainerRegistryReadOnly`) so it can `docker pull` from ECR.
- **Security group**: Inbound SSH (22) from GitHub’s IPs or from anywhere (0.0.0/0) if you accept the risk. Restricting to [GitHub’s IP ranges](https://api.github.com/meta) is safer.
- **Container and env**: The workflow assumes the container name is `da-emilocker-backend` and the env file is `/opt/da-emilocker-backend/.env` (as in the manual deploy guide).

### 3. Optional: override ECR URI

Default ECR image is:

`651007120790.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend`

To use another repo or region, add a **repository variable** (Settings → Secrets and variables → Actions → Variables):

- Name: `ECR_URI`
- Value: `ACCOUNT.dkr.ecr.REGION.amazonaws.com/da-emilocker-backend`

Then in the workflow you’d reference it (e.g. `env.ECR_URI` from a var). The current workflow uses a fixed `ECR_URI` in `env`; you can change that line to use a variable if you add one.

---

## How to run

- **Automatic**: Push (or merge) to `main`. The “Deploy to AWS” workflow runs.
- **Manual**: **Actions → Deploy to AWS → Run workflow** (branch: `main`).

---

## Branch

The workflow is set to `branches: [main]`. If your default branch is `master`, either:

- Change in the workflow file: `branches: [master]`, or  
- Rename the branch to `main` in GitHub.

---

## Troubleshooting

| Issue | What to check |
|-------|----------------|
| **Build fails** | Build log for Gradle/Docker errors. Run `./gradlew bootJar` and `docker build --platform linux/amd64 .` locally. |
| **ECR push denied** | IAM user used by GitHub (AWS_ACCESS_KEY_ID) needs ECR push permissions; repo URL and region must match. |
| **SSH connection failed** | EC2_HOST correct, security group allows SSH from runner, SSH_PRIVATE_KEY is full PEM and has no extra newlines. |
| **EC2: docker pull denied** | EC2 instance IAM role has ECR read (e.g. `AmazonEC2ContainerRegistryReadOnly`). |
| **Container exits after deploy** | On EC2: `sudo docker logs da-emilocker-backend`. Check DB, env file, and RDS security group. |

---

## Manual deploy (without GitHub Actions)

If you prefer to deploy from your machine:

```bash
export ECR_URI=651007120790.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend
./scripts/deploy-push.sh
```

Then on EC2:

```bash
sudo aws ecr get-login-password --region eu-north-1 | sudo docker login --username AWS --password-stdin 651007120790.dkr.ecr.eu-north-1.amazonaws.com
sudo docker pull $ECR_URI:latest
sudo docker stop da-emilocker-backend 2>/dev/null || true
sudo docker rm da-emilocker-backend 2>/dev/null || true
sudo docker run -d --name da-emilocker-backend --restart unless-stopped -p 8080:8080 --env-file /opt/da-emilocker-backend/.env $ECR_URI:latest
```

Or copy and run `scripts/deploy-update-ec2.sh` on EC2 (after setting `ECR_URI` and `AWS_REGION`).

---

## Quick reference

- **Workflow file**: `.github/workflows/deploy.yml`
- **Trigger**: Push to `main` or “Run workflow”
- **Secrets**: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `EC2_HOST`, `SSH_PRIVATE_KEY`
- **Health after deploy**: `curl http://<EC2_HOST>:8080/api/health`
