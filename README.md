# DA EMI Locker Backend

Kotlin/Spring Boot backend for the DA EMI Locker app (dealers, customers, devices, FCM, S3, RDS).

## Quick start

- **Local:** `./start-backend.sh` or `./gradlew bootRun`
- **Health:** `curl http://localhost:8080/api/health`
- **Check:** `./check-backend.sh`

## Deployment

- See [GITHUB_ACTIONS_DEPLOY.md](GITHUB_ACTIONS_DEPLOY.md) for CI/CD.
- See [AWS_DEPLOYMENT_GUIDE.md](AWS_DEPLOYMENT_GUIDE.md) for AWS (EC2, RDS, S3, ECR).

## Secrets (never commit)

- `keys.txt` – local AWS/DB/Firebase notes (in `.gitignore`)
- `.env` – env vars for prod (in `.gitignore`)
- `firebase_config.json` – Firebase service account (in `.gitignore`)

Add these on your machine and on the server; they are not in the repo.
