# Free HTTPS Alternatives (No No-IP)

If No-IP didn’t work, use one of these:

1. **Cloudflare Tunnel** – No domain at all. Get a valid HTTPS URL in one command. Best for “get it working now.”
2. **DuckDNS** – Free stable subdomain (e.g. `emi-locker-api.duckdns.org`) + Let’s Encrypt. Best for production.

---

## Option 1: Cloudflare Tunnel (no domain, no cert)

You get a URL like `https://abc-xyz-123.trycloudflare.com` that forwards to your backend. **No domain, no certbot, no keystore.** The backend can stay on HTTP.

### On EC2

1. **SSH to EC2**
   ```bash
   ssh -i emu_locker.pem ec2-user@13.63.53.154
   ```

2. **Install cloudflared** (Amazon Linux 2023)
   ```bash
   sudo dnf install -y dnf-plugins-core
   sudo dnf config-manager --add-repo https://pkg.cloudflare.com/cloudflare-main.repo
   sudo dnf install -y cloudflared
   ```
   If that repo doesn’t work, use the standalone binary:
   ```bash
   wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64.rpm
   sudo rpm -i cloudflared-linux-amd64.rpm
   ```

3. **Start a quick tunnel** (forwards public HTTPS → localhost:8080)
   ```bash
   sudo cloudflared tunnel --url http://127.0.0.1:8080
   ```
   Leave this running. It will print a line like:
   ```text
   Your quick Tunnel has been created! Visit it at:
   https://random-words-here.trycloudflare.com
   ```

4. **Use that URL in the admin panel**  
   Set API base URL to: `https://random-words-here.trycloudflare.com`  
   (No `:8080` – Cloudflare listens on 443 and forwards to your backend.)

### Keep the tunnel running (optional)

- **Quick tunnel:** The URL **changes every time** you restart. To keep the same URL you’d need a Cloudflare account and a named tunnel (with a hostname).
- To run in background for now:
  ```bash
  sudo nohup cloudflared tunnel --url http://127.0.0.1:8080 > /tmp/cloudflared.log 2>&1 &
  ```
  Then check the log for the URL:
  ```bash
  grep -i trycloudflare /tmp/cloudflared.log
  ```

**Limitation:** Quick tunnels have a 200 concurrent-request limit. For production with a stable URL, use DuckDNS below or a named Cloudflare tunnel with a custom hostname.

---

## Option 2: DuckDNS (free subdomain + Let’s Encrypt)

You get a **stable** hostname like `emi-locker-api.duckdns.org` and a trusted certificate. No 30‑day confirmation like No-IP.

### Step 1: Create DuckDNS hostname

1. Go to **https://www.duckdns.org**
2. Sign in with Google, GitHub, etc.
3. Create a subdomain (e.g. `emi-locker-api`) → you get **emi-locker-api.duckdns.org**
4. Set the IP to **13.63.53.154** (your EC2 IP).  
   DuckDNS will show the current IP; click “update” if needed.

### Step 2: On EC2 – certificate and backend

**If port 80 is blocked (security group / firewall):** use the **DNS challenge** below – no port 80 needed.

#### Option A: HTTP challenge (needs port 80 open on EC2)

1. **SSH to EC2**, install certbot (see Option 1 in doc), then:
   ```bash
   sudo docker stop da-emilocker-backend
   sudo certbot certonly --standalone -d emi-locker-api.duckdns.org
   ```

#### Option B: DNS challenge (no port 80 – use this if HTTP doesn’t work)

1. **Get your DuckDNS token**  
   Go to **https://www.duckdns.org** → log in → you’ll see your token on the main page (long string).

2. **SSH to EC2**
   ```bash
   ssh -i emu_locker.pem ec2-user@13.63.53.154
   ```

3. **Install certbot and DuckDNS plugin**
   ```bash
   sudo dnf install -y python3 python3-pip
   sudo python3 -m pip install certbot certbot-dns-duckdns
   sudo ln -sf /usr/local/bin/certbot /usr/bin/certbot 2>/dev/null || true
   ```

4. **Create credentials file** (replace `YOUR_DUCKDNS_TOKEN` with the token from step 1)
   ```bash
   sudo mkdir -p /etc/letsencrypt
   echo "dns_duckdns_token = YOUR_DUCKDNS_TOKEN" | sudo tee /etc/letsencrypt/duckdns.ini
   sudo chmod 600 /etc/letsencrypt/duckdns.ini
   ```

5. **Get certificate with DNS challenge** (backend can stay running; no port 80 needed)
   ```bash
   sudo certbot certonly \
     --authenticator dns-duckdns \
     --dns-duckdns-credentials /etc/letsencrypt/duckdns.ini \
     -d emi-locker-api.duckdns.org
   ```
   Use a real email when asked. When it succeeds, certs are in `/etc/letsencrypt/live/emi-locker-api.duckdns.org/`.

6. **Convert to PKCS12 keystore (include full chain to avoid "chain validation failed")**
   ```bash
   LE_DIR="/etc/letsencrypt/live/emi-locker-api.duckdns.org"
   sudo openssl pkcs12 -export \
     -in "$LE_DIR/cert.pem" \
     -inkey "$LE_DIR/privkey.pem" \
     -certfile "$LE_DIR/chain.pem" \
     -out /opt/da-emilocker-backend/keystore.p12 \
     -name tomcat \
     -passout pass:changeit

   sudo chown root:root /opt/da-emilocker-backend/keystore.p12
   ```
   Using `cert.pem` + `chain.pem` (not only `fullchain.pem`) ensures the intermediate certificates are in the keystore so clients can validate the chain.

7. **Ensure `.env` has SSL**
   ```bash
   sudo nano /opt/da-emilocker-backend/.env
   ```
   Add or keep:
   ```env
   SPRING_PROFILES_ACTIVE=prod,ssl
   SSL_KEY_STORE=/app/keystore.p12
   SSL_KEY_STORE_PASSWORD=changeit
   ```

8. **Restart backend**
   ```bash
   ./deploy-update-ec2.sh
   ```
   (Or your usual Docker run with `-v /opt/da-emilocker-backend/keystore.p12:/app/keystore.p12:ro` and `--env-file /opt/da-emilocker-backend/.env`.)

### Step 3: Admin panel

Set API base URL to:
```text
https://emi-locker-api.duckdns.org:8080
```
(Use your actual DuckDNS hostname.)

---

## Summary

| Method              | URL example                    | Domain? | Stable URL? | Best for        |
|---------------------|---------------------------------|--------|-------------|-----------------|
| **Cloudflare Tunnel** | https://xxx.trycloudflare.com   | No     | No (changes each run) | Quick test      |
| **DuckDNS**         | https://emi-locker-api.duckdns.org:8080 | Free subdomain | Yes          | Production      |

Use **Cloudflare Tunnel** to get HTTPS working in a few minutes with no domain. Use **DuckDNS** when you want a stable, free hostname and a normal Let’s Encrypt setup.
