# Email Configuration Guide

This guide explains how to configure email services for the AREA Backend, including SMTP setup and Resend API fallback configuration.

## Table of Contents

- [Overview](#overview)
- [SMTP Configuration](#smtp-configuration)
- [Resend API Configuration](#resend-api-configuration)
- [Dual Provider Setup](#dual-provider-setup)
- [Testing Email Configuration](#testing-email-configuration)
- [Troubleshooting](#troubleshooting)
- [Security Considerations](#security-considerations)

## Overview

The AREA Backend supports two email delivery methods:

1. **SMTP (Primary)**: Traditional email sending via SMTP servers
2. **Resend API (Fallback)**: Modern email API service

The system automatically falls back to Resend if SMTP fails, ensuring reliable email delivery.

## SMTP Configuration

### Gmail SMTP Setup

**1. Enable 2-Factor Authentication**
- Go to Google Account settings
- Enable 2-Step Verification

**2. Generate App Password**
- Go to Google Account → Security → App passwords
- Generate password for "AREA Backend"
- Use this 16-character password (not your regular password)

**3. Environment Configuration**
```bash
# SMTP Configuration for Gmail
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-16-char-app-password
```

**4. Application Properties**
```properties
# Gmail SMTP Configuration
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${SMTP_USERNAME}
spring.mail.password=${SMTP_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.ssl.trust=smtp.gmail.com
```

### Custom SMTP Server

For other SMTP providers (Outlook, Yahoo, etc.):

```bash
# Outlook/Hotmail
SMTP_HOST=smtp-mail.outlook.com
SMTP_PORT=587
SMTP_USERNAME=your-email@outlook.com
SMTP_PASSWORD=your-password

# Yahoo
SMTP_HOST=smtp.mail.yahoo.com
SMTP_PORT=587
SMTP_USERNAME=your-email@yahoo.com
SMTP_PASSWORD=your-app-password

# Custom SMTP
SMTP_HOST=your-smtp-server.com
SMTP_PORT=587
SMTP_USERNAME=your-username
SMTP_PASSWORD=your-password
```

### Common SMTP Configurations

| Provider | SMTP Host | Port | Security |
|----------|-----------|------|----------|
| Gmail | smtp.gmail.com | 587 | STARTTLS |
| Outlook | smtp-mail.outlook.com | 587 | STARTTLS |
| Yahoo | smtp.mail.yahoo.com | 587 | STARTTLS |
| SendGrid | smtp.sendgrid.net | 587 | STARTTLS |
| Mailgun | smtp.mailgun.org | 587 | STARTTLS |

## Resend API Configuration

### Getting Started with Resend

**1. Sign Up**
- Go to [resend.com](https://resend.com)
- Create an account
- Verify your domain (recommended for production)

**2. Get API Key**
- Go to API Keys section
- Create a new API key
- Copy the key (starts with `re_`)

**3. Domain Verification (Production)**
- Add your domain in the Domains section
- Add the required DNS records
- Wait for verification (can take up to 24 hours)

### Environment Configuration

```bash
# Resend API Configuration
RESEND_API_KEY=re_your-api-key-here
RESEND_FROM_EMAIL=noreply@yourdomain.com
RESEND_ENABLED=true
```

### Application Properties

```properties
# Resend Configuration
app.email.resend.api-key=${RESEND_API_KEY}
app.email.resend.from=${RESEND_FROM_EMAIL:noreply@yourdomain.com}
app.email.resend.enabled=${RESEND_ENABLED:true}
```

## Dual Provider Setup

### Complete Configuration

**.env file:**
```bash
# SMTP Configuration (Primary)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-email@gmail.com
SMTP_PASSWORD=your-16-char-app-password

# Resend Configuration (Fallback)
RESEND_API_KEY=re_your-api-key-here
RESEND_FROM_EMAIL=noreply@yourdomain.com
RESEND_ENABLED=true

# Email Settings
EMAIL_VERIFICATION_TOKEN_EXPIRY_MINUTES=1440
EMAIL_RESET_TOKEN_EXPIRY_MINUTES=15
EMAIL_FROM=noreply@yourdomain.com
EMAIL_VERIFICATION_SUBJECT="Verify Your AREA Account"
EMAIL_RESET_SUBJECT="Reset Your AREA Password"
NEXT_PUBLIC_APP_URL=http://localhost:3000
```

**application.properties:**
```properties
# SMTP Configuration
spring.mail.host=${SMTP_HOST:smtp.gmail.com}
spring.mail.port=${SMTP_PORT:587}
spring.mail.username=${SMTP_USERNAME}
spring.mail.password=${SMTP_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
spring.mail.properties.mail.smtp.ssl.trust=${SMTP_HOST:smtp.gmail.com}

# Email Verification Settings
app.email.verification.token-expiry-minutes=${EMAIL_VERIFICATION_TOKEN_EXPIRY_MINUTES:1440}
app.email.reset.token-expiry-minutes=${EMAIL_RESET_TOKEN_EXPIRY_MINUTES:15}
app.email.from=${EMAIL_FROM:noreply@yourdomain.com}
app.email.verification.subject=${EMAIL_VERIFICATION_SUBJECT:Verify Your AREA Account}
app.email.reset.subject=${EMAIL_RESET_SUBJECT:Reset Your AREA Password}
app.email.frontend-url=${NEXT_PUBLIC_APP_URL:http://localhost:3000}

# Resend Fallback
app.email.resend.api-key=${RESEND_API_KEY}
app.email.resend.from=${RESEND_FROM_EMAIL:noreply@yourdomain.com}
app.email.resend.enabled=${RESEND_ENABLED:true}
```

## Testing Email Configuration

### Health Check Endpoint

Check if email services are working:

```bash
curl http://localhost:8080/actuator/health
```

**Expected Response:**
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

### Manual Email Test

Test email sending with a simple curl command:

```bash
# Test SMTP connection
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{"email": "your-test-email@example.com"}'
```

### Email Service Logs

Check application logs for email sending:

```bash
# View recent logs
tail -f logs/application.log | grep -i email

# Or check with docker
docker-compose -f docker-compose.back.yaml logs -f area-backend | grep -i email
```

## Troubleshooting

### SMTP Issues

#### Authentication Failed
```
org.springframework.mail.MailAuthenticationException: Authentication failed
```

**Solutions:**
- Verify username/password
- For Gmail: Use App Password, not regular password
- Check if 2FA is enabled and App Password is correct
- Ensure account isn't locked or suspended

#### Connection Timeout
```
java.net.SocketTimeoutException: Read timed out
```

**Solutions:**
- Check SMTP host and port
- Verify firewall settings
- Try different SMTP provider
- Check network connectivity

#### TLS/SSL Issues
```
javax.mail.MessagingException: Could not convert socket to TLS
```

**Solutions:**
- Verify `spring.mail.properties.mail.smtp.starttls.enable=true`
- Check if port supports TLS (587 for STARTTLS, 465 for SSL)
- Update Java security settings if needed

### Resend API Issues

#### Invalid API Key
```
HTTP 401 Unauthorized
```

**Solutions:**
- Verify API key starts with `re_`
- Check if API key is active
- Regenerate API key if compromised

#### Domain Not Verified
```
HTTP 403 Forbidden - Domain not verified
```

**Solutions:**
- Verify domain ownership with DNS records
- Wait for DNS propagation (up to 24 hours)
- Use verified domain for `RESEND_FROM_EMAIL`

#### Rate Limiting
```
HTTP 429 Too Many Requests
```

**Solutions:**
- Check Resend dashboard for usage limits
- Implement email queuing for high-volume
- Upgrade Resend plan if needed

### Common Issues

#### Emails Not Being Sent

1. **Check Configuration:**
   ```bash
   # Verify environment variables
   echo $SMTP_USERNAME
   echo $RESEND_API_KEY
   ```

2. **Test Connectivity:**
   ```bash
   # Test SMTP connection
   telnet smtp.gmail.com 587

   # Test Resend API
   curl -H "Authorization: Bearer $RESEND_API_KEY" \
        https://api.resend.com/domains
   ```

3. **Check Logs:**
   ```bash
   # Application logs
   grep "EmailService" logs/application.log

   # Email health
   curl http://localhost:8080/actuator/health | jq '.components.email'
   ```

#### Emails Going to Spam

**Solutions:**
- Use verified domain for sender email
- Avoid spam trigger words in subject/content
- Set up SPF/DKIM/DMARC records
- Monitor email reputation
- Use consistent sending patterns

#### Email Templates Not Working

**Issues:**
- Links not clickable
- Variables not replaced
- HTML formatting broken

**Solutions:**
- Check template syntax
- Verify variable names match code
- Test with plain text first
- Validate HTML structure

## Security Considerations

### Credential Management

**Never commit credentials:**
```bash
# .gitignore should include
.env
application-local.properties
```

**Use environment variables:**
```bash
# Production deployment
export SMTP_PASSWORD="your-secure-password"
export RESEND_API_KEY="re_your-api-key"
```

### Email Security Best Practices

1. **Use HTTPS URLs** in email links
2. **Token Expiry** - Keep verification tokens short-lived
3. **Rate Limiting** - Prevent email abuse
4. **Content Security** - Avoid sensitive data in emails
5. **Domain Verification** - Use verified sending domains

### Monitoring and Alerts

**Set up monitoring for:**
- Email delivery failures
- High bounce rates
- Spam complaints
- API rate limit hits

**Log important events:**
```java
// In EmailService
log.info("Email sent successfully to: {}", email);
log.warn("Email delivery failed: {}", exception.getMessage());
```

## Production Deployment

### Environment-Specific Configuration

**Development:**
```properties
# Relaxed settings for development
app.email.verification.token-expiry-minutes=1440
app.email.resend.enabled=true
```

**Production:**
```properties
# Stricter settings for production
app.email.verification.token-expiry-minutes=1440
app.email.reset.token-expiry-minutes=15
app.email.resend.enabled=true
```

### Email Service Selection

**When to use SMTP:**
- Low to medium email volume
- Cost-sensitive projects
- Existing SMTP infrastructure

**When to use Resend:**
- High email volume
- Advanced analytics needed
- Transactional email focus
- Better deliverability

**Recommended: Use both with SMTP as primary and Resend as fallback**

### Scaling Considerations

**For high-volume applications:**
1. **Email queuing** - Use message queues for bulk emails
2. **Multiple SMTP accounts** - Rotate between accounts
3. **Email service providers** - Consider dedicated ESPs
4. **Monitoring** - Track delivery rates and bounce rates

### Backup and Recovery

**Email service failure scenarios:**
1. **SMTP down** → Automatic fallback to Resend
2. **Resend down** → Queue emails for retry
3. **Both down** → Alert administrators, manual intervention

**Recovery procedures:**
- Monitor health endpoints
- Have backup email services ready
- Document manual email sending procedures

This configuration guide should help you set up reliable email services for the AREA Backend application.