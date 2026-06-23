---
title: Change Detection
linkTitle: Change Detection
weight: 45
description: Detecting performance regressions using Fixed Threshold and Relative Difference algorithms.
---

Change detection is the process of automatically identifying when uploaded benchmark data deviates from expected behaviour. h5m implements change detection as specialised **nodes** in the computation DAG — they sit downstream of your extraction nodes and flag values that fall outside acceptable bounds.

Two algorithms are currently available:

- **[Fixed Threshold](fixed-threshold/)** — flags values that fall outside a static min/max boundary
- **[Relative Difference](relative-difference/)** — flags values that deviate from a historical baseline by more than a configured percentage

Detection nodes produce `Value` records just like any other node. Detected violations are stored in the DAG and queryable via the standard Value API, with no separate "Change" entity required.
