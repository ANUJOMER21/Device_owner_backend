# Firebase Status Check (EC2)

How to SSH to EC2, check why Firebase is not initialized, and fix it.

---

## 1. SSH to EC2

From your Mac (key and IP from your setup):

```bash
cd /Users/anujomer/Desktop/backend
chmod 400 emu_locker.pem
ssh -i emu_locker.pem ec2-user@13.63.53.154
```

Replace `13.63.53.154` with your EC2 public IP if different.

---

## 2. Check Firebase status via API

From any machine (after you have an admin JWT):

```bash
# Get admin JWT (replace YOUR_ADMIN_PASSWORD with your actual admin password)
curl -k -s -X POST https://emi-locker-api.duckdns.org:8080/api/admin/login \
  -H "Content-Type: application/json" \
  -d '{"password":"YOUR_ADMIN_PASSWORD"}' | jq -r '.token'

# Check Firebase status (replace YOUR_JWT with the token from above)
curl -k -s -H "Authorization: Bearer YOUR_JWT" \
  https://emi-locker-api.duckdns.org:8080/api/admin/firebase/status | jq .
```

**Expected when Firebase is OK:**
```json
{
  "initialized": true,
  "appCount": 1,
  "apps": ["[DEFAULT]"],
  "fcmEnabled": true
}
```

**When Firebase is not initialized:**
```json
{
  "initialized": false,
  "appCount": 0,
  "apps": [],
  "fcmEnabled": true
}
```

---

## 3. On EC2 – Why Firebase might not be initialized

Firebase initializes at startup only if:

1. **FCM is enabled** – `fcm.enabled=true` (prod profile sets this).
2. **Service account key path is set** – `fcm.service-account-key=file:/app/firebase_config.json` (prod profile).
3. **The file exists inside the container** – `/app/firebase_config.json` must be present and readable (mounted from host).

Run these on EC2 (after SSH) to verify.

### 3.1 Check if the file exists on the host

```bash
ls -la /opt/da-emilocker-backend/firebase_config.json
```

- If **No such file or directory** → the file was never copied to EC2 or was deleted. You must upload your Firebase service account JSON here (see **Fix** below).
- If the file exists → note size (should be a few KB). Proceed to 3.2.

### 3.2 Check if the file is mounted inside the container

```bash
sudo docker exec da-emilocker-backend ls -la /app/firebase_config.json
```

- If **No such file or directory** → the container was started without the volume mount. Recreate the container with the mount (see **Fix** below).
- If the file is listed → the mount is OK. Proceed to 3.3.

### 3.3 Check backend logs for Firebase errors

```bash
sudo docker logs da-emilocker-backend 2>&1 | grep -i firebase
```

Look for:

- `Firebase initialization started` – startup ran.
- `Firebase config file not found` – path wrong or file missing at startup.
- `Failed to load Google credentials` – file is not valid JSON or not a valid service account key.
- `Failed to initialize Firebase` – see the line after for the exact exception.
- `Firebase initialized successfully` – initialization succeeded.

Full recent logs (no filter):

```bash
sudo docker logs da-emilocker-backend --tail 150
```

### 3.4 Check Spring profile and FCM config

The app must run with the **prod** profile so it uses `file:/app/firebase_config.json`. Check `.env` on EC2:

```bash
sudo grep -E "SPRING_PROFILES|FCM|fcm" /opt/da-emilocker-backend/.env
```

You should see something like:

- `SPRING_PROFILES_ACTIVE=prod` or `prod,ssl`
- (FCM is enabled in `application-prod.properties` by default; no need for FCM vars in `.env` unless you override.)

---

## 4. Fix – Step by step

### Step 1: Ensure `firebase_config.json` exists on EC2

The file must be your **Firebase service account JSON** (from Firebase Console → Project Settings → Service accounts → Generate new private key).

**Option A – Upload from your Mac (if you have the file locally):**

```bash
# On your Mac (not EC2)
scp -i /Users/anujomer/Desktop/backend/emu_locker.pem /path/to/your/firebase_service_account.json ec2-user@13.63.53.154:/tmp/firebase_config.json
```

Then on EC2:

```bash
sudo mv /tmp/firebase_config.json /opt/da-emilocker-backend/firebase_config.json
sudo chown root:root /opt/da-emilocker-backend/firebase_config.json
sudo chmod 600 /opt/da-emilocker-backend/firebase_config.json
```

**Option B – Create/edit on EC2 (paste the JSON content):**

```bash
sudo nano /opt/da-emilocker-backend/firebase_config.json
```

Paste the full JSON (starts with `{"type":"service_account", ...}`), save and exit. Then:

```bash
sudo chown root:root /opt/da-emilocker-backend/firebase_config.json
sudo chmod 600 /opt/da-emilocker-backend/firebase_config.json
```

### Step 2: Verify file is valid JSON

```bash
sudo cat /opt/da-emilocker-backend/firebase_config.json | head -c 200
```

You should see `{"type":"service_account",` and other keys. If it looks like HTML or error text, the file is wrong.

### Step 3: Restart the backend so it picks up the file

If the file was missing before, the container may already have the volume mount (from deploy); restart is enough:

```bash
sudo docker stop da-emilocker-backend
sudo docker start da-emilocker-backend
```

Wait ~30 seconds, then check logs:

```bash
sudo docker logs da-emilocker-backend --tail 50 2>&1 | grep -i firebase
```

You should see `Firebase initialized successfully`.

### Step 4: If the container was created without the Firebase mount

Recreate the container with the same run options as your deploy (replace `ECR_URI` if yours is different):

```bash
export ECR_URI=651007120790.dkr.ecr.eu-north-1.amazonaws.com/da-emilocker-backend

sudo docker stop da-emilocker-backend
sudo docker rm da-emilocker-backend

SSL_VOL=""
[ -f /opt/da-emilocker-backend/keystore.p12 ] && SSL_VOL="-v /opt/da-emilocker-backend/keystore.p12:/app/keystore.p12:ro"

sudo docker run -d \
  --name da-emilocker-backend \
  --restart unless-stopped \
  -p 8080:8080 \
  $SSL_VOL \
  --env-file /opt/da-emilocker-backend/.env \
  -v /opt/da-emilocker-backend/firebase_config.json:/app/firebase_config.json:ro \
  $ECR_URI:latest
```

Wait ~30 seconds, then check Firebase status again via API (section 2).

---

## 5. Quick checklist

| Check | Command (on EC2) | OK when |
|-------|-------------------|--------|
| File on host | `ls -la /opt/da-emilocker-backend/firebase_config.json` | File exists, size &gt; 0 |
| File in container | `sudo docker exec da-emilocker-backend ls -la /app/firebase_config.json` | File exists |
| Firebase in logs | `sudo docker logs da-emilocker-backend 2>&1 \| grep -i firebase` | "Firebase initialized successfully" |
| API status | `curl -k -H "Authorization: Bearer JWT" https://emi-locker-api.duckdns.org:8080/api/admin/firebase/status` | `initialized: true`, `appCount: 1` |

---

## 6. Links

- **Admin login (get JWT):** `POST https://emi-locker-api.duckdns.org:8080/api/admin/login` with body `{"password":"YOUR_ADMIN_PASSWORD"}`.
- **Firebase status API:** `GET https://emi-locker-api.duckdns.org:8080/api/admin/firebase/status` with header `Authorization: Bearer YOUR_JWT`.
- Firebase Console (service account key): [Firebase Console](https://console.firebase.google.com/) → Project → Project settings → Service accounts → Generate new private key.
