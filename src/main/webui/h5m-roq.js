// h5m docs entry point — Carbon design system + Roq page layout.
// Built by Vite and served to both the SPA and the Roq static docs site.
import './h5m-roq.scss';

// ui-shell (cds-header*, cds-side-nav* — see layouts/base.html and partials/side-nav.html) is the above-the-fold chrome, and the only
// statically imported component family: the barrel (es/index.js) registers all 89 and dominates the bundle. Everything the code blocks
// need is loaded lazily, after first paint — see upgradeCodeBlocks() at the bottom of this file.
import '@carbon/web-components/es/components/ui-shell/index.js';

// highlight.js grammars for the languages appearing in fenced blocks under content/, each behind its own import() so a page only pays for
// the languages it actually uses. (`promql` is used by the Prometheus page but highlight.js ships no grammar for it, so those blocks stay
// unhighlighted — as they already did with the "common" build.)
const GRAMMARS = {
  bash: () => import('highlight.js/lib/languages/bash'),
  ini: () => import('highlight.js/lib/languages/ini'),
  java: () => import('highlight.js/lib/languages/java'),
  json: () => import('highlight.js/lib/languages/json'),
  properties: () => import('highlight.js/lib/languages/properties'),
  sql: () => import('highlight.js/lib/languages/sql'),
  yaml: () => import('highlight.js/lib/languages/yaml'),
};

// Rows a multi-line snippet shows before collapsing behind a "Show more" toggle. Carbon's default is 15; docs code blocks are usually
// short, so show more before collapsing.
const MAX_COLLAPSED_ROWS = 20;

// Resolves once the browser has painted: rAF runs *before* the next paint, so a task queued from inside it is the first thing to run
// after it. Anything awaiting this cannot delay first paint.
const afterPaint = () => new Promise((resolve) => requestAnimationFrame(() => setTimeout(resolve, 0)));

// Resolves when the main thread goes idle, falling back to a plain task on Safari, which has no requestIdleCallback.
const whenIdle = () => new Promise((resolve) => (window.requestIdleCallback ?? ((callback) => setTimeout(callback, 0)))(resolve));

// Replace one Markdown-rendered <pre><code>…</code></pre> with a plain-text <cds-code-snippet>, returning it along with what highlightAll() needs to finish the job.
function toSnippet(code) {
  const source = code.textContent.trim();
  // Markdown emits `language-xxx` on <code> for fenced blocks; bare fences leave this undefined. We never auto-detect, as it is unreliable on the DAG/expression samples that use those bare fences.
  const language = [...code.classList].find((name) => name.startsWith('language-'))?.slice('language-'.length);

  const snippet = document.createElement('cds-code-snippet');
  // `multi` brings expand/collapse and wrapping; a single line reads better as the compact `single` variant.
  if (source.includes('\n')) {
    snippet.setAttribute('type', 'multi');
    snippet.setAttribute('wrap-text', 'false');
    snippet.setAttribute('max-collapsed-number-of-rows', String(MAX_COLLAPSED_ROWS));
  } else {
    snippet.setAttribute('type', 'single');
  }

  // copy-text pins the copy button to the raw source up front, so copying works right away and keeps working once highlightAll() swaps the slot for markup
  // (Carbon's copy handler reads a text node, which that markup would not provide).
  snippet.setAttribute('copy-text', source);
  snippet.textContent = source;
  code.parentElement.replaceWith(snippet);

  return { snippet, source, language };
}

// Syntax highlight the snippets whose language we have a grammar for, loading highlight.js core and those grammars only.
async function highlightAll(snippets) {
  const pending = snippets.filter(({ language }) => Object.hasOwn(GRAMMARS, language ?? ''));
  if (pending.length === 0) return;

  const languages = [...new Set(pending.map(({ language }) => language))];
  const [{ default: hljs }, ...loaded] = await Promise.all([
    import('highlight.js/lib/core'),
    ...languages.map(async (language) => [language, (await GRAMMARS[language]()).default]),
  ]);
  loaded.forEach(([language, grammar]) => hljs.registerLanguage(language, grammar));

  pending.forEach(({ snippet, source, language }) => {
    snippet.innerHTML = hljs.highlight(source, { language }).value;
  });
}

// Upgrade the docs' fenced code blocks into Carbon <cds-code-snippet>. Only touches the prose container (.cds--content-block); the SPA has
// no such markup. The server-rendered <pre> blocks are already readable, so everything here is pure enhancement, staged to stay off the
// critical path: paint first, then swap in the snippets (copy button, expand/collapse), then highlight once idle.
async function upgradeCodeBlocks() {
  const blocks = [...document.querySelectorAll('.cds--content-block pre > code')].filter((code) => !code.parentElement.dataset.cdsUpgraded);
  if (blocks.length === 0) return;
  // Mark up front rather than per block, so a re-run (hot reload) cannot pick up blocks that are already mid-upgrade.
  blocks.forEach((code) => (code.parentElement.dataset.cdsUpgraded = 'true'));

  await afterPaint();
  await import('@carbon/web-components/es/components/code-snippet/index.js');
  const snippets = blocks.map(toSnippet);

  await whenIdle();
  await highlightAll(snippets);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', upgradeCodeBlocks);
} else {
  upgradeCodeBlocks();
}
