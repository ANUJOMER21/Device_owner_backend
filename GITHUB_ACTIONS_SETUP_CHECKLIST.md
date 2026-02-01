# GitHub Actions Setup Checklist

Use this to configure GitHub so the **Deploy to AWS** workflow can build, push to ECR, and deploy to EC2.

---

## 1. Open Actions settings

1. Go to your repo: **https://github.com/ANUJOMER21/Emi_locker_backend**
2. Click **Settings** (repo settings, not your profile).
3. In the left sidebar: **Secrets and variables → Actions**.

---

## 2. Add repository secrets (required)

Click **New repository secret** and add these **four secrets** one by one.

| Secret name (exact) | What to put | Where you get it |
|--------------------|-------------|------------------|
| **AWS_ACCESS_KEY_ID** | Your IAM access key ID (starts with `AKIA...`) | AWS Console → IAM → Users → your user → Security credentials → Create access key. Copy the **Access key ID**. |
| **AWS_SECRET_ACCESS_KEY** | The secret key (shown only once when you create the key) | Same “Create access key” flow → copy the **Secret access key**. |
| **EC2_HOST** | Your EC2 public IP or hostname | e.g. `13.63.53.154` (no `http://`, no port). From EC2 → Instances → your instance → Public IPv4 address. |
| **SSH_PRIVATE_KEY** | Full contents of your `.pem` file | Open `emu_locker.pem` in a text editor. Copy **everything** from `-----BEGIN RSA PRIVATE KEY-----` (or `-----BEGIN OPENSSH PRIVATE KEY-----`) down to `-----END ... KEY-----`. Paste as the secret value. No extra spaces or blank lines at start/end. |

**Important for SSH_PRIVATE_KEY:**

- Include the first and last lines (BEGIN / END).
- Use the same key you use for `ssh -i emu_locker.pem ec2-user@13.63.53.154`.
- One secret; the whole file in one paste.

---

## 3. IAM user for GitHub (ECR push)

The access key you put in **AWS_ACCESS_KEY_ID** / **AWS_SECRET_ACCESS_KEY** must be for an IAM user that can push to ECR.

**Option A – Simple (broader permissions):**

1. IAM → Users → Create user (e.g. `github-ecr-deploy`).
2. Attach policy: **AmazonEC2ContainerRegistryPowerUser** (or **AWSConnector** if you use that).
3. Create **Access key** (programmatic). Use that key’s ID and secret for the two GitHub secrets above.

**Option B – Minimal (recommended):**

1. Create IAM user (e.g. `github-ecr-deploy`).
2. Attach inline or custom policy that allows:
   - `ecr:GetAuthorizationToken` (no resource restriction), and
   - For your ECR repo ARN: `ecr:BatchCheckLayerAvailability`, `ecr:GetDownloadUrlForLayer`, `ecr:BatchGetImage`, `ecr:PutImage`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`.
3. Create access key and use it in GitHub.

Your ECR repo: `651007120790.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend` (account `651007120790`, region `eu-north-1`).

---

## 4. Optional: repository variables

Only if you use a **different** ECR repo or region.

- **Settings → Secrets and variables → Actions → Variables** tab.
- **New repository variable**
  - Name: `ECR_URI`
  - Value: `ACCOUNT_ID.dkr.ecr.REGION.amazonaws.com/da-emilocker-backend`

The workflow file already has `ECR_URI` in `env`; if you add this variable, you’d need to change the workflow to use it (e.g. `vars.ECR_URI`). For your current ECR URI you **don’t need** to add this.

---

## 5. Other settings (no extra env vars)

- **Branch:** Workflow runs on push to **main**. If your default branch is `master`, either rename it to `main` in GitHub or change in `.github/workflows/deploy.yml`: `branches: [master]`.
- **Workflow permissions:** Default “Read repository contents and packages permissions” is enough; no extra env or permissions needed for this workflow.
- **EC2:** Security group must allow **SSH (22)** from the internet (or from GitHub’s IPs). EC2 instance role must allow **ECR read** (e.g. `AmazonEC2ContainerRegistryReadOnly`) so it can `docker pull`.

---

## 6. What the workflow expects on EC2 (no GitHub config)

These are on the **server**, not in GitHub:

- `/opt/da-emilocker-backend/.env` – env vars for the app (DB, S3, JWT, etc.).
- `/opt/da-emilocker-backend/firebase_config.json` – Firebase service account JSON (for FCM).

The workflow does **not** need these as GitHub secrets; it uses the existing files on EC2.

---

## 7. Verify

1. Save all four secrets.
2. Go to **Actions** → **Deploy to AWS**.
3. Click **Run workflow** → choose branch **main** → **Run workflow**.
4. Open the run and check:
   - “Build and push” logs into AWS, builds the image, pushes to ECR.
   - “Deploy to EC2” SSHs to EC2, pulls the image, restarts the container.

If something fails, see **GITHUB_ACTIONS_DEPLOY.md** → Troubleshooting.

---

## Quick reference

| Type | Name | Required? |
|------|------|-----------|
| Secret | `AWS_ACCESS_KEY_ID` | Yes |
| Secret | `AWS_SECRET_ACCESS_KEY` | Yes |
| Secret | `EC2_HOST` | Yes |
| Secret | `SSH_PRIVATE_KEY` | Yes |
| Variable | `ECR_URI` | No (workflow has default) |

**No other env variables or GitHub settings are required** for this workflow.
