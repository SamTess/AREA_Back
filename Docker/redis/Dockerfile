FROM redis:latest

# Install OpenSSL for certificate generation
RUN apt-get update && \
    apt-get install -y openssl && \
    rm -rf /var/lib/apt/lists/*

COPY redis.conf /usr/local/etc/redis/redis.conf.template
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
COPY generate-certs.sh /usr/local/bin/generate-certs.sh

RUN chmod +x /usr/local/bin/docker-entrypoint.sh && \
    chmod +x /usr/local/bin/generate-certs.sh

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
