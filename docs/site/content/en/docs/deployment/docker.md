---
title: Docker
weight: 20
description: Running h5m in a Docker container.
draft: false
---

{{< alert color="warning" >}}
h5m does not currently publish an official Docker image. The examples on this page show how to build and run your own container image using standard Quarkus patterns.
{{< /alert >}}

## Build the JAR First

```bash
git clone https://github.com/hyperfoil/h5m.git
cd h5m
mvn clean package
```

## Create a Dockerfile

Place this `Dockerfile` in the project root:

```dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/h5m.jar /app/h5m.jar

# Data directory for SQLite database
RUN mkdir -p /data && \
    useradd -r -u 1001 h5m && \
    chown h5m:h5m /data

USER 1001

EXPOSE 8080

ENV QUARKUS_DATASOURCE_DB_KIND=sqlite
ENV QUARKUS_DATASOURCE_JDBC_URL=jdbc:sqlite:/data/h5m.db

ENTRYPOINT ["java", "-jar", "/app/h5m.jar"]
```

## Build the Image

```bash
docker build -t h5m:latest .
```

## Run with SQLite (Persistent Volume)

```bash
docker run -d \
  --name h5m \
  -p 8080:8080 \
  -v h5m-data:/data \
  h5m:latest
```

Data persists in the `h5m-data` Docker volume between restarts.

## Run with PostgreSQL

```bash
docker run -d \
  --name h5m \
  -p 8080:8080 \
  -e QUARKUS_DATASOURCE_DB_KIND=postgresql \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/h5m \
  -e QUARKUS_DATASOURCE_USERNAME=h5m \
  -e QUARKUS_DATASOURCE_PASSWORD=secret \
  h5m:latest
```

## Docker Compose

A minimal Compose file with h5m and PostgreSQL:

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_USER: h5m
      POSTGRES_PASSWORD: secret
      POSTGRES_DB: h5m
    volumes:
      - pg-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "h5m"]
      interval: 5s
      retries: 5

  h5m:
    image: h5m:latest
    ports:
      - "8080:8080"
    environment:
      QUARKUS_DATASOURCE_DB_KIND: postgresql
      QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://db:5432/h5m
      QUARKUS_DATASOURCE_USERNAME: h5m
      QUARKUS_DATASOURCE_PASSWORD: secret
    depends_on:
      db:
        condition: service_healthy

volumes:
  pg-data:
```

Start with:

```bash
docker compose up -d
```

## With OIDC

Pass OIDC environment variables at runtime:

```bash
docker run -d \
  --name h5m \
  -p 8080:8080 \
  -e H5M_SECURITY_ENABLED=true \
  -e QUARKUS_OIDC_TENANT_ENABLED=true \
  -e QUARKUS_OIDC_AUTH_SERVER_URL=https://keycloak.example.com/realms/h5m \
  -e QUARKUS_OIDC_CLIENT_ID=h5m \
  -e QUARKUS_OIDC_CREDENTIALS_SECRET=your-secret \
  -e QUARKUS_DATASOURCE_DB_KIND=postgresql \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/h5m \
  -e QUARKUS_DATASOURCE_USERNAME=h5m \
  -e QUARKUS_DATASOURCE_PASSWORD=secret \
  h5m:latest
```

## Health Check

Add a Docker health check to detect startup failures:

```dockerfile
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
  CMD curl -f http://localhost:8080/api/folder || exit 1
```

## Kubernetes

For Kubernetes deployment, expose the container as a `Deployment` with a `Service`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: h5m
spec:
  replicas: 1
  selector:
    matchLabels:
      app: h5m
  template:
    metadata:
      labels:
        app: h5m
    spec:
      containers:
        - name: h5m
          image: h5m:latest
          ports:
            - containerPort: 8080
          env:
            - name: QUARKUS_DATASOURCE_DB_KIND
              value: postgresql
            - name: QUARKUS_DATASOURCE_JDBC_URL
              value: jdbc:postgresql://postgres-svc:5432/h5m
            - name: QUARKUS_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: h5m-db-secret
                  key: username
            - name: QUARKUS_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: h5m-db-secret
                  key: password
          livenessProbe:
            httpGet:
              path: /api/folder
              port: 8080
            initialDelaySeconds: 15
          readinessProbe:
            httpGet:
              path: /api/folder
              port: 8080
            initialDelaySeconds: 10
```
