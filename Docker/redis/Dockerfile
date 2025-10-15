FROM redis:latest

# Install OpenSSL and dos2unix for certificate generation and line ending conversion
RUN apt-get update && \
    apt-get install -y openssl dos2unix && \
    rm -rf /var/lib/apt/lists/*

COPY redis.conf /usr/local/etc/redis/redis.conf.template
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY generate-certs.sh /usr/local/bin/generate-certs.sh

# Convert line endings from CRLF to LF (fixes Windows compatibility)
RUN dos2unix /usr/local/bin/docker-entrypoint.sh && \
    dos2unix /usr/local/bin/generate-certs.sh && \
    chmod +x /usr/local/bin/docker-entrypoint.sh && \
    chmod +x /usr/local/bin/generate-certs.sh

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
