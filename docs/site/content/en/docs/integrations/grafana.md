---
title: Grafana
weight: 60
description: Visualise h5m performance data and change detection results in Grafana dashboards.
draft: false
---

h5m exposes performance data through the `/api/folder/{id}/labelValues` endpoint, which can be connected to Grafana using the **JSON Datasource plugin**. This lets you build dashboards that plot metric values over time, grouped by fingerprint or any other node output.

## Prerequisites

- A running h5m instance
- A Grafana instance (self-hosted or Grafana Cloud)
- The [JSON Datasource plugin](https://grafana.com/grafana/plugins/simpod-json-datasource/) installed in Grafana

---

## Configuring h5m as a Datasource

### Step 1 — Install the JSON Datasource Plugin

1. Go to **Administration → Plugins**
2. Search for **JSON Datasource** (by simpod)
3. Click **Install**

![Install JSON Grafana Plugin](/images/integrations/grafana/install-plugin.png)

### Step 2 — Add a New Datasource

1. Go to **Connections → Data sources → Add data source**

   ![Click Add new data source](/images/integrations/grafana/add-datasource.png)

2. Select **JSON** and configure the following fields:

   | Field | Value |
   |-------|-------|
   | **Name** | h5m |
   | **URL** | `http://<h5m_host>:8080` |
   | **Access** | Server (Default) |

   ![Configure h5m JSON Datasource](/images/integrations/grafana/configure-datasource.png)

3. Click **Save & Test**

   ![Datasource connected successfully](/images/integrations/grafana/datasource-success.png)

If h5m has authentication enabled, add an `Authorization` header under **Custom HTTP Headers**:

| Header | Value |
|--------|-------|
| `Authorization` | `Bearer H5M_<your-api-key>` |

---

## The labelValues API

The primary endpoint for Grafana queries is:

```
GET /api/folder/{id}/labelValues
```

### Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `Long` (path) | The folder ID |
| `nodeIds` | `List<Long>` (query) | Node IDs whose values to include in the result |
| `groupById` | `Long` (query, optional) | Node ID to group results by (e.g. a fingerprint node) |
| `sortById` | `Long` (query, optional) | Node ID to sort results by |

### Example Request

```bash
curl "http://localhost:8080/api/folder/1/labelValues?nodeIds=10&nodeIds=11&groupById=5&sortById=3"
```

This returns a JSON array where each element represents one upload's computed values for the requested nodes, grouped by the fingerprint node (ID 5) and sorted by node ID 3.

### Finding Your IDs

```bash
# Get the folder ID
curl http://localhost:8080/api/folder/my-benchmarks

# Get node IDs for the folder
curl http://localhost:8080/api/folder/my-benchmarks/structure
```

---

## Building a Dashboard

### Step 1 — Create a New Dashboard

Create a fresh Grafana dashboard.

![Create new Dashboard](/images/integrations/grafana/create-dashboard.png)

### Step 2 — Identify the Nodes to Plot

From the folder structure response, note the IDs of:

- The **metric nodes** you want to chart (e.g. throughput, CPU)
- The **fingerprint node** to group by (optional)
- A **sort node** (e.g. upload ID or timestamp)

### Step 3 — Add a Panel

1. Add a panel and select **h5m** as the datasource

   ![Select h5m Datasource](/images/integrations/grafana/select-datasource.png)

2. Give the panel a name

   ![Set panel name](/images/integrations/grafana/panel-name.png)

3. Set the query path using your folder and node IDs:

   ```
   /api/folder/1/labelValues?sortById=6&nodeIds=4&nodeIds=7&nodeIds=2
   ```

   ![Define query path](/images/integrations/grafana/path.png)

4. Add the appropriate fields in the **Fields** section

   ![Configure fields in Grafana](/images/integrations/grafana/fields-grafana.png)

5. Save the panel

   ![Save panel](/images/integrations/grafana/save-panel.png)

   ![Dashboard with h5m data](/images/integrations/grafana/dashboard.png)

### Step 4 — Visualise Change Detection Violations

Fixed Threshold (`ft`) and Relative Difference (`rd`) node IDs can be added to `nodeIds` alongside metric nodes. Violation values appear in the same result set, making it straightforward to annotate your metric panels with detected regressions.

```bash
# Include both the metric node and the detection node
curl "http://localhost:8080/api/folder/1/labelValues?nodeIds=10&nodeIds=77&groupById=5"
```

---
## Notes

- h5m's API is unauthenticated by default (`h5m.security.enabled=false`). If security is enabled, configure an API key in the datasource headers.
- Node IDs are stable after creation — Grafana queries remain valid across restarts.
