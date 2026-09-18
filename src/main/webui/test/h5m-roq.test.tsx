import { beforeEach, expect, test, vi } from 'vitest';

vi.mock('@carbon/web-components/es/components/ui-shell/index.js', () => ({}));
vi.mock('@carbon/web-components/es/components/code-snippet/index.js', () => ({}));

const FIXTURE = `<div class="cds--content-block">
  <pre><code class="language-bash">echo hi\necho there</code></pre>
  <pre><code class="language-sql">select 1</code></pre>
  <pre><code class="language-promql">rate(x[5m])</code></pre>
  <pre><code>bare fence</code></pre>
</div>`;

// The docs entry point is plain JS outside tsconfig's `include`, with no exports and no declarations — loading it just runs it for its side effects on document.
// One suppression here rather than at each call site.
// @ts-expect-error TS7016: untyped JS module
const runEntry = (): Promise<unknown> => import('../h5m-roq.js');

const settle = () => new Promise((r) => setTimeout(r, 200));

beforeEach(() => {
  document.body.innerHTML = FIXTURE;
  vi.resetModules();
});

test('stages: paint is not blocked, then snippets, then highlight', async () => {
  await runEntry();

  // module evaluation must return before any DOM mutation
  expect(document.querySelectorAll('pre')).toHaveLength(4);

  await settle();

  const snippets = [...document.querySelectorAll('cds-code-snippet')];
  expect(snippets).toHaveLength(4);
  expect(document.querySelectorAll('pre')).toHaveLength(0);

  // named after the fixture, one per fenced block; the length assertion above justifies the tuple
  const [bash, sql, promql, bare] = snippets as [Element, Element, Element, Element];

  // multi vs single variant
  expect(bash.getAttribute('type')).toBe('multi');
  expect(bash.getAttribute('max-collapsed-number-of-rows')).toBe('20');
  expect(sql.getAttribute('type')).toBe('single');

  // copy-text survives the highlight swap
  expect(bash.getAttribute('copy-text')).toBe('echo hi\necho there');
  expect(bash.innerHTML).toContain('hljs-built_in');
  expect(sql.innerHTML).toContain('hljs-keyword');

  // no grammar / no language -> left as plain text
  expect(promql.textContent).toBe('rate(x[5m])');
  expect(promql.innerHTML).not.toContain('hljs-');
  expect(bare.textContent).toBe('bare fence');
});

test('re-running is a no-op', async () => {
  await runEntry();
  await settle();
  const before = document.body.innerHTML;

  vi.resetModules();
  await runEntry();
  await settle();

  expect(document.body.innerHTML).toBe(before);
});

test('a page with no code blocks upgrades nothing', async () => {
  document.body.innerHTML = '<div class="cds--content-block"><p>prose only</p></div>';
  await runEntry();
  await settle();
  expect(document.querySelectorAll('cds-code-snippet')).toHaveLength(0);
});
