FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends fpc time \
    && rm -rf /var/lib/apt/lists/* \
    && (getent passwd 1000 >/dev/null || useradd -u 1000 -m runner)
