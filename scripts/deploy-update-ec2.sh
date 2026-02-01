#!/usr/bin/env bash
# Pull latest image and restart container - RUN ON EC2.
# Copy to EC2: scp scripts/deploy-update-ec2.sh ec2-user@<EC2_IP>:~/
# Usage: ./deploy-update-ec2.sh  (or sudo if not in docker group)
# Or:    ECR_URI=... AWS_REGION=eu-north-1 ./deploy-update-ec2.sh

set -e

AWS_REGION="${AWS_REGION:-eu-north-1}"
ECR_URI="${ECR_URI:-123456789.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend}"
ENV_FILE="${ENV_FILE:-/opt/da-emilocker-backend/.env}"
CONTAINER_NAME="da-emilocker-backend"

echo "Logging into ECR..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "${ECR_URI%%/*}"

echo "Pulling latest image..."
docker pull "$ECR_URI:latest"

echo "Stopping old container..."
docker stop "$CONTAINER_NAME" 2>/dev/null || true
docker rm "$CONTAINER_NAME" 2>/dev/null || true

echo "Starting new container..."
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  -p 8080:8080 \
  --env-file "$ENV_FILE" \
  "$ECR_URI:latest"

echo "Deployed. Health: curl http://localhost:8080/api/health"
