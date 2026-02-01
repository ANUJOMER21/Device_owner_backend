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

# Optional: HTTPS (mount keystore when present and .env has SSL_KEY_STORE, SSL_KEY_STORE_PASSWORD, spring.profiles.include=ssl or SPRING_PROFILES_ACTIVE=prod,ssl)
KEYSTORE_PATH="${KEYSTORE_PATH:-/opt/da-emilocker-backend/keystore.p12}"
SSL_VOL=""
if [ -f "$KEYSTORE_PATH" ]; then
  SSL_VOL="-v $KEYSTORE_PATH:/app/keystore.p12:ro"
  echo "Using HTTPS keystore: $KEYSTORE_PATH"
fi

echo "Starting new container..."
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  -p 8080:8080 \
  $SSL_VOL \
  --env-file "$ENV_FILE" \
  "$ECR_URI:latest"

echo "Deployed. Health: curl http://localhost:8080/api/health (or https if SSL is configured)"
