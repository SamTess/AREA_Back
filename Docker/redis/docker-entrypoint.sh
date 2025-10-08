#!/bin/bash
# Redis initialization script with password support

set -e

# Generate redis.conf with password substitution
if [ -n "$REDIS_PASSWORD" ]; then
    echo "Configuring Redis with authentication..."
    sed "s/\${REDIS_PASSWORD}/$REDIS_PASSWORD/g" /usr/local/etc/redis/redis.conf.template > /usr/local/etc/redis/redis.conf
    chmod 644 /usr/local/etc/redis/redis.conf
else
    echo "WARNING: REDIS_PASSWORD not set. Redis will run without authentication!"
    cp /usr/local/etc/redis/redis.conf.template /usr/local/etc/redis/redis.conf
fi

# Start Redis with configuration
exec redis-server /usr/local/etc/redis/redis.conf
