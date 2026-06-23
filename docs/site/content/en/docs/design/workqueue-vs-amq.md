---
title: WorkQueue vs ActiveMQ
linkTitle: WorkQueue vs AMQ
weight: 30
description: The decision to use an in-process work queue instead of ActiveMQ.
draft: false
---

Horreum uses **ActiveMQ Artemis (AMQ)** as its message broker to coordinate asynchronous processing of uploaded benchmark data. h5m replaces it with an **in-process, persistence-backed WorkQueue**. This page explains the reasoning.

## The Problem AMQ Solves in Horreum

When benchmark data is uploaded to Horreum, several processing steps need to happen asynchronously — extracting values, applying transformers, running change detection. AMQ is used to queue these steps and hand them off to worker consumers.

This is a standard enterprise pattern: decouple the upload endpoint from the processing work so uploads return quickly while computation happens in the background.

## Why AMQ Was Removed

### 1. External dependency

AMQ is an external service that must be installed, configured, and kept running alongside Horreum. A typical Horreum deployment requires:

- PostgreSQL
- ActiveMQ Artemis
- Keycloak
- The Horreum application itself

Each of these is a separate process with its own configuration, resource requirements, health monitoring, and failure modes. For a benchmark data tool, this is a significant operational burden.

h5m's goal is single-JAR deployment. AMQ makes that impossible by definition.

### 2. AMQ lacked required features

The WorkQueue in h5m needed two capabilities that AMQ did not provide out of the box:

**Inter-task dependencies** — a node that depends on another node's output cannot run until the upstream node has written its Value. AMQ queues are flat; ordering and dependency relationships require custom coordination logic on top.

**Deduplication** — if the same node is triggered multiple times for the same upload (e.g. during recalculation), only one execution should run. AMQ has no native deduplication — it requires message ID tracking and idempotency logic in the consumer.

The in-process WorkQueue implements both natively.

### 3. Crash recovery without a broker

A common argument for external brokers: messages survive if the application crashes. h5m addresses this differently — Work items are persisted to the database before processing begins. On restart, the WorkQueue reads any incomplete Work items from the database and resumes. The database becomes the source of truth, not the broker.

## How the WorkQueue Works

```
Upload arrives
      │
      ▼
FolderService.upload()
      │
      ├─ creates root ValueEntity (persisted to DB)
      └─ creates Work items for all nodes (persisted to DB)
                │
                ▼
         WorkQueue (in-process)
                │
                ├─ KahnDagSort: topological sort of Work items
                │   (respects node dependency order)
                │
                ├─ deduplicates: same node + same upload = one Work item
                │
                ├─ executes nodes in order
                │   └─ each node reads upstream Values, writes its own Value
                │
                └─ on completion: Work item marked done in DB
```

**Kahn's algorithm** is a classic topological sort. It repeatedly finds nodes with no unmet dependencies, executes them, and removes them from the pending set — repeating until all nodes are processed. This guarantees nodes always run after their inputs are ready.

## Feature Comparison

| Feature | ActiveMQ (Horreum) | WorkQueue (h5m) |
|---------|-------------------|-----------------|
| **Deployment** | External service | In-process |
| **Inter-task dependencies** | Custom logic required | Native |
| **Deduplication** | Custom logic required | Native |
| **Crash recovery** | Broker persistence | Database persistence |
| **Monitoring** | AMQ console, JMX | Same DB as application |
| **Single-JAR compatible** | No | Yes |
| **Operational overhead** | High | None |

## Trade-offs

The main trade-off of an in-process queue: it cannot distribute work across multiple application instances. AMQ supports multiple consumers on multiple machines reading from a shared queue — horizontal scaling of workers.

h5m's WorkQueue runs within one JVM. For a tool designed for single-JAR local and small-team deployment, this is acceptable. If h5m ever needed to scale to many concurrent uploads across a fleet, an external broker would become relevant again.

For now, the simplicity and the additional features (dependency ordering, deduplication) outweigh the scaling limitation.
