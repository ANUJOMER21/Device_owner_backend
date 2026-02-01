# HTTPS Setup – Fix Mixed Content (Admin Panel → Backend)

The admin panel (Vercel, HTTPS) was blocked from calling the backend over HTTP. Enabling HTTPS on the backend fixes this.

---

## 1. Backend changes (already done)

- **SSL profile**: `application-ssl.properties` – when the `ssl` profile is active and `SSL_KEY_STORE` / `SSL_KEY_STORE_PASSWORD` are set, the server serves over **HTTPS** on the same port (e.g. 8080).
- **Keystore script**: `scripts/gen-keystore.sh` – generates a self-signed PKCS12 keystore for dev/test.
- **Deploy**: `scripts/deploy-update-ec2.sh` and GitHub Actions deploy workflow mount `keystore.p12` when the file exists on EC2.

---

## 2. Option A – Self-signed certificate (quick test)

**Limitation**: Browsers may still block or warn when the admin panel (HTTPS) calls `https://13.63.53.154:8080` with a self-signed cert, because the certificate is not trusted. For a **trusted** setup, use Option B (domain + real cert).

1. **Generate keystore** (on your machine or on EC2):

   ```bash
   chmod +x scripts/gen-keystore.sh
   ./scripts/gen-keystore.sh
   # Creates keystore.p12 in project root (password: changeit)
   ```

2. **Copy keystore to EC2**:

   ```bash
   scp -i emu_locker.pem keystore.p12 ec2-user@13.63.53.154:/tmp/
   ssh -i emu_locker.pem ec2-user@13.63.53.154
   sudo mkdir -p /opt/da-emilocker-backend
   sudo mv /tmp/keystore.p12 /opt/da-emilocker-backend/
   sudo chown root:root /opt/da-emilocker-backend/keystore.p12
   ```

3. **Add to EC2 `.env`** (`/opt/da-emilocker-backend/.env`):

   ```bash
   # Add or update:
   SPRING_PROFILES_ACTIVE=prod,ssl
   SSL_KEY_STORE=/app/keystore.p12
   SSL_KEY_STORE_PASSWORD=changeit
   ```

4. **Redeploy** (on EC2):

   ```bash
   ./deploy-update-ec2.sh
   ```

5. **Admin panel**: Point the API base URL to `https://13.63.53.154:8080`.  
   Browsers may show a certificate warning; you may need to open `https://13.63.53.154:8080/api/health` once and accept the exception. For production, use Option B.

---

## 3. Option B – Domain + real certificate (recommended for production)

Use a **domain** pointing to your EC2 IP (e.g. `api.yourdomain.com` → 13.63.53.154), then get a free certificate (e.g. Let’s Encrypt).

1. **Point a domain to EC2**  
   Create a DNS A record: `api.yourdomain.com` → `13.63.53.154`.

2. **On EC2, install certbot** (Amazon Linux 2023):

   ```bash
   sudo dnf install -y certbot
   sudo certbot certonly --standalone -d api.yourdomain.com
   # Cert and key in /etc/letsencrypt/live/api.yourdomain.com/
   ```

3. **Convert to PKCS12** (certbot gives PEM; Spring Boot needs a keystore):

   ```bash
   sudo openssl pkcs12 -export \
     -in /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem \
     -inkey /etc/letsencrypt/live/api.yourdomain.com/privkey.pem \
     -out /opt/da-emilocker-backend/keystore.p12 \
     -name tomcat \
     -passout pass:YOUR_KEYSTORE_PASSWORD
   sudo chown root:root /opt/da-emilocker-backend/keystore.p12
   ```

4. **EC2 `.env`**:

   ```bash
   SPRING_PROFILES_ACTIVE=prod,ssl
   SSL_KEY_STORE=/app/keystore.p12
   SSL_KEY_STORE_PASSWORD=YOUR_KEYSTORE_PASSWORD
   ```

5. **Deploy** so the container mounts the keystore (same as Option A); the deploy script and GitHub workflow already support this.

6. **Admin panel**: Set API base URL to `https://api.yourdomain.com:8080` (or use Nginx on 443 and proxy to 8080 so the URL is `https://api.yourdomain.com` without a port).

---

## 4. Summary

| Step | Action |
|------|--------|
| Backend | Use profile `ssl` and set `SSL_KEY_STORE`, `SSL_KEY_STORE_PASSWORD` (and optionally `SSL_KEY_ALIAS`, default `tomcat`). |
| Keystore | Self-signed: `./scripts/gen-keystore.sh`. Production: domain + certbot → PKCS12. |
| EC2 | Put `keystore.p12` in `/opt/da-emilocker-backend/`, add env vars, redeploy. |
| Admin panel | Set API URL to **https://** your backend host (and port if not 443). |

After this, the backend is served over HTTPS and the mixed-content block from the admin panel is resolved.
