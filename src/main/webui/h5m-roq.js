// h5m docs entry point — Carbon design system + Roq page layout.
// Built by Vite and served to both the SPA and the Roq static docs site.
import './h5m-roq.scss';

// Only the component families the docs actually use — the barrel (es/index.js)
// registers all 89 and dominates the bundle.
//   ui-shell     → cds-header*, cds-side-nav* (layouts/base.html, partials/side-nav.html)
//   code-snippet → cds-code-snippet (created by upgradeCodeBlocks below)
import '@carbon/web-components/es/components/ui-shell/index.js';
import '@carbon/web-components/es/components/code-snippet/index.js';

// highlight.js core + only the languages appearing in fenced blocks under content/.
// (`promql` is used by the Prometheus page but highlight.js ships no grammar for it,
// so those blocks stay unhighlighted — as they already did with the "common" build.)
import hljs from 'highlight.js/lib/core';
import bash from 'highlight.js/lib/languages/bash';
import ini from 'highlight.js/lib/languages/ini';
import java from 'highlight.js/lib/languages/java';
import json from 'highlight.js/lib/languages/json';
import properties from 'highlight.js/lib/languages/properties';
import sql from 'highlight.js/lib/languages/sql';
import yaml from 'highlight.js/lib/languages/yaml';

for (const [name, language] of Object.entries({ bash, ini, java, json, properties, sql, yaml })) {
  hljs.registerLanguage(name, language);
}

// Rows a multi-line snippet shows before collapsing behind a "Show more" toggle.
// Carbon's default is 15; docs code blocks are usually short, so show more before collapsing.
const MAX_COLLAPSED_ROWS = 20;

// Upgrade Markdown-rendered fenced code blocks (<pre><code>…</code></pre>) into
// Carbon <cds-code-snippet>, syntax-highlighted via highlight.js. Only runs inside
// the docs prose container (.cds--content-block); the SPA has no such markup.
function upgradeCodeBlocks() {
  document.querySelectorAll('.cds--content-block pre > code').forEach((code) => {
    const pre = code.parentElement;
    // Guard against re-running (hot reload / repeated calls).
    if (pre.dataset.cdsUpgraded) return;

    const source = code.textContent.trim();
    const snippet = document.createElement('cds-code-snippet');

    // `multi` (expand/collapse, wrapping) only when there are multiple lines
    // a single line reads better as the compact `single` variant.
    const multiline = source.includes('\n');
    snippet.setAttribute('type', multiline ? 'multi' : 'single');
    if (multiline) {
      snippet.setAttribute('wrap-text', 'false');
      snippet.setAttribute('max-collapsed-number-of-rows', String(MAX_COLLAPSED_ROWS));
    }

    // Syntax highlight. Markdown emits `language-xxx` on <code> for fenced blocks;
    // highlight only when a language is declared (auto-detect is unreliable on the
    // DAG/expression samples that use bare fences).
    const declared = [...code.classList].find((c) => c.startsWith('language-'));
    const language = declared?.slice('language-'.length);
    if (hljs.getLanguage(language)) {
      // Highlighted HTML goes in the slot; copy-text keeps the copy button on raw source
      // (Carbon's copy handler reads a text node, which highlighted markup would not provide).
      snippet.innerHTML = hljs.highlight(source, { language }).value;
      snippet.setAttribute('copy-text', source);
    } else {
      snippet.textContent = source;
    }

    pre.dataset.cdsUpgraded = 'true';
    pre.replaceWith(snippet);
  });
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', upgradeCodeBlocks);
} else {
  upgradeCodeBlocks();
}
