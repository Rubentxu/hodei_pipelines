# Dockerfile para Hodei Pipelines Worker
# Imagen base con JDK 21
FROM openjdk:21-jdk-slim

# Información de la imagen
LABEL maintainer="hodei-pipelines@rubentxu.dev"
LABEL description="Hodei Pipelines Worker - Ejecutor distribuido de pipelines CI/CD"
LABEL version="1.0.0"

# Variables de entorno
ENV HODEI_HOME=/opt/hodei
ENV HODEI_WORK_DIR=/work
ENV HODEI_ORCHESTRATOR_HOST=localhost
ENV HODEI_ORCHESTRATOR_PORT=9090
ENV WORKER_ID=""
ENV WORKER_LABELS=""
ENV LOG_LEVEL=INFO

# Crear directorios
RUN mkdir -p $HODEI_HOME $HODEI_WORK_DIR && \
    useradd -r -u 1000 -g root -d $HODEI_HOME -s /bin/bash hodei && \
    chown -R hodei:root $HODEI_HOME $HODEI_WORK_DIR && \
    chmod -R 755 $HODEI_HOME $HODEI_WORK_DIR

# Instalar herramientas básicas para pipelines
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    git \
    openssh-client \
    ca-certificates \
    gnupg \
    lsb-release \
    unzip \
    jq \
    python3 \
    python3-pip \
    nodejs \
    npm \
    && rm -rf /var/lib/apt/lists/*

# Instalar Docker CLI (para pipelines que necesiten Docker)
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && \
    apt-get install -y docker-ce-cli && \
    rm -rf /var/lib/apt/lists/*

# Copiar el JAR del worker
COPY hodei-worker.jar $HODEI_HOME/

# Copiar scripts de inicio
COPY scripts/ $HODEI_HOME/scripts/
RUN chmod +x $HODEI_HOME/scripts/*.sh

# Configurar usuario
USER hodei
WORKDIR $HODEI_WORK_DIR

# Configurar punto de entrada
ENTRYPOINT ["$HODEI_HOME/scripts/entrypoint.sh"]
CMD ["worker"]

# Exponer puerto gRPC (aunque el worker es cliente)
EXPOSE 9091

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD $HODEI_HOME/scripts/healthcheck.sh || exit 1