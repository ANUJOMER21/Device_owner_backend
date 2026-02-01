#!/usr/bin/env bash
# Build and push Docker image to ECR.
# Usage: ./scripts/deploy-push.sh
# Or:    ECR_URI=123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend ./scripts/deploy-push.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

AWS_REGION="${AWS_REGION:-eu-north-1}"
ECR_URI="${ECR_URI:-}"

if [ -z "$ECR_URI" ]; then
    echo "Set ECR_URI (e.g. 123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend)"
    echo "  export ECR_URI=YOUR_ACCOUNT.dkr.ecr.$AWS_REGION.amazonaws.com/da-emilocker-backend"
    exit 1
fi

echo "Building Docker image for linux/amd64 (EC2)..."
docker build --platform linux/amd64 -t da-emilocker-backend:latest .

echo "Logging into ECR..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "${ECR_URI%%/*}"

echo "Tagging and pushing..."
docker tag da-emilocker-backend:latest "$ECR_URI:latest"
docker push "$ECR_URI:latest"

echo "Done. On EC2 run: sudo docker pull $ECR_URI:latest && sudo docker restart da-emilocker-backend"
