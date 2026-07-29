---
title: Docker
weight: 20
description: Running h5m in a Docker container.
draft: false
---

## Container Image

A container image is published to GitHub Container Registry on every push to `main`:

```bash
docker pull ghcr.io/hyperfoil/h5m:latest
```

Alternatively, build locally using Quarkus JIB (no Docker daemon required):

```bash
git clone https://github.com/hyperfoil/h5m.git
cd h5m
mvn clean package -DskipTests -Dquarkus.container-image.build=true
```

All examples below work with both Docker and Podman.

## Run with PostgreSQL

```bash
docker run -d \
  --name h5m \
  -p 8080:8080 \
  -e QUARKUS_DATASOURCE_DB_KIND=postgresql \
  -e QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:5432/h5m \
  -e QUARKUS_DATASOURCE_USERNAME=h5m \
  -e QUARKUS_DATASOURCE_PASSWORD=secret \
  ghcr.io/hyperfoil/h5m:latest
```

## Docker Compose

A minimal Compose file with h5m and PostgreSQL:

```yaml
services:
  db:
    image: postgres:18
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
    image: ghcr.io/hyperfoil/h5m:latest
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

## Health Checks

h5m includes SmallRye Health endpoints for readiness and liveness probes:

- `GET /q/health/ready` -- returns 200 when the application is ready to serve requests
- `GET /q/health/live` -- returns 200 when the application is alive

For Testcontainers:

```java
new GenericContainer<>("ghcr.io/hyperfoil/h5m:latest")
    .waitingFor(Wait.forHttp("/q/health/ready").forStatusCode(200))
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
  ghcr.io/hyperfoil/h5m:latest
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
          image: ghcr.io/hyperfoil/h5m:latest
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
              path: /q/health/live
              port: 8080
            initialDelaySeconds: 15
          readinessProbe:
            httpGet:
              path: /q/health/ready
              port: 8080
            initialDelaySeconds: 10
```
