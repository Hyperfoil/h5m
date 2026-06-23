---
title: Prometheus Metrics
linkTitle: Prometheus
weight: 50
description: Exposing h5m runtime metrics to Prometheus for monitoring.
draft: false
---

h5m exposes runtime metrics in Prometheus format via the Quarkus Micrometer extension. No additional configuration is required — the metrics endpoint is available by default when running the web server.

## Metrics Endpoint

```
GET /q/metrics
```

Example:

```bash
curl http://localhost:8080/q/metrics
```

The response is in Prometheus text format:

```
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="G1 Eden Space",} 2.4117248E7
...
# HELP http_server_requests_seconds
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="GET",outcome="SUCCESS",status="200",uri="/api/folder"} 42.0
...
```

## What is Exposed

h5m includes `quarkus-micrometer-registry-prometheus`, which automatically instruments:

| Category | Examples |
|----------|---------|
| **JVM** | Heap/non-heap memory, GC pauses, thread count, class loading |
| **HTTP server** | Request count, latency (summary/histogram), status codes, URI |
| **Datasource** | Connection pool size, acquisition time, active connections |
| **Hibernate ORM** | Query count, slow queries (logged at `>100ms`) |
| **System** | CPU usage, file descriptors, uptime |

## Prometheus Configuration

Add h5m to your `prometheus.yml` scrape config:

```yaml
scrape_configs:
  - job_name: h5m
    static_configs:
      - targets:
          - localhost:8080
    metrics_path: /q/metrics
    scrape_interval: 15s
```

## Useful Metrics

### Request Latency by Endpoint

```promql
histogram_quantile(0.99,
  sum(rate(http_server_requests_seconds_bucket{job="h5m"}[5m])) by (le, uri)
)
```

### Upload Rate

```promql
rate(http_server_requests_seconds_count{
  job="h5m",
  uri="/api/folder/{name}/upload",
  method="POST"
}[5m])
```

### Database Connection Pool Utilisation

```promql
datasource_pool_active_connections{job="h5m"} /
datasource_pool_max_connections{job="h5m"}
```

### JVM Heap Usage

```promql
jvm_memory_used_bytes{job="h5m", area="heap"}
```

## Grafana

Import Quarkus community dashboards from [grafana.com/dashboards](https://grafana.com/grafana/dashboards/) to get pre-built panels for JVM, HTTP, and datasource metrics. Search for "Quarkus" to find compatible dashboards.
