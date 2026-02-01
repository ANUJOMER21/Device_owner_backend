#!/usr/bin/env bash
# Generate a self-signed PKCS12 keystore for HTTPS (dev/test or behind reverse proxy).
# For production with a public domain, use Let's Encrypt (certbot) and convert to PKCS12.
#
# Usage:
#   ./scripts/gen-keystore.sh
#   ./scripts/gen-keystore.sh /path/to/keystore.p12
#
# Output: keystore.p12 in project root (or path you pass). Use with application-ssl profile.

set -e

OUT="${1:-$(dirname "$0")/../keystore.p12}"
OUT_DIR="$(dirname "$OUT")"
mkdir -p "$OUT_DIR"
OUT="$(cd "$OUT_DIR" && pwd)/$(basename "$OUT")"

# Default alias and password (override for production)
ALIAS="${SSL_KEY_ALIAS:-tomcat}"
PASS="${SSL_KEY_STORE_PASSWORD:-changeit}"
DAYS="${SSL_VALID_DAYS:-365}"
CN="${SSL_CN:-localhost}"

echo "Generating self-signed keystore: $OUT"
echo "  CN=$CN, alias=$ALIAS, valid=$DAYS days"

keytool -genkeypair -alias "$ALIAS" \
  -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore "$OUT" \
  -storepass "$PASS" -keypass "$PASS" \
  -validity "$DAYS" \
  -dname "CN=$CN, OU=Backend, O=DA EMI Locker, L=Local, ST=State, C=IN" \
  -ext "SAN=DNS:localhost,IP:127.0.0.1"

echo "Done. To use:"
echo "  export SSL_KEY_STORE=$OUT"
echo "  export SSL_KEY_STORE_PASSWORD=$PASS"
echo "  java -jar app.jar --spring.profiles.active=prod,ssl"
echo ""
echo "For EC2 with a domain (e.g. api.yourdomain.com), set CN and SAN:"
echo "  SSL_CN=api.yourdomain.com ./scripts/gen-keystore.sh"
