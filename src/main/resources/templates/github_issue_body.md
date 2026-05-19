## Change Detected

**Folder:** {folderName}
**Detection node:** {nodeName} (`{nodeType}`)
**Changes:** {changeCount}

{#if changes.size > 0}
### Details

| # | Fingerprint | Details |
|---|-------------|----------|
{#each changes}
| {it_count} | `{it.fingerprint}` | {it.data} |
{/each}
{/if}

---
*Created by [h5m](https://github.com/Hyperfoil/h5m)*
