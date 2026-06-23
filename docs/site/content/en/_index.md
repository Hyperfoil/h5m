---
title: h5m
---

{{< blocks/cover title="h5m Documentation" height="min" >}}
<p class="lead">A lightweight Java/Quarkus reimagining of Horreum for storing and querying benchmark results — powered by a DAG computation engine.</p>
<div class="mt-5">
  <a class="btn btn-lg btn-primary me-3" href="docs/">
    Get Started
  </a>
  <a class="btn btn-lg btn-secondary" href="https://github.com/hyperfoil/h5m">
    GitHub
  </a>
</div>
{{< /blocks/cover >}}

{{< blocks/lead color="white" >}}
h5m uses a directed acyclic graph (DAG) of <strong>jq</strong>, <strong>JavaScript</strong>, and <strong>JSONata</strong> computation nodes to process performance data — flexibly, repeatably, and without an external broker or database.
{{< /blocks/lead >}}

{{< blocks/section color="dark" type="row" >}}

{{% blocks/feature icon="fa-solid fa-diagram-project" title="DAG Computation" %}}
Replace complex Extractors, Labels, and Variables with a simple graph of composable nodes.
{{% /blocks/feature %}}

{{% blocks/feature icon="fa-solid fa-box" title="Single-JAR Deployment" %}}
Run h5m with no external dependencies — SQLite, DuckDB, or PostgreSQL as your backend.
{{% /blocks/feature %}}

{{% blocks/feature icon="fa-brands fa-github" title="Open Source" %}}
h5m is part of the Hyperfoil ecosystem. Contribute on [GitHub](https://github.com/hyperfoil/h5m).
{{% /blocks/feature %}}

{{< /blocks/section >}}
