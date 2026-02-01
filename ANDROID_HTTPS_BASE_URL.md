# Android App – Fix "Chain Validation Failed" with HTTPS Base URL

When your Android app uses base URL **`https://13.63.53.154:8080/`** and you get **"chain validation failed"**, it's because:

1. **Certificate is for the domain, not the IP** – The server's Let's Encrypt certificate is issued for **`emi-locker-api.duckdns.org`**. Android validates that the certificate's hostname matches the URL you connect to. Connecting to `13.63.53.154` does not match `emi-locker-api.duckdns.org`, so validation fails.
2. **Server must send full chain** – The backend keystore must include the full certificate chain (see [FREE_HTTPS_ALTERNATIVES.md](FREE_HTTPS_ALTERNATIVES.md) – use `cert.pem` + `chain.pem` when creating the PKCS12).

---

## Recommended: Use the domain in the Android app

**Change base URL from:**
```text
https://13.63.53.154:8080/
```

**To:**
```text
https://emi-locker-api.duckdns.org:8080/
```

- Ensure **DuckDNS** has the correct IP (13.63.53.154) for `emi-locker-api.duckdns.org`.
- The certificate then matches the hostname and Android's chain validation will succeed (assuming the server sends the full chain).

No code changes in the app are needed – only the base URL.

---

## If you must use the IP (e.g. dev / same network)

Let's Encrypt does **not** issue certificates for IP addresses. So `https://13.63.53.154:8080/` will always have a hostname mismatch with a Let's Encrypt cert.

Options:

1. **Use the domain** (above) – best for production and most cases.
2. **Dev only – trust the server cert for that host**  
   Configure your HTTP client (OkHttp/Retrofit) to trust the certificate when connecting to the IP. This is **insecure** and only for development.

### Example: OkHttp – trust all for a specific host (dev only)

```kotlin
// WARNING: Insecure – use only in debug builds, never in production
fun createUnsafeOkHttpClient(): OkHttpClient {
    val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )
    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }
    return OkHttpClient.Builder()
        .hostnameVerifier { _, _ -> true }
        .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
        .build()
}
```

Use this client only for debug builds and only when connecting to your dev server; never ship it for production.

---

## Summary

| Base URL | Result |
|----------|--------|
| `https://emi-locker-api.duckdns.org:8080/` | ✅ Certificate matches; use this in the app. |
| `https://13.63.53.154:8080/` | ❌ Hostname mismatch → chain validation failed (unless you use dev-only trust, which is insecure). |

Use **`https://emi-locker-api.duckdns.org:8080/`** as the base URL in your Android app to fix the chain validation error.

---

## Still "chain validation failed" with the domain?

Then the **server is not sending the full certificate chain** (intermediates). Fix it on EC2:

### 1. Verify what the server sends (from your Mac or any machine)

```bash
echo | openssl s_client -connect emi-locker-api.duckdns.org:8080 -servername emi-locker-api.duckdns.org 2>/dev/null | openssl x509 -noout -issuer -subject
```

You should see **at least two** certificates (run without the last `openssl x509` to see all). If you only see one cert, the chain is incomplete.

Full chain dump:

```bash
openssl s_client -connect emi-locker-api.duckdns.org:8080 -servername emi-locker-api.duckdns.org -showcerts </dev/null 2>/dev/null
```

### 2. On EC2 – re-create PKCS12 with full chain and restart

SSH to EC2, then run (replace domain if different):

```bash
LE_DIR="/etc/letsencrypt/live/emi-locker-api.duckdns.org"

# Method A: cert + chain (recommended)
sudo openssl pkcs12 -export \
  -in "$LE_DIR/cert.pem" \
  -inkey "$LE_DIR/privkey.pem" \
  -certfile "$LE_DIR/chain.pem" \
  -out /opt/da-emilocker-backend/keystore.p12 \
  -name tomcat \
  -passout pass:changeit

# If Method A still fails, try Method B: single PEM (leaf then intermediates)
sudo cat "$LE_DIR/cert.pem" "$LE_DIR/chain.pem" > /tmp/fullchain_combined.pem
sudo openssl pkcs12 -export \
  -in /tmp/fullchain_combined.pem \
  -inkey "$LE_DIR/privkey.pem" \
  -out /opt/da-emilocker-backend/keystore.p12 \
  -name tomcat \
  -passout pass:changeit
sudo rm -f /tmp/fullchain_combined.pem

sudo chown root:root /opt/da-emilocker-backend/keystore.p12
```

Restart the backend so it loads the new keystore:

```bash
sudo docker stop da-emilocker-backend
sudo docker start da-emilocker-backend
```

Wait ~30 seconds, then test again from the Android app (base URL `https://emi-locker-api.duckdns.org:8080/`).

### 3. Confirm keystore has multiple certs (on EC2)

```bash
openssl pkcs12 -nokeys -info -in /opt/da-emilocker-backend/keystore.p12 -passin pass:changeit 2>/dev/null | grep -c "BEGIN CERTIFICATE"
```

You should see **2** or more (leaf + intermediate(s)).

### 4. Server verified OK but Android still fails?

If **Mac** shows:
- `openssl s_client ... -showcerts` → **2 certs** in the chain and **Verification: OK**
- EC2 keystore: `grep -c "BEGIN CERTIFICATE"` → **2**

then the **server and certificate chain are correct**. The problem is on the **Android side**.

**Android-side fixes:**

1. **Check Network Security Config**  
   Your current config has `base-config` with only `<certificates src="system" />`. That is correct for Let's Encrypt, but some devices still fail chain validation. Two options:

   **Option A – Add certificate pinning for your API domain (recommended)**  
   Add a `domain-config` for `emi-locker-api.duckdns.org` with a `pin-set`. Android will then trust your server's certificate directly for that domain and chain validation will succeed.

   **Get the pin (run on Mac):**
   ```bash
   echo | openssl s_client -connect emi-locker-api.duckdns.org:8080 -servername emi-locker-api.duckdns.org 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
   ```
   Copy the output (e.g. `abc123...=`).

   **Add this inside `<network-security-config>` (before `</network-security-config>`):**
   ```xml
   <!-- Pin API host so chain validation succeeds on all devices -->
   <domain-config cleartextTrafficPermitted="false">
       <domain includeSubdomains="true">emi-locker-api.duckdns.org</domain>
       <pin-set expiration="2026-05-02">
           <pin digest="SHA-256">REPLACE_WITH_PIN_FROM_COMMAND_ABOVE</pin>
       </pin-set>
   </domain-config>
   ```
   Replace `REPLACE_WITH_PIN_FROM_COMMAND_ABOVE` with the value from the command. Set `expiration` to your cert's NotAfter (e.g. 2026-05-02 for Let's Encrypt). When you renew the cert, update the pin (and optionally add a backup pin for the new cert).

   **Option B – Trust user CAs in base-config (less secure)**  
   If you don't want pinning, you can try adding `<certificates src="user" />` to `base-config` so the system trusts user-installed CAs too. That usually does **not** fix "chain validation failed" for Let's Encrypt; pinning (Option A) does.

2. **Certificate pinning (recommended for production)**  
   Pin the server's certificate or public key so the app explicitly trusts your backend and is not affected by device trust-store quirks. With OkHttp:
   ```kotlin
   val certificatePinner = CertificatePinner.Builder()
       .add("emi-locker-api.duckdns.org", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
       .build()
   // Get the correct pin: openssl s_client -connect emi-locker-api.duckdns.org:8080 -servername emi-locker-api.duckdns.org </dev/null 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
   val client = OkHttpClient.Builder().certificatePinner(certificatePinner).build()
   ```
   Replace the pin with the actual value from the command above (run from Mac/Linux).

3. **Pin at TrustManager level (fixes "chain validation failed" when pin-set in XML didn’t help)**  
   Android’s normal chain validation runs before `pin-set` is applied, so if the device doesn’t trust the chain, validation fails before the pin is checked. To fix that, use a **custom TrustManager** that accepts your server’s certificate for the API host by pinning its public key. Only that cert is trusted for that host; other hosts still use the system CA.

   **Get the pin (run on Mac):**
   ```bash
   echo | openssl s_client -connect emi-locker-api.duckdns.org:8080 -servername emi-locker-api.duckdns.org 2>/dev/null | openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
   ```
   Copy the output (e.g. `abc123...=`).

   **Kotlin – custom TrustManager that pins your API server (use this for your Retrofit/OkHttp client):**
   ```kotlin
   import java.security.MessageDigest
   import java.security.SecureRandom
   import java.security.cert.X509Certificate
   import javax.net.ssl.SSLContext
   import javax.net.ssl.TrustManager
   import javax.net.ssl.TrustManagerFactory
   import javax.net.ssl.X509TrustManager
   import android.util.Base64
   import okhttp3.OkHttpClient

   // Replace with the pin from the command above
   private const val API_PIN = "YOUR_PIN_FROM_OPENSSL_COMMAND"

   fun createOkHttpClientForApi(): OkHttpClient {
       val defaultTm = getDefaultTrustManager()
       val pinningTm = object : X509TrustManager {
           override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
               defaultTm.checkClientTrusted(chain, authType)
           }
           override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
               if (chain.isNotEmpty()) {
                   val pin = sha256Base64(chain[0].publicKey.encoded)
                   if (pin == API_PIN) return // accept: our server cert
               }
               defaultTm.checkServerTrusted(chain, authType)
           }
           override fun getAcceptedIssuers(): Array<X509Certificate> = defaultTm.acceptedIssuers ?: arrayOf()
       }
       val sslContext = SSLContext.getInstance("TLS").apply {
           init(null, arrayOf<TrustManager>(pinningTm), SecureRandom())
       }
       return OkHttpClient.Builder()
           .sslSocketFactory(sslContext.socketFactory, pinningTm)
           .hostnameVerifier { hostname, _ -> hostname == "emi-locker-api.duckdns.org" }
           .build()
   }

   private fun getDefaultTrustManager(): X509TrustManager {
       val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
       tmf.init(null) // default system trust store
       return tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
   }

   private fun sha256Base64(input: ByteArray): String {
       return Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(input), Base64.NO_WRAP)
   }
   ```
   Use `createOkHttpClientForApi()` only for requests to `https://emi-locker-api.duckdns.org:8080/` (e.g. set this client on your Retrofit instance that calls the API). When you renew the Let’s Encrypt cert, re-run the openssl command and update `API_PIN` if the key changed.

4. **Debug only – trust all for this host**  
   If you need it working immediately and don’t want to pin, use the "trust all" OkHttp client from the "If you must use the IP" section only for the API base URL and only in debug builds. Do not ship in production.

5. **Older Android devices**  
   Very old Android versions may not have Let's Encrypt's root (ISRG Root X1). Use option 3 (pinning TrustManager) so the app trusts your server’s cert for that host.
