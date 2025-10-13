#!/bin/bash
# Generate self-signed certificates for Redis TLS/SSL
# For production, replace with proper CA-signed certificates

set -e

CERT_DIR="/usr/local/etc/redis/certs"
DAYS_VALID=365

echo "Generating Redis TLS/SSL certificates..."

# Create certificate directory
mkdir -p "$CERT_DIR"
cd "$CERT_DIR"

# Generate CA key and certificate
if [ ! -f ca-key.pem ]; then
    echo "Generating CA certificate..."
    openssl genrsa -out ca-key.pem 4096
    openssl req -new -x509 -days $DAYS_VALID -key ca-key.pem -out ca-cert.pem \
        -subj "/C=US/ST=State/L=City/O=AREA/CN=Redis CA"
fi

# Generate Redis server key and certificate
if [ ! -f redis-key.pem ]; then
    echo "Generating Redis server certificate..."
    openssl genrsa -out redis-key.pem 4096
    openssl req -new -key redis-key.pem -out redis-req.pem \
        -subj "/C=US/ST=State/L=City/O=AREA/CN=redis"
    openssl x509 -req -days $DAYS_VALID -in redis-req.pem -CA ca-cert.pem \
        -CAkey ca-key.pem -CAcreateserial -out redis-cert.pem
    rm -f redis-req.pem
fi

# Set proper permissions
chmod 644 ca-cert.pem redis-cert.pem
chmod 600 ca-key.pem redis-key.pem

echo "Certificates generated successfully in $CERT_DIR"
echo "CA Certificate: ca-cert.pem"
echo "Redis Certificate: redis-cert.pem"
echo "Redis Key: redis-key.pem"
echo ""
echo "For production, replace these self-signed certificates with proper CA-signed certificates."
