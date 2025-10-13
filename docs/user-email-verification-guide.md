# Email Verification and Password Reset Guide

This guide explains how to use the email verification and password reset features in the AREA Backend application.

## Table of Contents

- [Overview](#overview)
- [Email Verification Process](#email-verification-process)
- [Password Reset Process](#password-reset-process)
- [Email Templates](#email-templates)
- [Troubleshooting](#troubleshooting)
- [Security Considerations](#security-considerations)

## Overview

The AREA Backend includes a comprehensive email verification system that ensures:

- **Account Security**: New accounts must verify their email before full access
- **Password Recovery**: Secure password reset functionality
- **User Experience**: Clear feedback and guidance throughout the process
- **Dual Email Providers**: SMTP primary with Resend API fallback

## Email Verification Process

### Step 1: User Registration

When a user registers for an account:

```http
POST /api/auth/register
Content-Type: application/json

{
    "email": "user@example.com",
    "password": "securePassword123"
}
```

**Response (201 Created):**
```json
{
    "message": "Registration successful. Please check your email to verify your account.",
    "user": {
        "id": "123e4567-e89b-12d3-a456-426614174000",
        "email": "user@example.com",
        "isActive": false,
        "isAdmin": false,
        "createdAt": "2024-01-01T10:00:00Z"
    }
}
```

**Important Notes:**
- The user account is created but `isActive` is set to `false`
- A verification email is automatically sent
- The user cannot log in until email is verified

### Step 2: Email Verification

The user receives an email with a verification link:

```
Subject: Verify Your AREA Account

Dear user@example.com,

Welcome to AREA! Please verify your email address to complete your registration.

Click the link below to verify your account:
http://localhost:3000/verify?token=abc123def456...

This link will expire in 24 hours.

If you didn't create an account, please ignore this email.

Best regards,
AREA Team
```

### Step 3: Verify Email

The user clicks the verification link or manually calls the API:

```http
GET /api/auth/verify?token=verification_token_here
```

**Success Response (200 OK):**
```json
{
    "message": "Email verified successfully",
    "user": {
        "id": "123e4567-e89b-12d3-a456-426614174000",
        "email": "user@example.com",
        "isActive": true,
        "isAdmin": false
    }
}
```

**What happens after verification:**
- User account is activated (`isActive: true`)
- User can now log in normally
- Verification token is invalidated

### Step 4: Login

After verification, the user can log in:

```http
POST /api/auth/login
Content-Type: application/json

{
    "email": "user@example.com",
    "password": "securePassword123"
}
```

## Password Reset Process

### Step 1: Request Password Reset

When a user forgets their password:

```http
POST /api/auth/forgot-password
Content-Type: application/json

{
    "email": "user@example.com"
}
```

**Response (200 OK):**
```json
{
    "message": "If an account with this email exists, a password reset link has been sent"
}
```

**Security Notes:**
- Response is identical whether email exists or not (prevents email enumeration)
- Reset email is only sent if account exists and is verified
- Rate limiting may apply to prevent abuse

### Step 2: Password Reset Email

The user receives a password reset email:

```
Subject: Reset Your AREA Password

Dear user@example.com,

You requested a password reset for your AREA account.

Click the link below to reset your password:
http://localhost:3000/reset-password?token=xyz789abc123...

This link will expire in 15 minutes for security reasons.

If you didn't request this reset, please ignore this email.

Best regards,
AREA Team
```

### Step 3: Reset Password

The user clicks the reset link and provides a new password:

```http
POST /api/auth/reset-password
Content-Type: application/json

{
    "token": "reset_token_here",
    "newPassword": "newSecurePassword123"
}
```

**Success Response (200 OK):**
```json
{
    "message": "Password reset successfully"
}
```

**What happens after reset:**
- User's password is updated
- All existing sessions are invalidated (security measure)
- Reset token is invalidated
- User must log in again with new password

## Email Templates

### Verification Email Template

**Subject:** Verify Your AREA Account

```
Dear {{user.email}},

Welcome to AREA! Please verify your email address to complete your registration.

Click the link below to verify your account:
{{frontendUrl}}/verify?token={{verificationToken}}

This link will expire in {{expiryHours}} hours.

If you didn't create an account, please ignore this email.

Best regards,
AREA Team
```

### Password Reset Email Template

**Subject:** Reset Your AREA Password

```
Dear {{user.email}},

You requested a password reset for your AREA account.

Click the link below to reset your password:
{{frontendUrl}}/reset-password?token={{resetToken}}

This link will expire in {{expiryMinutes}} minutes for security reasons.

If you didn't request this reset, please ignore this email.

Best regards,
AREA Team
```

## Troubleshooting

### Email Not Received

**Possible causes:**
1. **Spam/Junk folder**: Check spam folder
2. **Email service issues**: SMTP provider blocking or Resend API issues
3. **Invalid email**: Verify email address is correct
4. **Account not verified**: Only verified accounts can reset passwords

**Solutions:**
- Check application logs for email sending errors
- Verify SMTP/Resend configuration
- Test with different email providers
- Check email health endpoint: `GET /actuator/health`

### Verification Link Expired

**Symptoms:**
```json
{
    "message": "Invalid or expired verification token"
}
```

**Solutions:**
- Request a new verification email (if registration endpoint allows)
- Contact support for manual verification
- Check token expiry configuration (`app.email.verification.token-expiry-minutes`)

### Reset Link Expired

**Symptoms:**
```json
{
    "message": "Invalid or expired reset token"
}
```

**Solutions:**
- Request a new password reset
- Check token expiry configuration (`app.email.reset.token-expiry-minutes`)
- Ensure you're using the latest reset email

### Email Service Issues

**Check email health:**
```http
GET /actuator/health
```

**Response includes email health:**
```json
{
    "status": "UP",
    "components": {
        "email": {
            "status": "UP",
            "details": {
                "smtp": "UP",
                "resend": "UP"
            }
        }
    }
}
```

## Security Considerations

### Token Security

- **Verification tokens**: 24-hour expiry, single-use
- **Reset tokens**: 15-minute expiry, single-use
- **Token storage**: Secure, time-limited
- **Token invalidation**: Automatic cleanup after use

### Rate Limiting

- Password reset requests are rate-limited to prevent abuse
- Multiple reset requests for same email may be throttled
- Failed verification attempts may trigger additional security measures

### Account Security

- Unverified accounts cannot log in
- Password reset invalidates all existing sessions
- Email verification prevents unauthorized account creation
- Secure password requirements enforced

### Email Security

- No sensitive information sent in emails
- Tokens are cryptographically secure
- Email content is generic to prevent information leakage
- SMTP credentials are properly secured

## Frontend Integration

### Verification Flow

```javascript
// After registration
const handleRegistration = async (email, password) => {
    const response = await fetch('/api/auth/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
    });

    if (response.ok) {
        // Show success message
        showMessage('Please check your email to verify your account');

        // Redirect to verification pending page
        navigate('/verify-email', { state: { email } });
    }
};

// Email verification
const handleEmailVerification = async (token) => {
    const response = await fetch(`/api/auth/verify?token=${token}`);

    if (response.ok) {
        showMessage('Email verified successfully!');
        navigate('/login');
    } else {
        showMessage('Verification failed. Link may be expired.');
    }
};
```

### Password Reset Flow

```javascript
// Request password reset
const handleForgotPassword = async (email) => {
    const response = await fetch('/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email })
    });

    // Always show success message (security)
    showMessage('If an account with this email exists, a reset link has been sent');
    navigate('/reset-email-sent');
};

// Reset password
const handlePasswordReset = async (token, newPassword) => {
    const response = await fetch('/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token, newPassword })
    });

    if (response.ok) {
        showMessage('Password reset successfully!');
        navigate('/login');
    } else {
        showMessage('Reset failed. Link may be expired.');
    }
};
```

## Configuration

### Environment Variables

```bash
# Email Configuration
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-app-password

# Resend Configuration (Fallback)
RESEND_API_KEY=your-resend-api-key
RESEND_FROM_EMAIL=noreply@yourdomain.com

# Email Settings
EMAIL_VERIFICATION_TOKEN_EXPIRY_MINUTES=1440
EMAIL_RESET_TOKEN_EXPIRY_MINUTES=15
EMAIL_FROM=noreply@yourdomain.com
EMAIL_VERIFICATION_SUBJECT="Verify Your AREA Account"
EMAIL_RESET_SUBJECT="Reset Your AREA Password"

# Frontend URL
NEXT_PUBLIC_APP_URL=http://localhost:3000
```

### Application Properties

```properties
# Email verification settings
app.email.verification.token-expiry-minutes=${EMAIL_VERIFICATION_TOKEN_EXPIRY_MINUTES:1440}
app.email.reset.token-expiry-minutes=${EMAIL_RESET_TOKEN_EXPIRY_MINUTES:15}
app.email.from=${EMAIL_FROM:noreply@yourdomain.com}
app.email.verification.subject=${EMAIL_VERIFICATION_SUBJECT:Verify Your AREA Account}
app.email.reset.subject=${EMAIL_RESET_SUBJECT:Reset Your AREA Password}
app.email.frontend-url=${NEXT_PUBLIC_APP_URL:http://localhost:3000}

# Resend fallback
app.email.resend.api-key=${RESEND_API_KEY}
app.email.resend.from=${RESEND_FROM_EMAIL:noreply@yourdomain.com}
app.email.resend.enabled=${RESEND_ENABLED:true}
```

This guide should help users and developers understand and implement the email verification and password reset features effectively.