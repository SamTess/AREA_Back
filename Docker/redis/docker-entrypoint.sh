#!/bin/bash
# Redis initialization script with password and TLS support

set -e

CERT_DIR="/usr/local/etc/redis/certs"

# Generate TLS certificates if they don't exist and TLS is enabled
if [ "${REDIS_SSL:-false}" = "true" ]; then
    echo "TLS/SSL is enabled for Redis"

    if [ ! -f "$CERT_DIR/redis-cert.pem" ] || [ ! -f "$CERT_DIR/redis-key.pem" ]; then
        echo "Generating self-signed TLS certificates..."
        /usr/local/bin/generate-certs.sh
    else
        echo "Using existing TLS certificates"
    fi
else
    echo "TLS/SSL is disabled for Redis (REDIS_SSL=${REDIS_SSL:-false})"
fi

# Generate redis.conf with password substitution
if [ -n "$REDIS_PASSWORD" ]; then
    echo "Configuring Redis with authentication..."
    sed "s/\${REDIS_PASSWORD}/$REDIS_PASSWORD/g" /usr/local/etc/redis/redis.conf.template > /usr/local/etc/redis/redis.conf
    chmod 644 /usr/local/etc/redis/redis.conf

    # Configure TLS settings
    if [ "${REDIS_SSL:-false}" = "true" ]; then
        echo "Enabling TLS/SSL in redis.conf..."
        # Set port 0 to disable non-TLS connections
        sed -i 's/^# port 0/port 0/g' /usr/local/etc/redis/redis.conf
    else
        echo "Disabling TLS/SSL configuration in redis.conf..."
        # Comment out all TLS settings
        sed -i 's/^tls-/#tls-/g' /usr/local/etc/redis/redis.conf
        # Enable regular port 6379
        sed -i 's/^# port 0/port 6379/g' /usr/local/etc/redis/redis.conf
    fi
else
    echo "WARNING: REDIS_PASSWORD not set. Redis will run without authentication!"
    cp /usr/local/etc/redis/redis.conf.template /usr/local/etc/redis/redis.conf

    # Configure TLS settings
    if [ "${REDIS_SSL:-false}" = "true" ]; then
        echo "Enabling TLS/SSL in redis.conf..."
        # Set port 0 to disable non-TLS connections
        sed -i 's/^# port 0/port 0/g' /usr/local/etc/redis/redis.conf
    else
        echo "Disabling TLS/SSL configuration in redis.conf..."
        # Comment out all TLS settings
        sed -i 's/^tls-/#tls-/g' /usr/local/etc/redis/redis.conf
        # Enable regular port 6379
        sed -i 's/^# port 0/port 6379/g' /usr/local/etc/redis/redis.conf
    fi
fi

# Start Redis with configuration
exec redis-server /usr/local/etc/redis/redis.conf
