# AWS S3 Setup Guide for DA EMI Locker Backend

This guide walks you through creating the required S3 bucket and IAM configuration for:

- **Device Owner APK** uploads (used in provisioning QR codes)
- **Image uploads** (customer photos, Aadhar, PAN, signatures, ticket attachments) via admin panel

---

## Prerequisites

- AWS account
- AWS CLI (optional, for testing)

---

## Step 1: Create S3 Bucket

1. Log in to [AWS Console](https://console.aws.amazon.com/) and go to **S3**.

2. Click **Create bucket**.

3. Configure:
   - **Bucket name**: e.g. `da-emilocker-uploads` (must be globally unique)
   - **AWS Region**: Choose your region (e.g. `ap-south-1` for Mumbai)
   - **Block Public Access**: Uncheck "Block all public access" if you need public URLs for APK download and images. The provisioning QR requires a publicly accessible APK URL.
   - Alternatively, use a **bucket policy** to allow public read for specific prefixes only (see Step 3).

4. Leave other settings as default, then **Create bucket**.

---

## Step 2: Configure CORS (for browser uploads)

1. Open your bucket → **Permissions** tab → **CORS** section → **Edit**.

2. Paste:

```json
[
    {
        "AllowedHeaders": ["*"],
        "AllowedMethods": ["GET", "PUT", "POST", "DELETE", "HEAD"],
        "AllowedOrigins": [
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:3000",
            "https://your-admin-domain.com"
        ],
        "ExposeHeaders": ["ETag"]
    }
]
```

3. Replace `https://your-admin-domain.com` with your actual admin panel URL(s).

4. Save.

---

## Step 3: Bucket Policy (Public Read for APK and Images)

If you want the APK and uploaded images to be publicly readable (required for Device Owner QR provisioning and displaying images):

1. Bucket → **Permissions** → **Bucket policy** → **Edit**.

2. Example policy (allow public read for the entire bucket; tighten as needed):

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "PublicReadGetObject",
            "Effect": "Allow",
            "Principal": "*",
            "Action": "s3:GetObject",
            "Resource": "arn:aws:s3:::YOUR-BUCKET-NAME/*"
        }
    ]
}
```

Replace `YOUR-BUCKET-NAME` with your bucket name.

3. Save. If you see a "Block Public Access" warning, go to **Block public access** and ensure "Block public access to buckets and objects granted through new public bucket or access point policies" is **unchecked** (or edit the block settings to allow this policy).

---

## Step 4: Create IAM User for Backend

1. Go to **IAM** → **Users** → **Create user**.

2. User name: e.g. `da-emilocker-s3-upload`.

3. **Attach policies directly** → **Create policy** (opens new tab).

4. Choose **JSON** and paste:

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:DeleteObject",
                "s3:ListBucket"
            ],
            "Resource": [
                "arn:aws:s3:::YOUR-BUCKET-NAME",
                "arn:aws:s3:::YOUR-BUCKET-NAME/*"
            ]
        }
    ]
}
```

Replace `YOUR-BUCKET-NAME` with your bucket name.

5. Name the policy e.g. `da-emilocker-s3-upload-policy`, create it.

6. Return to user creation, attach the new policy, then create the user.

7. **Create access key** for the user:
   - User → **Security credentials** → **Create access key**
   - Choose "Application running outside AWS" → Next → Create
   - Save the **Access Key ID** and **Secret Access Key** (you won’t see the secret again).

---

## Step 5: Configure Backend

Add to `application.properties` or set environment variables:

```properties
# AWS S3
aws.s3.region=ap-south-1
aws.s3.bucket-name=da-emilocker-uploads
aws.s3.access-key=YOUR_ACCESS_KEY_ID
aws.s3.secret-key=YOUR_SECRET_ACCESS_KEY
```

**Environment variables** (preferred for production):

```bash
export AWS_S3_REGION=ap-south-1
export AWS_S3_BUCKET=da-emilocker-uploads
export AWS_ACCESS_KEY=AKIA...
export AWS_SECRET_KEY=...
```

---

## Step 6: Object Layout

The backend uses these prefixes:

| Prefix                | Use case                         |
|-----------------------|----------------------------------|
| `device-owner-apk/`   | Configure App APK for provisioning |
| `uploads/customer/`   | Customer images                  |
| `uploads/aadhar_*`    | Aadhar documents                 |
| `uploads/pan/`        | PAN documents                    |
| `uploads/dealer_profile/` | Dealer profile images        |
| `uploads/ticket_attachments/` | Support ticket attachments |
| `uploads/general/`    | General uploads                  |

---

## Verification

1. Start the backend with S3 configured.
2. In Admin Panel → **Settings**:
   - Upload an APK → it should upload to S3 and show the URL.
3. In **Customer Detail**:
   - Add/update a customer with an image → URL should point to S3.

---

## Security Notes

- Never commit access keys to version control.
- Use IAM roles instead of access keys when running on EC2/ECS.
- Restrict the IAM policy to only the required bucket and actions.
- Consider CloudFront in front of S3 for caching and HTTPS.
